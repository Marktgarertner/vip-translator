package de.vip.liveuebersetzer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private enum class TranslationMode { GETIPPT, LIVE }

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
    var translatedText by remember { mutableStateOf("") }
    var isTranslating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var liveTranscript by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var isPreparingSpeechModel by remember { mutableStateOf(false) }
    var recognizerJob by remember { mutableStateOf<Job?>(null) }
    var activeRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    val liveSupported = SpeechEngine.isLiveSupported(sourceLanguage.code)

    val translator = remember(sourceLanguage, targetLanguage) {
        TranslationEngine.createTranslator(sourceLanguage, targetLanguage)
    }
    DisposableEffect(translator) {
        onDispose { TranslationEngine.close(translator) }
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
        val recognizer = SpeechEngine.createRecognizer(sourceLanguage.code)
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
                                TranslationEngine.ensureModelDownloaded(translator)
                                TranslationEngine.translate(translator, text)
                            }.onSuccess { translatedText = it }
                                .onFailure { errorMessage = it.message ?: "Übersetzung fehlgeschlagen." }
                        }
                    },
                    onError = { throwable ->
                        errorMessage = throwable.message ?: "Fehler bei der Live-Spracherkennung."
                    },
                )
                listenJob.join()
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

    // Wechselt der Nutzer die Quellsprache auf Ukrainisch/Arabisch während des
    // Live-Modus, ist Live nicht mehr verfügbar - sauber auf Getippt zurückfallen.
    LaunchedEffect(sourceLanguage) {
        if (!liveSupported && mode == TranslationMode.LIVE) {
            stopLive()
            mode = TranslationMode.GETIPPT
        }
    }

    DisposableEffect(Unit) {
        onDispose { stopLive() }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("ViP Live-Übersetzer") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
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

            when (mode) {
                TranslationMode.GETIPPT -> TypedModePanel(
                    inputText = inputText,
                    onInputChange = { inputText = it },
                    isTranslating = isTranslating,
                    onTranslateClick = {
                        val textToTranslate = inputText
                        scope.launch {
                            isTranslating = true
                            errorMessage = null
                            runCatching {
                                TranslationEngine.ensureModelDownloaded(translator)
                                TranslationEngine.translate(translator, textToTranslate)
                            }.onSuccess { translatedText = it }
                                .onFailure { errorMessage = it.message ?: "Übersetzung fehlgeschlagen." }
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

            TranslationOutputCard(text = translatedText)

            errorMessage?.let { message ->
                Text(text = message, color = MaterialTheme.colorScheme.error)
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
        ExposedDropdownMenu(
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

@Composable
private fun TranslationOutputCard(text: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.padding(16.dp)) {
            Text(text = text.ifBlank { "Übersetzung erscheint hier." })
        }
    }
}
