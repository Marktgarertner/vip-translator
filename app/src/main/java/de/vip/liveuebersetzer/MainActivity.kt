package de.vip.liveuebersetzer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.mlkit.genai.speechrecognition.SpeechRecognizer
import de.vip.liveuebersetzer.ui.theme.VipLiveUebersetzerTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private enum class TranslationMode { GETIPPT, LIVE }

/** Status eines Sprachpakets im "Sprachpakete"-Menü. */
private enum class ModelStatus { PRUEFEN, FEHLT, LAEDT, GELADEN, FEHLER }

/**
 * Ein abgeschlossener Gesprächsbeitrag. Der Verlauf lebt bewusst nur im
 * Arbeitsspeicher (Datenschutz: nichts wird persistiert) und bleibt beim
 * Sprach- bzw. Richtungswechsel vollständig erhalten - jeder Eintrag trägt
 * sein eigenes Sprachpaar.
 */
private data class ConversationEntry(
    val id: Long,
    val sourceLanguage: Language,
    val targetLanguage: Language,
    val originalText: String,
    val translatedText: String,
) {
    /** Text dieses Beitrags in der Sprache der jeweiligen Bildschirmhälfte. */
    fun textFor(pane: Language): String = when (pane.code) {
        sourceLanguage.code -> originalText
        else -> translatedText
    }

    /** Das jeweils andere Gegenstück zu [textFor]. */
    fun counterpartFor(pane: Language): String = when (pane.code) {
        sourceLanguage.code -> translatedText
        else -> originalText
    }
}

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

    var staffLanguage by remember { mutableStateOf(LanguageCatalog.defaultSource) }
    var customerLanguage by remember { mutableStateOf(LanguageCatalog.defaultTarget) }
    var customerSpeaks by remember { mutableStateOf(false) }
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
    var showModelManager by remember { mutableStateOf(false) }

    // Auto-Scroll zum neuesten Beitrag (Index 0, liegt bei reverseLayout "unten").
    val staffListState = rememberLazyListState()
    LaunchedEffect(conversation.size) {
        if (conversation.isNotEmpty()) staffListState.animateScrollToItem(0)
    }

    // Wer gerade spricht, bestimmt Quell- und Zielsprache der nächsten Beiträge.
    val speakingLanguage = if (customerSpeaks) customerLanguage else staffLanguage
    val answerLanguage = if (customerSpeaks) staffLanguage else customerLanguage
    val liveSupported = SpeechEngine.isLiveSupported(speakingLanguage.code)

    fun addEntry(from: Language, to: Language, original: String, translated: String) {
        // Neuester Eintrag an Index 0; die Listen rendern mit reverseLayout,
        // sodass er auf beiden Bildschirmhälften wie in einem Chat "unten" steht.
        conversation.add(0, ConversationEntry(nextEntryId++, from, to, original, translated))
        // Nie sprechen, solange das Mikrofon offen ist - sonst erkennt die
        // Erkennung die eigene Ausgabe als Eingabe (Rückkopplungsschleife).
        if (speechOutputEnabled && !isListening) speechOutput.speak(translated, to)
    }

    fun speakOrExplain(text: String, language: Language) {
        if (!speechOutput.speak(text, language)) {
            errorMessage = "Sprachausgabe für ${language.displayName} ist auf diesem Gerät nicht verfügbar."
        }
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
        // Laufende Sprachausgabe abbrechen, bevor das Mikrofon aufgeht.
        speechOutput.stop()
        liveTranscript = ""
        // Sprachpaar zum Startzeitpunkt festhalten: Ein Richtungswechsel während
        // der Aufnahme darf bereits laufende Beiträge nicht mehr umdrehen.
        val from = speakingLanguage
        val to = answerLanguage
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
                        // Tap-to-Talk: Nach dem ersten fertigen Satz stoppt die
                        // Aufnahme automatisch, damit die anschließende
                        // Sprachausgabe nicht wieder als Eingabe erkannt wird
                        // (Rückkopplungsschleife). Für den nächsten Satz das
                        // Mikrofon einfach erneut antippen.
                        stopLive()
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

    // Jeder Sprach- oder Richtungswechsel stoppt eine laufende Aufnahme, denn
    // der Recognizer ist fest auf seine Startsprache gebunden. Ist die neue
    // Sprechsprache nicht live-fähig (uk/ar), zusätzlich sauber auf Getippt
    // zurückfallen. Der Konversationsverlauf bleibt dabei erhalten.
    LaunchedEffect(staffLanguage, customerLanguage, customerSpeaks) {
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

    if (showModelManager) {
        ModelManagerScreen(onClose = { showModelManager = false })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // ===== Kundenseite: um 180° gedreht, damit das Gegenüber am Schalter =====
        // ===== alles in seiner Leserichtung sieht.                          =====
        CustomerPane(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .rotate(180f),
            language = customerLanguage,
            entries = conversation,
            pendingTranscript = liveTranscript.takeIf { isListening && customerSpeaks && it.isNotBlank() },
            onSpeakClick = { entry -> speakOrExplain(entry.textFor(customerLanguage), customerLanguage) },
        )

        // Trennlinie in ViP-Grün zwischen den beiden Hälften.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(MaterialTheme.colorScheme.primary),
        )

        // ===== Mitarbeiterseite: alle Bedienelemente. =====
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.3f)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.vip_logo),
                    contentDescription = "ViP-Logo",
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = "ViP Live-Übersetzer",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(
                    onClick = {
                        stopLive()
                        showModelManager = true
                    },
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = "Sprachpakete verwalten")
                }
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
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LanguageDropdown(
                    modifier = Modifier.weight(1f),
                    label = "Meine Sprache",
                    selected = staffLanguage,
                    onSelected = { staffLanguage = it },
                )
                LanguageDropdown(
                    modifier = Modifier.weight(1f),
                    label = "Kundensprache",
                    selected = customerLanguage,
                    onSelected = { customerLanguage = it },
                )
            }

            // Große, selbsterklärende Richtungswahl statt eines Tausch-Icons.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !customerSpeaks,
                    onClick = { customerSpeaks = false },
                    label = { Text("Ich spreche") },
                )
                FilterChip(
                    selected = customerSpeaks,
                    onClick = { customerSpeaks = true },
                    label = { Text("Kunde spricht (${customerLanguage.displayName})") },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == TranslationMode.GETIPPT,
                    onClick = {
                        stopLive()
                        mode = TranslationMode.GETIPPT
                    },
                    label = { Text("Getippt") },
                )
                FilterChip(
                    selected = mode == TranslationMode.LIVE,
                    enabled = liveSupported,
                    onClick = { mode = TranslationMode.LIVE },
                    label = { Text(if (liveSupported) "Live" else "Live (für ${speakingLanguage.displayName} nicht verfügbar)") },
                )
            }

            if (conversation.isEmpty()) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    text = "Noch keine Beiträge. Richtung wählen, dann Mikrofon antippen " +
                        "oder Text eintippen - jede Übersetzung erscheint hier und beim " +
                        "Kunden gedreht in Leserichtung, inklusive Vorlesen.",
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    state = staffListState,
                    reverseLayout = true,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(conversation, key = { it.id }) { entry ->
                        StaffEntryCard(
                            entry = entry,
                            staffLanguage = staffLanguage,
                            onSpeakClick = { speakOrExplain(entry.translatedText, entry.targetLanguage) },
                        )
                    }
                }
            }

            errorMessage?.let { message ->
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }

            when (mode) {
                TranslationMode.GETIPPT -> TypedModePanel(
                    inputText = inputText,
                    inputLabel = "Text eingeben (${speakingLanguage.displayName})",
                    onInputChange = { inputText = it },
                    isTranslating = isTranslating,
                    onTranslateClick = {
                        val textToTranslate = inputText
                        val from = speakingLanguage
                        val to = answerLanguage
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
                    speakingLanguageName = speakingLanguage.displayName,
                    onMicClick = ::onMicButtonClick,
                )
            }
        }
    }
}

@Composable
private fun CustomerPane(
    modifier: Modifier = Modifier,
    language: Language,
    entries: List<ConversationEntry>,
    pendingTranscript: String?,
    onSpeakClick: (ConversationEntry) -> Unit,
) {
    // Auto-Scroll zum neuesten Beitrag auch auf der Kundenseite.
    val listState = rememberLazyListState()
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.vip_logo),
                contentDescription = "ViP-Logo",
                modifier = Modifier.size(40.dp),
            )
            Column {
                Text(
                    text = language.greeting,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "Verkehrsbetrieb Potsdam",
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }

        pendingTranscript?.let { transcript ->
            Text(text = transcript, color = MaterialTheme.colorScheme.tertiary)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            state = listState,
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(entries, key = { it.id }) { entry ->
                CustomerEntryCard(
                    entry = entry,
                    language = language,
                    onSpeakClick = { onSpeakClick(entry) },
                )
            }
        }
    }
}

@Composable
private fun CustomerEntryCard(
    entry: ConversationEntry,
    language: Language,
    onSpeakClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = entry.textFor(language),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = entry.counterpartFor(language),
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            IconButton(onClick = onSpeakClick) {
                Icon(Icons.Filled.VolumeUp, contentDescription = "Vorlesen")
            }
        }
    }
}

@Composable
private fun StaffEntryCard(
    entry: ConversationEntry,
    staffLanguage: Language,
    onSpeakClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "${entry.sourceLanguage.displayName} → ${entry.targetLanguage.displayName}",
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    text = entry.textFor(staffLanguage),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = entry.counterpartFor(staffLanguage),
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            IconButton(onClick = onSpeakClick) {
                Icon(Icons.Filled.VolumeUp, contentDescription = "Übersetzung erneut vorlesen")
            }
        }
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

@Composable
private fun TypedModePanel(
    inputText: String,
    inputLabel: String,
    onInputChange: (String) -> Unit,
    isTranslating: Boolean,
    onTranslateClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = inputText,
            onValueChange = onInputChange,
            label = { Text(inputLabel) },
            minLines = 2,
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onTranslateClick,
            enabled = !isTranslating && inputText.isNotBlank(),
        ) {
            if (isTranslating) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
            } else {
                Text("Übersetzen und vorlesen")
            }
        }
    }
}

@Composable
private fun LiveModePanel(
    isListening: Boolean,
    isPreparingSpeechModel: Boolean,
    liveTranscript: String,
    speakingLanguageName: String,
    onMicClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onMicClick) {
            Icon(
                imageVector = if (isListening) Icons.Filled.MicOff else Icons.Filled.Mic,
                contentDescription = if (isListening) "Aufnahme stoppen" else "Aufnahme starten",
                modifier = Modifier.size(56.dp),
                tint = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = if (isListening) {
                "Sprechen Sie jetzt - stoppt nach dem Satz automatisch"
            } else {
                "Zum Sprechen antippen ($speakingLanguageName)"
            },
        )
        if (isPreparingSpeechModel) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                Text(" Spracherkennungsmodell wird vorbereitet …")
            }
        }
        Text(text = liveTranscript.ifBlank { "…" })
    }
}

/**
 * Vollbild-Menü "Sprachpakete": Übersetzungsmodelle (und Live-Erkennung, wo
 * verfügbar) pro Sprache vorab herunterladen, damit am Schalter keine
 * Wartezeit durch spontane Modell-Downloads entsteht.
 */
@Composable
private fun ModelManagerScreen(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val statuses = remember { mutableStateMapOf<String, ModelStatus>() }

    LaunchedEffect(Unit) {
        LanguageCatalog.all.forEach { language ->
            statuses[language.code] = ModelStatus.PRUEFEN
            statuses[language.code] = runCatching { TranslationEngine.isModelDownloaded(language) }
                .fold(
                    { downloaded -> if (downloaded) ModelStatus.GELADEN else ModelStatus.FEHLT },
                    { ModelStatus.FEHLT },
                )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
            }
            Text(
                text = "Sprachpakete",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Text(
            text = "Einmalig mit Internet (am besten WLAN) vorbereiten - danach " +
                "übersetzt und spricht die App komplett offline, ohne Wartezeit " +
                "beim Kunden. Empfehlung: alle häufig gebrauchten Sprachen vorab laden.",
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(LanguageCatalog.all, key = { it.code }) { language ->
                val status = statuses[language.code] ?: ModelStatus.PRUEFEN
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(text = language.displayName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = when (status) {
                                    ModelStatus.PRUEFEN -> "Prüfe …"
                                    ModelStatus.FEHLT -> "Noch nicht geladen"
                                    ModelStatus.LAEDT -> "Wird heruntergeladen …"
                                    ModelStatus.GELADEN ->
                                        if (language.liveSpeechSupported) {
                                            "Bereit (inkl. Live-Erkennung)"
                                        } else {
                                            "Bereit (nur getippter Modus)"
                                        }
                                    ModelStatus.FEHLER -> "Download fehlgeschlagen - Internetverbindung prüfen"
                                },
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        Button(
                            onClick = {
                                statuses[language.code] = ModelStatus.LAEDT
                                scope.launch {
                                    runCatching {
                                        TranslationEngine.downloadModel(language)
                                        SpeechEngine.prepareModel(language.code)
                                    }.onSuccess { statuses[language.code] = ModelStatus.GELADEN }
                                        .onFailure { statuses[language.code] = ModelStatus.FEHLER }
                                }
                            },
                            enabled = status == ModelStatus.FEHLT || status == ModelStatus.FEHLER,
                        ) {
                            Text(
                                when (status) {
                                    ModelStatus.GELADEN -> "Geladen ✓"
                                    ModelStatus.LAEDT -> "Lädt …"
                                    else -> "Laden"
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
