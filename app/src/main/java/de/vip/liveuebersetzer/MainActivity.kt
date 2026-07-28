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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import de.vip.liveuebersetzer.ui.theme.VipDisabled
import de.vip.liveuebersetzer.ui.theme.VipLiveUebersetzerTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Status eines Sprachpakets im "Sprachpakete"-Menü. */
private enum class ModelStatus { PRUEFEN, FEHLT, LAEDT, GELADEN, FEHLER }

/**
 * Ein abgeschlossener Gesprächsbeitrag. Der Verlauf lebt bewusst nur im
 * Arbeitsspeicher (Datenschutz: nichts wird persistiert) und bleibt beim
 * Sprachwechsel vollständig erhalten - jeder Eintrag trägt sein eigenes
 * Sprachpaar.
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

@Composable
private fun LiveUebersetzerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var staffLanguage by remember { mutableStateOf(LanguageCatalog.defaultSource) }
    var customerLanguage by remember { mutableStateOf(LanguageCatalog.defaultTarget) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    val conversation = remember { mutableStateListOf<ConversationEntry>() }
    var nextEntryId by remember { mutableStateOf(0L) }

    var liveTranscript by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    // Welche Seite gerade aufnimmt: Wer seine Sprechtaste drückt, bestimmt die
    // Übersetzungsrichtung - es gibt bewusst keinen Richtungs-Umschalter und
    // keine Texteingabe, die App konzentriert sich aufs Sprechen.
    var recordingFromCustomer by remember { mutableStateOf(false) }
    var isPreparingSpeechModel by remember { mutableStateOf(false) }
    var recognizerJob by remember { mutableStateOf<Job?>(null) }
    var activeRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var activeVoskSession by remember { mutableStateOf<VoskSpeechSession?>(null) }

    val speechOutput = remember { SpeechOutput(context) }
    var speechOutputEnabled by remember { mutableStateOf(true) }
    var showModelManager by remember { mutableStateOf(false) }
    // Wenn für eine Sprache keine TTS-Stimme installiert ist, bietet die UI
    // einen direkten Absprung in die Android-Sprachausgabe-Einstellungen an.
    var ttsSettingsHint by remember { mutableStateOf(false) }

    // Auto-Scroll zum neuesten Beitrag (Index 0, liegt bei reverseLayout "unten").
    val staffListState = rememberLazyListState()
    LaunchedEffect(conversation.size) {
        if (conversation.isNotEmpty()) staffListState.animateScrollToItem(0)
    }

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
            ttsSettingsHint = true
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
        activeVoskSession?.cancel()
        activeVoskSession = null
        isListening = false
        isPreparingSpeechModel = false
    }

    // Übersetzt einen fertig erkannten Satz und hängt ihn an den Verlauf an.
    fun translateAndAdd(from: Language, to: Language, text: String) {
        scope.launch {
            runCatching {
                TranslationEngine.translate(from, to, text)
            }.onSuccess { addEntry(from, to, text, it) }
                .onFailure { errorMessage = it.message ?: "Übersetzung fehlgeschlagen." }
        }
    }

    fun startLive(fromCustomer: Boolean) {
        // Sprachpaar zum Startzeitpunkt festhalten: Die gedrückte Sprechtaste
        // bestimmt die Richtung dieses Beitrags.
        val from = if (fromCustomer) customerLanguage else staffLanguage
        val to = if (fromCustomer) staffLanguage else customerLanguage
        val engine = SpeechEngine.engineFor(context, from.code)
        if (engine == LiveEngine.NONE || isListening) return
        errorMessage = null
        ttsSettingsHint = false
        // Laufende Sprachausgabe abbrechen, bevor das Mikrofon aufgeht.
        speechOutput.stop()
        liveTranscript = ""
        recordingFromCustomer = fromCustomer
        isListening = true

        if (engine == LiveEngine.VOSK) {
            // Gebündelte Vosk-Erkennung (Ukrainisch/Arabisch): stoppt nach dem
            // Satz von selbst - dasselbe Tap-to-Talk-Verhalten wie unten.
            scope.launch {
                activeVoskSession = VoskSpeechEngine.listen(
                    context = context,
                    languageCode = from.code,
                    onPartial = { text -> liveTranscript = text },
                    onFinal = { text ->
                        liveTranscript = text
                        activeVoskSession = null
                        isListening = false
                        translateAndAdd(from, to, text)
                    },
                    onError = { message ->
                        activeVoskSession = null
                        isListening = false
                        errorMessage = message
                    },
                )
            }
            return
        }

        val recognizer = SpeechEngine.createRecognizer(from.code)
        activeRecognizer = recognizer
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
                        // (Rückkopplungsschleife). Für den nächsten Satz die
                        // Sprechtaste einfach erneut antippen.
                        stopLive()
                        translateAndAdd(from, to, text)
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

    var pendingFromCustomer by remember { mutableStateOf(false) }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startLive(pendingFromCustomer)
        } else {
            errorMessage = "Ohne Mikrofonzugriff ist der Live-Modus nicht möglich."
        }
    }

    fun onMicButtonClick(fromCustomer: Boolean) {
        if (isListening) {
            // Nur die Seite, die gerade aufnimmt, kann die Aufnahme stoppen.
            if (recordingFromCustomer == fromCustomer) stopLive()
            return
        }
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            startLive(fromCustomer)
        } else {
            pendingFromCustomer = fromCustomer
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Jeder Sprachwechsel stoppt eine laufende Aufnahme, denn der Recognizer
    // ist fest auf seine Startsprache gebunden. Der Verlauf bleibt erhalten.
    LaunchedEffect(staffLanguage, customerLanguage) {
        if (isListening) stopLive()
    }

    DisposableEffect(Unit) {
        onDispose {
            stopLive()
            speechOutput.shutdown()
            TranslationEngine.closeAll()
            VoskSpeechEngine.closeAll()
        }
    }

    if (showModelManager) {
        ModelManagerScreen(
            onClose = { showModelManager = false },
            speechOutput = speechOutput,
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // ===== Kundenseite: um 180° gedreht, damit das Gegenüber alles in =====
        // ===== seiner Leserichtung sieht - mit eigener Sprach- und Sprechtaste. =====
        CustomerPane(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .rotate(180f),
            language = customerLanguage,
            onLanguageSelected = { customerLanguage = it },
            onLogoClick = { showModelManager = true },
            entries = conversation,
            pendingTranscript = liveTranscript.takeIf { isListening && recordingFromCustomer && it.isNotBlank() },
            micVisible = SpeechEngine.isLiveSupported(context, customerLanguage.code),
            isRecording = isListening && recordingFromCustomer,
            isPreparing = isPreparingSpeechModel && recordingFromCustomer,
            onMicClick = { onMicButtonClick(true) },
            onSpeakClick = { entry -> speakOrExplain(entry.textFor(customerLanguage), customerLanguage) },
        )

        // Trennlinie in ViP-Grün zwischen den beiden Hälften.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(MaterialTheme.colorScheme.primary),
        )

        // ===== Mitarbeiterseite: eigene Sprach- und Sprechtaste (unten links). =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.3f),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PaneHeader(
                    selected = staffLanguage,
                    labelFor = { it.displayName },
                    onSelected = { staffLanguage = it },
                    onLogoClick = { showModelManager = true },
                    extras = {
                        IconButton(onClick = { speechOutputEnabled = !speechOutputEnabled }) {
                            Icon(
                                imageVector = if (speechOutputEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                                contentDescription = if (speechOutputEnabled) {
                                    "Sprachausgabe ausschalten"
                                } else {
                                    "Sprachausgabe einschalten"
                                },
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        IconButton(
                            onClick = {
                                conversation.clear()
                                liveTranscript = ""
                                errorMessage = null
                                ttsSettingsHint = false
                            },
                            enabled = conversation.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Konversation löschen",
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    },
                )

                errorMessage?.let { message ->
                    Text(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (ttsSettingsHint) {
                    Button(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        onClick = {
                            if (!SpeechOutput.openTtsSettings(context)) {
                                errorMessage = "Die Sprachausgabe-Einstellungen ließen sich nicht öffnen."
                            }
                            ttsSettingsHint = false
                        },
                    ) {
                        Text("Sprachausgabe-Einstellungen öffnen")
                    }
                }

                if (isListening) {
                    Text(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        text = if (recordingFromCustomer) {
                            "Kunde spricht gerade …"
                        } else {
                            "Sprechen Sie jetzt - stoppt nach dem Satz automatisch"
                        },
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (!recordingFromCustomer && liveTranscript.isNotBlank()) {
                        Text(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            text = liveTranscript,
                        )
                    }
                }

                if (conversation.isEmpty()) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        text = "Noch keine Beiträge. Einfach das grüne Mikrofon unten " +
                            "links antippen und sprechen - der Kunde hat auf seiner " +
                            "Seite eine eigene Sprechtaste.",
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(start = 16.dp, end = 16.dp, bottom = 88.dp),
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
            }

            // Sprechtaste der Mitarbeiterseite: unten links, wie auf der
            // Kundenseite - beide Seiten bedienen sich identisch.
            MicButton(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
                isRecording = isListening && !recordingFromCustomer,
                isPreparing = isPreparingSpeechModel && !recordingFromCustomer,
                enabled = SpeechEngine.isLiveSupported(context, staffLanguage.code) &&
                    !(isListening && recordingFromCustomer),
                label = "Sprechen",
                contentDescription = "Zum Sprechen antippen (${staffLanguage.displayName})",
                onClick = { onMicButtonClick(false) },
            )
        }
    }
}

@Composable
private fun CustomerPane(
    modifier: Modifier = Modifier,
    language: Language,
    onLanguageSelected: (Language) -> Unit,
    onLogoClick: () -> Unit,
    entries: List<ConversationEntry>,
    pendingTranscript: String?,
    micVisible: Boolean,
    isRecording: Boolean,
    isPreparing: Boolean,
    onMicClick: () -> Unit,
    onSpeakClick: (ConversationEntry) -> Unit,
) {
    // Auto-Scroll zum neuesten Beitrag auch auf der Kundenseite.
    val listState = rememberLazyListState()
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PaneHeader(
                selected = language,
                labelFor = { it.nativeName },
                onSelected = onLanguageSelected,
                onLogoClick = onLogoClick,
            )

            Text(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = language.greeting,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleLarge,
            )

            pendingTranscript?.let { transcript ->
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = transcript,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = if (micVisible) 88.dp else 8.dp,
                    ),
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

        // Sprechtaste der Kundenseite: unten links (aus Kundensicht),
        // beschriftet in der Kundensprache. Für Sprachen ohne Live-Modus
        // (Ukrainisch, Arabisch) ausgeblendet - siehe README, "Offene Punkte".
        if (micVisible) {
            MicButton(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
                isRecording = isRecording,
                isPreparing = isPreparing,
                enabled = true,
                label = language.tapToSpeak,
                contentDescription = language.tapToSpeak,
                onClick = onMicClick,
            )
        }
    }
}

/**
 * Grüne Kopfleiste einer Bildschirmhälfte: links das ViP-Logo in einem weißen
 * Kreis (Klick öffnet die Sprachpakete), rechts die Sprachauswahl - dazwischen
 * optionale Aktionen ([extras], z. B. Lautsprecher/Papierkorb der
 * Mitarbeiterseite). Ein Antippen der Sprachauswahl blendet darunter eine
 * horizontal scrollbare Chip-Reihe aller 11 Sprachen ein. Bewusst ohne
 * Popup-Menü (das würde in der um 180° gedrehten Kundenhälfte falsch
 * positioniert/orientiert erscheinen) - stattdessen normales Layout, das mit
 * der Elternhälfte mitrotiert.
 */
@Composable
private fun PaneHeader(
    selected: Language,
    labelFor: (Language) -> String,
    onSelected: (Language) -> Unit,
    onLogoClick: () -> Unit,
    extras: @Composable RowScope.() -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .clickable(onClick = onLogoClick),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.vip_logo),
                    contentDescription = "Einstellungen (Sprachpakete)",
                    modifier = Modifier.size(26.dp),
                )
            }
            extras()
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = { expanded = !expanded },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(labelFor(selected))
            }
        }
        if (expanded) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(LanguageCatalog.all, key = { it.code }) { language ->
                    FilterChip(
                        selected = language.code == selected.code,
                        onClick = {
                            onSelected(language)
                            expanded = false
                        },
                        label = { Text(labelFor(language)) },
                    )
                }
            }
        }
    }
}

/**
 * Große runde Sprechtaste in ViP-Grün (rot während der Aufnahme, gedämpft
 * wenn deaktiviert) mit Beschriftung darunter - auf beiden Bildschirmhälften
 * unten links.
 */
@Composable
private fun MicButton(
    modifier: Modifier = Modifier,
    isRecording: Boolean,
    isPreparing: Boolean,
    enabled: Boolean,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (isPreparing) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp))
        }
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(
                    color = when {
                        isRecording -> MaterialTheme.colorScheme.error
                        enabled -> MaterialTheme.colorScheme.primary
                        else -> VipDisabled
                    },
                    shape = CircleShape,
                )
                .clickable { if (enabled || isRecording) onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Filled.MicOff else Icons.Filled.Mic,
                contentDescription = contentDescription,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Text(text = label, color = MaterialTheme.colorScheme.primary)
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
                Icon(
                    Icons.Filled.VolumeUp,
                    contentDescription = "Vorlesen",
                    tint = MaterialTheme.colorScheme.primary,
                )
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
                Icon(
                    Icons.Filled.VolumeUp,
                    contentDescription = "Übersetzung erneut vorlesen",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Vollbild-Menü "Sprachpakete": Übersetzungsmodelle (und Live-Erkennung, wo
 * verfügbar) pro Sprache vorab herunterladen, damit am Schalter keine
 * Wartezeit durch spontane Modell-Downloads entsteht. Zeigt außerdem pro
 * Sprache die Lage der **Sprachausgabe-Stimmen** (offline bereit / nur
 * online / fehlt) mit Probehören- und Installations-Button - so fällt eine
 * fehlende Stimme (typisch: Ukrainisch/Arabisch) VOR dem Kundengespräch auf.
 * Erreichbar über einen Klick auf das ViP-Logo (auf beiden Bildschirmhälften).
 */
@Composable
private fun ModelManagerScreen(
    onClose: () -> Unit,
    speechOutput: SpeechOutput,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val statuses = remember { mutableStateMapOf<String, ModelStatus>() }
    val voiceStatuses = remember { mutableStateMapOf<String, SpeechOutput.VoiceStatus>() }
    // Nur fuer Sprachen ohne ML-Kit-Live relevant (Ukrainisch/Arabisch).
    val voskStatuses = remember { mutableStateMapOf<String, ModelStatus>() }
    val voskProgress = remember { mutableStateMapOf<String, Int>() }

    LaunchedEffect(Unit) {
        LanguageCatalog.all.forEach { language ->
            statuses[language.code] = ModelStatus.PRUEFEN
            voiceStatuses[language.code] = speechOutput.voiceStatus(language)
            if (!language.mlKitLiveSpeech) {
                voskStatuses[language.code] =
                    if (VoskSpeechEngine.isModelReady(context, language.code)) ModelStatus.GELADEN else ModelStatus.FEHLT
            }
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
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Zurück",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text(
                text = "Sprachpakete",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Text(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = "Einmalig mit Internet (am besten WLAN) vorbereiten - danach " +
                "übersetzt und spricht die App komplett offline, ohne Wartezeit " +
                "beim Kunden. Empfehlung: alle häufig gebrauchten Sprachen vorab " +
                "laden und per Probehören prüfen, ob die Stimme da ist. Fehlende " +
                "Stimmen lassen sich über \"Stimme installieren\" in den Android-" +
                "Einstellungen nachladen (dort: Google Speech Services > " +
                "Sprachdaten installieren).",
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(LanguageCatalog.all, key = { it.code }) { language ->
                val status = statuses[language.code] ?: ModelStatus.PRUEFEN
                val voiceStatus = voiceStatuses[language.code] ?: SpeechOutput.VoiceStatus.NICHT_BEREIT
                val voskStatus = voskStatuses[language.code] ?: ModelStatus.PRUEFEN
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(text = language.displayName, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = when (status) {
                                        ModelStatus.PRUEFEN -> "Übersetzung: prüfe …"
                                        ModelStatus.FEHLT -> "Übersetzung: noch nicht geladen"
                                        ModelStatus.LAEDT -> "Übersetzung: wird heruntergeladen …"
                                        ModelStatus.GELADEN ->
                                            if (language.mlKitLiveSpeech) {
                                                "Übersetzung bereit (inkl. Live-Erkennung)"
                                            } else {
                                                "Übersetzung bereit - Live-Erkennung über eigenes " +
                                                    "Sprachmodell (siehe unten)"
                                            }
                                        ModelStatus.FEHLER -> "Übersetzung: Download fehlgeschlagen - Internetverbindung prüfen"
                                    },
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                                Text(
                                    text = when (voiceStatus) {
                                        SpeechOutput.VoiceStatus.NICHT_BEREIT ->
                                            "Sprachausgabe: wird vorbereitet …"
                                        SpeechOutput.VoiceStatus.OFFLINE_BEREIT ->
                                            "Sprachausgabe: Offline-Stimme bereit"
                                        SpeechOutput.VoiceStatus.NUR_ONLINE ->
                                            "Sprachausgabe: nur Online-Stimme installiert - wird aus " +
                                                "Datenschutzgründen nicht genutzt, bitte Offline-Stimme laden"
                                        SpeechOutput.VoiceStatus.FEHLT ->
                                            "Sprachausgabe: keine Stimme installiert"
                                    },
                                    color = if (voiceStatus == SpeechOutput.VoiceStatus.OFFLINE_BEREIT) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                )
                                if (!language.mlKitLiveSpeech) {
                                    val progress = voskProgress[language.code]
                                    Text(
                                        text = when (voskStatus) {
                                            ModelStatus.PRUEFEN -> "Live-Erkennung: prüfe …"
                                            ModelStatus.FEHLT ->
                                                "Live-Erkennung: eigenes Sprachmodell noch nicht " +
                                                    "geladen (größerer Download, am besten über WLAN)"
                                            ModelStatus.LAEDT ->
                                                if (progress != null) {
                                                    "Live-Erkennung: Sprachmodell wird geladen … $progress %"
                                                } else {
                                                    "Live-Erkennung: Sprachmodell wird geladen …"
                                                }
                                            ModelStatus.GELADEN -> "Live-Erkennung: Sprachmodell bereit ✓"
                                            ModelStatus.FEHLER ->
                                                "Live-Erkennung: Download fehlgeschlagen - " +
                                                    "Internetverbindung prüfen"
                                        },
                                        color = if (voskStatus == ModelStatus.GELADEN) {
                                            MaterialTheme.colorScheme.tertiary
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        },
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    statuses[language.code] = ModelStatus.LAEDT
                                    if (!language.mlKitLiveSpeech) voskStatuses[language.code] = ModelStatus.LAEDT
                                    scope.launch {
                                        runCatching {
                                            TranslationEngine.downloadModel(language)
                                            SpeechEngine.prepareModel(context, language.code) { downloaded, total ->
                                                if (total != null && total > 0) {
                                                    voskProgress[language.code] = (downloaded * 100 / total).toInt()
                                                }
                                            }
                                        }.onSuccess {
                                            statuses[language.code] = ModelStatus.GELADEN
                                            if (!language.mlKitLiveSpeech) {
                                                voskStatuses[language.code] = ModelStatus.GELADEN
                                            }
                                        }.onFailure {
                                            statuses[language.code] = ModelStatus.FEHLER
                                            if (!language.mlKitLiveSpeech) {
                                                voskStatuses[language.code] = ModelStatus.FEHLER
                                            }
                                        }
                                    }
                                },
                                enabled = status == ModelStatus.FEHLT || status == ModelStatus.FEHLER ||
                                    (!language.mlKitLiveSpeech &&
                                        (voskStatus == ModelStatus.FEHLT || voskStatus == ModelStatus.FEHLER)),
                            ) {
                                Text(
                                    when {
                                        status == ModelStatus.LAEDT || voskStatus == ModelStatus.LAEDT -> "Lädt …"
                                        status == ModelStatus.FEHLT || status == ModelStatus.FEHLER -> "Laden"
                                        !language.mlKitLiveSpeech &&
                                            (voskStatus == ModelStatus.FEHLT || voskStatus == ModelStatus.FEHLER) -> "Laden"
                                        else -> "Geladen ✓"
                                    },
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Probehören spricht die Begrüßung - so lässt sich die
                            // Stimme VOR dem Kundengespräch prüfen. Schlägt es fehl,
                            // direkt in die Stimmen-Einstellungen springen.
                            Button(
                                onClick = {
                                    if (!speechOutput.speak(language.greeting, language)) {
                                        SpeechOutput.openTtsSettings(context)
                                    }
                                    voiceStatuses[language.code] = speechOutput.voiceStatus(language)
                                },
                            ) {
                                Text("Probehören")
                            }
                            if (voiceStatus == SpeechOutput.VoiceStatus.NUR_ONLINE ||
                                voiceStatus == SpeechOutput.VoiceStatus.FEHLT
                            ) {
                                Button(
                                    onClick = { SpeechOutput.openTtsSettings(context) },
                                ) {
                                    Text("Stimme installieren")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
