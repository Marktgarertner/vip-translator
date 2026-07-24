package de.vip.liveuebersetzer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.mlkit.genai.speechrecognition.SpeechRecognizer
import de.vip.liveuebersetzer.ui.theme.VipLiveUebersetzerTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private enum class TranslationMode { GETIPPT, LIVE }

/**
 * Ein abgeschlossener Gesprächsbeitrag. Der Verlauf lebt bewusst nur im
 * Arbeitsspeicher (Datenschutz: nichts wird persistiert) und bleibt beim
 * Sprachwechsel bzw. Richtungstausch vollständig erhalten - jeder Eintrag
 * trägt sein eigenes Sprachpaar.
 */
private data class ConversationEntry(
    val id: Long,
    val sourceLanguage: Language,
    val targetLanguage: Language,
    val originalText: String,
    val translatedText: String,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VipLiveUebersetzerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LiveUebersetzerScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveUebersetzerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceLanguage by remember { mutableStateOf(LanguageCatalog.defaultSource) }
    var targetLanguage by remember { mutableStateOf(LanguageCatalog.defaultTarget) }
    var mode by remember { mutableStateOf(TranslationMode.GETIPPT) }

    var inputText by remember { mutableStateOf("") }
    var isTranslating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val conversation = remember { mutableStateListOf<ConversationEntry>() }
    var nextEntryId by remember { mutableStateOf(0L) }

    var liveTranscript by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var isPreparingSpeechModel by remember { mutableStateOf(false) }
    var recognizerJob by remember { mutableStateOf<Job?>(null) }
    var activeRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    val speechOutput = remember { SpeechOutput(context) }
    var speechOutputEnabled by remember { mutableStateOf(true) }

    val liveSupported = SpeechEngine.isLiveSupported(sourceLanguage.code)

    fun addEntry(from: Language, to: Language, original: String, translated: String) {
        // Neuester Eintrag an Index 0; die Liste rendert mit reverseLayout,
        // sodass er wie in einem Chat unten erscheint.
        conversation.add(0, ConversationEntry(nextEntryId++, from, to, original, translated))
        if (speechOutputEnabled) speechOutput.speak(translated, to)
    }

    fun stopLive() {
        recognizerJob?.cancel()
        recognizerJob = null
        activeRecognizer?.let { recognizer ->
            SpeechEngine.stopListening(recognizer)
            SpeechEngine.release(recognizer)
        }
        activeRecognizer = null
        isListening = false
        isPreparingSpeechModel = false
    }

    fun startLive() {
        if (!liveSupported || isListening) return
        errorMessage = null
        liveTranscript = ""
        // Sprachpaar zum Startzeitpunkt festhalten: Ein Richtungstausch während
        // der Aufnahme darf bereits laufende Beiträge nicht mehr umdrehen.
        val from = sourceLanguage
        val to = targetLanguage
        val recognizer = SpeechEngine.createRecognizer(from.code)
        activeRecognizer = recognizer
        isListening = true
        recognizerJob = scope.launch {
            try {
                isPreparingSpeechModel = true
                SpeechEngine.ensureModelDownloaded(recognizer)
                isPreparingSpeechModel = false
                val listenJob = SpeechEngine.listen(
                    recognizer = recognizer,
                    scope = this,
                    onPartial = { text -> liveTranscript = text },
                    onFinal = { text ->
                        liveTranscript = text
                        scope.launch {
                            runCatching {
                                TranslationEngine.translate(from, to, text)
                            }.onSuccess { addEntry(from, to, text, it) }
                                .onFailure { errorMessage = it.message ?: "Übersetzung fehlgeschlagen." }
                        }
                    },
                    onError = { throwable ->
                        errorMessage = throwable.message ?: "Fehler bei der Live-Spracherkennung."
                    },
                )
                listenJob.join()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.message ?: "Fehler bei der Live-Spracherkennung."
            } finally {
                isPreparingSpeechModel = false
                isListening = false
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startLive() else errorMessage = "Ohne Mikrofonzugriff ist der Live-Modus nicht möglich."
    }

    fun onMicButtonClick() {
        if (isListening) {
            stopLive()
            return
        }
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) startLive() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Quellsprachwechsel (auch durch Tauschen): laufende Aufnahme stoppen, denn
    // der Recognizer ist fest auf die Startsprache gebunden. Ist die neue
    // Sprache nicht live-fähig (uk/ar), zusätzlich sauber auf Getippt
    // zurückfallen. Der Konversationsverlauf bleibt dabei erhalten.
    LaunchedEffect(sourceLanguage) {
        if (isListening) stopLive()
        if (!liveSupported && mode == TranslationMode.LIVE) {
            mode = TranslationMode.GETIPPT
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopLive()
            speechOutput.shutdown()
            TranslationEngine.closeAll()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ViP Live-Übersetzer") },
                actions = {
                    IconButton(onClick = { speechOutputEnabled = !speechOutputEnabled }) {
                        Icon(
                            imageVector = if (speechOutputEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                            contentDescription = if (speechOutputEnabled) {
                                "Sprachausgabe ausschalten"
                            } else {
                                "Sprachausgabe einschalten"
                            },
                        )
                    }
                    IconButton(
                        onClick = {
                            conversation.clear()
                            liveTranscript = ""
                            errorMessage = null
                        },
                        enabled = conversation.isNotEmpty(),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Konversation löschen")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LanguageSelectorRow(
                source = sourceLanguage,
                target = targetLanguage,
                onSourceChange = { sourceLanguage = it },
                onTargetChange = { targetLanguage = it },
                onSwap = {
                    val tmp = sourceLanguage
                    sourceLanguage = targetLanguage
                    targetLanguage = tmp
                },
            )

            ModeSwitcher(
                mode = mode,
                liveSupported = liveSupported,
                onModeChange = { newMode ->
                    if (newMode == TranslationMode.GETIPPT) stopLive()
                    mode = newMode
                },
            )

            ConversationList(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                entries = conversation,
                onSpeakClick = { entry ->
                    if (!speechOutput.speak(entry.translatedText, entry.targetLanguage)) {
                        errorMessage =
                            "Sprachausgabe für ${entry.targetLanguage.displayName} ist auf diesem Gerät nicht verfügbar."
                    }
                },
            )

            errorMessage?.let { message ->
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }

            when (mode) {
                TranslationMode.GETIPPT -> TypedModePanel(
                    inputText = inputText,
                    onInputChange = { inputText = it },
                    isTranslating = isTranslating,
                    onTranslateClick = {
                        val textToTranslate = inputText
                        val from = sourceLanguage
                        val to = targetLanguage
                        scope.launch {
                            isTranslating = true
                            errorMessage = null
                            runCatching {
                                TranslationEngine.translate(from, to, textToTranslate)
                            }.onSuccess {
                                addEntry(from, to, textToTranslate, it)
                                inputText = ""
                            }.onFailure { errorMessage = it.message ?: "Übersetzung fehlgeschlagen." }
                            isTranslating = false
                        }
                    },
                )

                TranslationMode.LIVE -> LiveModePanel(
                    isListening = isListening,
                    isPreparingSpeechModel = isPreparingSpeechModel,
                    liveTranscript = liveTranscript,
                    onMicClick = ::onMicButtonClick,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSelectorRow(
    source: Language,
    target: Language,
    onSourceChange: (Language) -> Unit,
    onTargetChange: (Language) -> Unit,
    onSwap: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LanguageDropdown(
            modifier = Modifier.weight(1f),
            label = "Von",
            selected = source,
            onSelected = onSourceChange,
        )
        IconButton(onClick = onSwap) {
            Icon(Icons.Filled.SwapHoriz, contentDescription = "Sprachen tauschen")
        }
        LanguageDropdown(
            modifier = Modifier.weight(1f),
            label = "Nach",
            selected = target,
            onSelected = onTargetChange,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(
    modifier: Modifier = Modifier,
    label: String,
    selected: Language,
    onSelected: (Language) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        modifier = modifier,
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            readOnly = true,
            value = selected.displayName,
            onValueChange = {},
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            LanguageCatalog.all.forEach { language ->
                DropdownMenuItem(
                    text = { Text(language.displayName) },
                    onClick = {
                        onSelected(language)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSwitcher(
    mode: TranslationMode,
    liveSupported: Boolean,
    onModeChange: (TranslationMode) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = mode == TranslationMode.GETIPPT,
            onClick = { onModeChange(TranslationMode.GETIPPT) },
            label = { Text("Getippt") },
        )
        FilterChip(
            selected = mode == TranslationMode.LIVE,
            enabled = liveSupported,
            onClick = { onModeChange(TranslationMode.LIVE) },
            label = { Text(if (liveSupported) "Live" else "Live (nicht verfügbar)") },
        )
    }
}

@Composable
private fun ConversationList(
    modifier: Modifier = Modifier,
    entries: List<ConversationEntry>,
    onSpeakClick: (ConversationEntry) -> Unit,
) {
    if (entries.isEmpty()) {
        Text(
            modifier = modifier,
            text = "Die Konversation erscheint hier. Der Verlauf bleibt beim Sprachwechsel erhalten.",
        )
        return
    }
    LazyColumn(
        modifier = modifier,
        // Neuester Eintrag (Index 0) unten, wie in einem Chat - ohne manuelles
        // Scroll-Management bleibt der aktuellste Beitrag immer sichtbar.
        reverseLayout = true,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(entries, key = { it.id }) { entry ->
            ConversationEntryCard(entry = entry, onSpeakClick = { onSpeakClick(entry) })
        }
    }
}

@Composable
private fun ConversationEntryCard(entry: ConversationEntry, onSpeakClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "${entry.sourceLanguage.displayName} → ${entry.targetLanguage.displayName}",
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Text(text = entry.originalText)
                Text(text = entry.translatedText, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onSpeakClick) {
                Icon(Icons.Filled.VolumeUp, contentDescription = "Übersetzung vorlesen")
            }
        }
    }
}

@Composable
private fun TypedModePanel(
    inputText: String,
    onInputChange: (String) -> Unit,
    isTranslating: Boolean,
    onTranslateClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = inputText,
            onValueChange = onInputChange,
            label = { Text("Text eingeben") },
            minLines = 3,
        )
        Button(
            onClick = onTranslateClick,
            enabled = !isTranslating && inputText.isNotBlank(),
        ) {
            if (isTranslating) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
            } else {
                Text("Übersetzen")
            }
        }
    }
}

@Composable
private fun LiveModePanel(
    isListening: Boolean,
    isPreparingSpeechModel: Boolean,
    liveTranscript: String,
    onMicClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(onClick = onMicClick) {
            Icon(
                imageVector = if (isListening) Icons.Filled.MicOff else Icons.Filled.Mic,
                contentDescription = if (isListening) "Aufnahme stoppen" else "Aufnahme starten",
                modifier = Modifier.size(48.dp),
                tint = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
        if (isPreparingSpeechModel) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                Text(" Spracherkennungsmodell wird vorbereitet …")
            }
        }
        Text(text = liveTranscript.ifBlank { "…" })
    }
}
