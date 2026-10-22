package de.vip.liveuebersetzer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.mlkit.genai.speechrecognition.SpeechRecognizer
import de.vip.liveuebersetzer.ui.theme.VipDisabled
import de.vip.liveuebersetzer.ui.theme.VipLiveUebersetzerTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Welcher Bildschirm gerade sichtbar ist. */
private enum class Screen { CONVERSATION, SETUP, MODELS, GLOSSARY, PHRASES }

/**
 * Wie lange nach dem letzten Beitrag gewartet wird, bis sich der Verlauf von
 * selbst abräumt (Datenschutz-Sicherheitsnetz, falls "Nächster Kunde"
 * vergessen wird).
 */
private const val AUTO_RESET_MILLIS = 5L * 60L * 1000L

private const val MEGABYTE = 1024L * 1024L

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
    /**
     * Ursprünglich erkannter Wortlaut, falls [TransitGlossary] etwas korrigiert
     * hat - sonst `null`. Wird auf der Mitarbeiterseite als Hinweis angezeigt,
     * damit eine Fehlkorrektur auffällt und nicht unbemerkt bleibt.
     */
    val recognizedText: String? = null,
) {
    /** Text dieses Beitrags, wie ihn die Bildschirmhälfte mit Sprache [pane] zeigt. */
    fun textFor(pane: Language): String = when (pane.code) {
        sourceLanguage.code -> originalText
        else -> translatedText
    }

    /** Das jeweils andere Gegenstück zu [textFor]. */
    fun counterpartFor(pane: Language): String = when (pane.code) {
        sourceLanguage.code -> translatedText
        else -> originalText
    }

    /**
     * Sprache, in der [textFor] tatsächlich geschrieben ist. Nicht zwingend
     * [pane]: Wurde die Kundensprache nach diesem Beitrag gewechselt, zeigt
     * die Kundenseite die Übersetzung in der *damaligen* Zielsprache - und
     * genau mit deren Stimme muss sie vorgelesen werden, nicht mit der jetzt
     * gewählten.
     */
    fun languageFor(pane: Language): Language = when (pane.code) {
        sourceLanguage.code -> sourceLanguage
        else -> targetLanguage
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
    val view = LocalView.current

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
    // Bisher geladene MB, wenn ML Kit beim ersten Sprechen sein Modell nachlädt.
    var preparingMegabytes by remember { mutableStateOf<Long?>(null) }
    // Laufende Aufnahme-Coroutine - für ML Kit UND Vosk. Beim Vosk-Pfad lief
    // der Start vorher ohne Job: Wer die Sprechtaste zum Stoppen antippte,
    // während das Modell noch von der Platte geladen wurde, bekam "Aufnahme
    // aus" angezeigt - die Session startete danach trotzdem, nahm unsichtbar
    // weiter auf, und ihr nächster Satz landete aus dem Nichts im Verlauf.
    var recognizerJob by remember { mutableStateOf<Job?>(null) }
    var activeRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var activeVoskSession by remember { mutableStateOf<VoskSpeechSession?>(null) }

    val speechOutput = remember { SpeechOutput(context) }
    var speechOutputEnabled by remember { mutableStateOf(true) }
    // Beim allerersten Start direkt in die Einrichtung, damit niemand vor
    // einem scheinbar fertigen, aber unvorbereiteten Gerät steht.
    var screen by remember {
        mutableStateOf(if (AppSettings.isSetupDone(context)) Screen.CONVERSATION else Screen.SETUP)
    }
    // Wenn für eine Sprache keine TTS-Stimme installiert ist, bietet die UI
    // einen direkten Absprung in die Android-Sprachausgabe-Einstellungen an.
    var ttsSettingsHint by remember { mutableStateOf(false) }

    // Selbst gepflegte Fachbegriffe einmalig in den Wortschatz laden.
    LaunchedEffect(Unit) { AppSettings.applyCustomTerms(context) }

    // Zurück-Taste führt in den Untermenüs eine Ebene hoch statt die App zu
    // beenden (vorher: Zurück im Sprachpakete-Menü = App zu, Verlauf weg).
    BackHandler(enabled = screen != Screen.CONVERSATION) {
        screen = when (screen) {
            Screen.MODELS, Screen.GLOSSARY -> Screen.SETUP
            else -> Screen.CONVERSATION
        }
    }

    // Display an, solange ein Gespräch läuft: Am Schalter liest der Kunde die
    // Übersetzung in Ruhe - ein Bildschirm, der dabei dunkel wird, ist das
    // Letzte, was helfen würde. Nach "Nächster Kunde" bzw. dem automatischen
    // Abräumen darf das Gerät wieder normal abdunkeln.
    val keepScreenOn = isListening || conversation.isNotEmpty()
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // Auto-Scroll zum neuesten Beitrag (Index 0, liegt bei reverseLayout "unten").
    val staffListState = rememberLazyListState()
    LaunchedEffect(conversation.size) {
        if (conversation.isNotEmpty()) staffListState.animateScrollToItem(0)
    }

    fun addEntry(
        from: Language,
        to: Language,
        original: String,
        translated: String,
        recognized: String? = null,
    ) {
        // Neuester Eintrag an Index 0; die Listen rendern mit reverseLayout,
        // sodass er auf beiden Bildschirmhälften wie in einem Chat "unten" steht.
        conversation.add(
            0,
            ConversationEntry(nextEntryId++, from, to, original, translated, recognized),
        )
        // Nie sprechen, solange das Mikrofon offen ist - sonst erkennt die
        // Erkennung die eigene Ausgabe als Eingabe (Rückkopplungsschleife).
        if (speechOutputEnabled && !isListening && translated.isNotBlank() && !speechOutput.speak(translated, to)) {
            // Vorher blieb die App hier einfach stumm, ohne zu sagen, warum.
            errorMessage = speechOutput.explainSilence(to)
            ttsSettingsHint = true
        }
    }

    fun speakOrExplain(text: String, language: Language) {
        if (!speechOutput.speak(text, language)) {
            errorMessage = speechOutput.explainSilence(language)
            ttsSettingsHint = true
        }
    }

    fun stopLive() {
        recognizerJob?.cancel()
        recognizerJob = null
        activeRecognizer?.let { SpeechEngine.stopAndRelease(it) }
        activeRecognizer = null
        activeVoskSession?.cancel()
        activeVoskSession = null
        isListening = false
        isPreparingSpeechModel = false
        preparingMegabytes = null
    }

    // Übersetzt einen fertig erkannten Satz und hängt ihn an den Verlauf an.
    fun translateAndAdd(from: Language, to: Language, text: String) {
        // ÖPNV-Fachwortschatz zuerst: Das korrigiert zerlegte Komposita
        // ("Kassen Automat") und Eigennamen ("Potstam") noch VOR der
        // Übersetzung - ML Kit bekommt dadurch den richtigen Begriff statt
        // zweier Bruchstücke. Siehe TransitGlossary.
        val corrected = TransitGlossary.correct(from, text)
        val recognized = text.takeIf { it != corrected }
        scope.launch {
            runCatching {
                TranslationEngine.translate(from, to, corrected)
            }.onSuccess { addEntry(from, to, corrected, it, recognized) }
                .onFailure {
                    if (it is CancellationException) throw it
                    errorMessage = "Übersetzung fehlgeschlagen" +
                        (it.message?.let { reason -> ": $reason" } ?: ".")
                }
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
            // Gebündelte Vosk-Erkennung: stoppt nach dem Satz von selbst -
            // dasselbe Tap-to-Talk-Verhalten wie beim ML-Kit-Pfad unten.
            recognizerJob = scope.launch {
                try {
                    // Beim ersten Sprechen wird das Modell von der Platte in
                    // den Speicher geladen - das kann spürbar dauern, daher
                    // derselbe "wird vorbereitet"-Hinweis wie beim ML-Kit-Pfad.
                    isPreparingSpeechModel = true
                    val session = VoskSpeechEngine.listen(
                        context = context,
                        languageCode = from.code,
                        onPartial = { text -> liveTranscript = text },
                        onFinal = { text ->
                            liveTranscript = text
                            activeVoskSession = null
                            recognizerJob = null
                            isListening = false
                            translateAndAdd(from, to, text)
                        },
                        onError = { message ->
                            activeVoskSession = null
                            recognizerJob = null
                            isListening = false
                            errorMessage = message
                        },
                    )
                    if (!isActive) {
                        // Stopp-Tipp kam, während das Modell noch lud: Die
                        // gerade gestartete Aufnahme sofort wieder beenden.
                        session.cancel()
                    } else {
                        activeVoskSession = session
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Z. B. Mikrofon nicht initialisierbar oder Modell auf der
                    // Platte beschädigt - ohne dieses Fangnetz stürzte die
                    // App ab, statt eine Meldung zu zeigen.
                    activeVoskSession = null
                    isListening = false
                    errorMessage = e.message
                        ?: "Spracherkennung konnte nicht gestartet werden - im Menü \"Sprachpakete\" das Modell neu laden."
                } finally {
                    isPreparingSpeechModel = false
                }
            }
            return
        }

        val recognizer = SpeechEngine.createRecognizer(from.code)
        activeRecognizer = recognizer
        recognizerJob = scope.launch {
            try {
                isPreparingSpeechModel = true
                SpeechEngine.ensureModelDownloaded(recognizer) { bytes ->
                    val megabytes = bytes / MEGABYTE
                    if (preparingMegabytes != megabytes) preparingMegabytes = megabytes
                }
                isPreparingSpeechModel = false
                preparingMegabytes = null
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
                preparingMegabytes = null
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

    /**
     * Setzt die App für den nächsten Kunden zurück: Verlauf weg, Aufnahme aus,
     * Sprachen auf Standard. Datenschutz-relevant - niemand soll das Gespräch
     * des Vorgängers sehen.
     */
    fun resetForNextCustomer() {
        stopLive()
        speechOutput.stop()
        conversation.clear()
        liveTranscript = ""
        errorMessage = null
        ttsSettingsHint = false
        staffLanguage = LanguageCatalog.defaultSource
        customerLanguage = LanguageCatalog.defaultTarget
    }

    // Sicherheitsnetz, falls das Zurücksetzen vergessen wird: Nach einer
    // Weile ohne neuen Beitrag räumt sich der Verlauf von selbst ab.
    LaunchedEffect(conversation.size, isListening) {
        if (conversation.isNotEmpty() && !isListening) {
            delay(AUTO_RESET_MILLIS)
            resetForNextCustomer()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopLive()
            speechOutput.shutdown()
            TranslationEngine.closeAll()
            VoskSpeechEngine.closeAll()
        }
    }

    when (screen) {
        Screen.SETUP -> {
            SetupScreen(
                speechOutput = speechOutput,
                onOpenModels = { screen = Screen.MODELS },
                onOpenGlossary = { screen = Screen.GLOSSARY },
                onOpenPhrases = { screen = Screen.PHRASES },
                onClose = { screen = Screen.CONVERSATION },
            )
            return
        }
        Screen.MODELS -> {
            ModelManagerScreen(
                onClose = { screen = Screen.SETUP },
                speechOutput = speechOutput,
            )
            return
        }
        Screen.GLOSSARY -> {
            GlossaryScreen(onClose = { screen = Screen.SETUP })
            return
        }
        Screen.PHRASES -> {
            PhrasesScreen(
                customerLanguage = customerLanguage,
                onPhraseSelected = { phrase ->
                    // Bausteine sind auf Deutsch formuliert (siehe StaffPhrases),
                    // deshalb immer aus dem Deutschen übersetzen - unabhängig
                    // davon, welche Sprache die Mitarbeiterseite gerade zeigt.
                    translateAndAdd(LanguageCatalog.defaultSource, customerLanguage, phrase)
                    screen = Screen.CONVERSATION
                },
                onClose = { screen = Screen.CONVERSATION },
            )
            return
        }
        Screen.CONVERSATION -> Unit
    }

    // Warum eine Seite gerade keine Sprechtaste hat - in Klartext auf der
    // Mitarbeiterseite, statt die Taste kommentarlos auszublenden. Die
    // Vosk-Statusmaps werden mitgelesen, damit ein im Hintergrund fertig
    // gewordener Modell-Download die Sprechtaste sofort einblendet und nicht
    // erst bei der nächsten Interaktion.
    val customerVoskState = ModelDownloads.vosk[customerLanguage.code]
    val staffVoskState = ModelDownloads.vosk[staffLanguage.code]
    val customerMicReason = remember(customerLanguage, customerVoskState) {
        SpeechEngine.unavailableReason(context, customerLanguage)
    }
    val staffMicReason = remember(staffLanguage, staffVoskState) {
        SpeechEngine.unavailableReason(context, staffLanguage)
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
            onLogoClick = { screen = Screen.SETUP },
            entries = conversation,
            pendingTranscript = liveTranscript.takeIf { isListening && recordingFromCustomer && it.isNotBlank() },
            micVisible = customerMicReason == null,
            isRecording = isListening && recordingFromCustomer,
            isPreparing = isPreparingSpeechModel && recordingFromCustomer,
            onMicClick = { onMicButtonClick(true) },
            onSpeakClick = { entry ->
                speakOrExplain(entry.textFor(customerLanguage), entry.languageFor(customerLanguage))
            },
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
                    onLogoClick = { screen = Screen.SETUP },
                    actions = {
                        IconButton(onClick = { speechOutputEnabled = !speechOutputEnabled }) {
                            Icon(
                                imageVector = if (speechOutputEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                contentDescription = if (speechOutputEnabled) {
                                    "Sprachausgabe ausschalten"
                                } else {
                                    "Sprachausgabe einschalten"
                                },
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        IconButton(onClick = { screen = Screen.PHRASES }) {
                            Icon(
                                Icons.AutoMirrored.Filled.List,
                                contentDescription = "Schnellbausteine öffnen",
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        // Ersetzt den früheren Papierkorb: räumt alles für
                        // den nächsten Kunden ab, nicht nur den Verlauf.
                        Button(
                            onClick = { resetForNextCustomer() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Text("Nächster Kunde")
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
                listOfNotNull(
                    customerMicReason,
                    staffMicReason.takeIf { staffLanguage.code != customerLanguage.code },
                ).forEach { reason ->
                    Text(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        text = reason,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (isListening) {
                    Text(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        text = when {
                            isPreparingSpeechModel && preparingMegabytes != null ->
                                "Erkennungsmodell wird einmalig geladen … $preparingMegabytes MB"
                            isPreparingSpeechModel -> "Spracherkennung wird vorbereitet …"
                            recordingFromCustomer -> "Kunde spricht gerade …"
                            else -> "Sprechen Sie jetzt - stoppt nach dem Satz automatisch"
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
                enabled = staffMicReason == null && !(isListening && recordingFromCustomer),
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
        // (z. B. Vosk-Modell noch nicht geladen) ausgeblendet - die
        // Mitarbeiterseite zeigt dann in Klartext, warum.
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
 * Kreis (Klick öffnet die Einrichtung), rechts die Sprachauswahl - darunter
 * optionale Aktionen ([actions], z. B. Lautsprecher/"Nächster Kunde" der
 * Mitarbeiterseite). Ein Antippen der Sprachauswahl blendet darunter eine
 * horizontal scrollbare Chip-Reihe aller Sprachen ein. Bewusst ohne
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
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        // Logo und Sprachauswahl bleiben in einer eigenen Zeile, Aktionen
        // kommen darunter. Alles in EINE Zeile zu packen hat auf einem
        // normalen Telefon nicht gepasst: Die Sprachtaste wird zuletzt
        // gemessen, bekam nur noch Restbreite und brach "Deutsch"
        // buchstabenweise um ("D e ut sc h").
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        contentDescription = "Einstellungen",
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = { expanded = !expanded },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    // maxLines als Sicherheitsnetz: lieber abgeschnitten als
                    // buchstabenweise umgebrochen.
                    Text(text = labelFor(selected), maxLines = 1)
                }
            }
            actions?.let { actionsRow ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    actionsRow()
                }
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
                    Icons.AutoMirrored.Filled.VolumeUp,
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
                // Nur wenn der Fachwortschatz etwas geändert hat: Original
                // zeigen, damit eine Fehlkorrektur auffällt (siehe TransitGlossary).
                entry.recognizedText?.let { recognized ->
                    Text(
                        text = "wörtlich erkannt: $recognized",
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            IconButton(onClick = onSpeakClick) {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Übersetzung erneut vorlesen",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Vollbild-Menü "Sprachpakete": Übersetzungs- und Erkennungsmodelle pro
 * Sprache vorab herunterladen, damit am Schalter keine Wartezeit durch
 * spontane Modell-Downloads entsteht. Zeigt außerdem pro Sprache die Lage der
 * **Sprachausgabe-Stimmen** (offline bereit / nur online / fehlt) mit
 * Probehören- und Installations-Button - so fällt eine fehlende Stimme VOR
 * dem Kundengespräch auf. Der Download-Zustand lebt in [ModelDownloads] und
 * überlebt das Verlassen des Menüs. Erreichbar über den Einrichtungs-
 * Assistenten (Logo antippen → "Sprachpakete öffnen").
 */
@Composable
private fun ModelManagerScreen(
    onClose: () -> Unit,
    speechOutput: SpeechOutput,
) {
    val context = LocalContext.current
    val voiceStatuses = remember { mutableStateMapOf<String, SpeechOutput.VoiceStatus>() }

    LaunchedEffect(Unit) {
        // Erst auf die TTS-Engine warten - sonst stand direkt nach dem
        // App-Start bei allen Sprachen dauerhaft "wird vorbereitet".
        speechOutput.awaitReady()
        LanguageCatalog.all.forEach { voiceStatuses[it.code] = speechOutput.voiceStatus(it) }
        ModelDownloads.refresh(context)
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
                    Icons.AutoMirrored.Filled.ArrowBack,
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
                "beim Kunden. Downloads laufen weiter, auch wenn dieses Menü " +
                "verlassen wird. Empfehlung: alle häufig gebrauchten Sprachen " +
                "vorab laden und per Probehören prüfen, ob die Stimme da ist. " +
                "Fehlende Stimmen lassen sich über \"Stimme installieren\" in den " +
                "Android-Einstellungen nachladen (dort: Google Speech Services > " +
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
                LanguagePackageCard(
                    language = language,
                    voiceStatus = voiceStatuses[language.code] ?: SpeechOutput.VoiceStatus.NICHT_BEREIT,
                    onListen = {
                        // Probehören spricht die Begrüßung - so lässt sich die
                        // Stimme VOR dem Kundengespräch prüfen. Schlägt es fehl,
                        // direkt in die Stimmen-Einstellungen springen.
                        if (!speechOutput.speak(language.greeting, language)) {
                            SpeechOutput.openTtsSettings(context)
                        }
                        voiceStatuses[language.code] = speechOutput.voiceStatus(language)
                    },
                )
            }
        }
    }
}

/** Eine Zeile des Sprachpakete-Menüs: Status, "Laden", Probehören, Alternativen. */
@Composable
private fun LanguagePackageCard(
    language: Language,
    voiceStatus: SpeechOutput.VoiceStatus,
    onListen: () -> Unit,
) {
    val context = LocalContext.current
    val code = language.code
    val translationStatus = ModelDownloads.translation[code] ?: ModelStatus.PRUEFEN
    val mlKitStatus = ModelDownloads.mlKitSpeech[code]
    val voskStatus = ModelDownloads.vosk[code]
    val voskSupported = VoskSpeechEngine.isSupported(code)
    // Lesen der Snapshot-Maps oben sorgt dafür, dass diese Karte neu
    // gezeichnet wird, sobald ein Download den Zustand ändert - auch die
    // Engine-Weiche hängt davon ab (Vosk geladen -> Vosk).
    val engine = SpeechEngine.engineFor(context, code)
    val running = ModelDownloads.isRunning(language)
    val speechReady = ModelDownloads.isSpeechReady(context, language)
    val voskSizeHint = VoskSpeechEngine.modelSizeMb(code)?.let { " (~$it MB, am besten über WLAN)" } ?: ""

    val speechLine: String
    val speechOk: Boolean
    when (engine) {
        LiveEngine.VOSK -> {
            speechLine = "Live-Erkennung: eigenes Sprachmodell bereit ✓"
            speechOk = true
        }
        LiveEngine.MLKIT -> {
            speechOk = mlKitStatus == ModelStatus.GELADEN
            speechLine = when (mlKitStatus) {
                null, ModelStatus.PRUEFEN -> "Live-Erkennung: prüfe …"
                ModelStatus.FEHLT -> "Live-Erkennung: Erkennungsmodell noch nicht geladen"
                ModelStatus.LAEDT -> {
                    val megabytes = ModelDownloads.mlKitSpeechBytes[code]?.let { it / MEGABYTE }
                    "Live-Erkennung: Erkennungsmodell wird geladen …" +
                        (megabytes?.let { " $it MB" } ?: "")
                }
                ModelStatus.GELADEN -> "Live-Erkennung: bereit ✓"
                ModelStatus.FEHLER ->
                    "Live-Erkennung: Download fehlgeschlagen oder Status nicht ermittelbar - " +
                        "Internetverbindung prüfen und erneut laden"
                ModelStatus.NICHT_VERFUEGBAR ->
                    "Live-Erkennung: über die Android-Erkennung auf diesem Gerät nicht " +
                        "verfügbar" + if (voskSupported) {
                        " - stattdessen unten das eigene Sprachmodell laden"
                    } else {
                        " (in den Android-Einstellungen die Offline-Spracheingabe für " +
                            "diese Sprache installieren)"
                    }
            }
        }
        LiveEngine.NONE -> {
            speechOk = false
            speechLine = when {
                !voskSupported -> "Live-Erkennung: braucht Android 12 oder neuer"
                else -> when (voskStatus) {
                    null, ModelStatus.PRUEFEN -> "Live-Erkennung: prüfe …"
                    ModelStatus.LAEDT -> {
                        val percent = ModelDownloads.voskProgress[code]
                        "Live-Erkennung: Sprachmodell wird geladen …" + (percent?.let { " $it %" } ?: "")
                    }
                    ModelStatus.FEHLER -> "Live-Erkennung: Download fehlgeschlagen - Internetverbindung prüfen"
                    else -> "Live-Erkennung: eigenes Sprachmodell noch nicht geladen$voskSizeHint"
                }
            }
        }
    }
    // Zweigleisige Sprache, deren Vosk-Alternative gerade lädt, während ML Kit
    // (noch) die aktive Engine ist.
    val voskAlternativeLine = if (engine == LiveEngine.MLKIT && voskStatus == ModelStatus.LAEDT) {
        val percent = ModelDownloads.voskProgress[code]
        "Eigenes Sprachmodell wird geladen …" + (percent?.let { " $it %" } ?: "")
    } else {
        null
    }
    val voskAlternativeOffered = engine == LiveEngine.MLKIT && voskSupported && !running &&
        (voskStatus == ModelStatus.FEHLT || voskStatus == ModelStatus.FEHLER)

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
                        text = when (translationStatus) {
                            ModelStatus.PRUEFEN -> "Übersetzung: prüfe …"
                            ModelStatus.FEHLT -> "Übersetzung: noch nicht geladen"
                            ModelStatus.LAEDT -> "Übersetzung: wird heruntergeladen …"
                            ModelStatus.GELADEN -> "Übersetzung: bereit ✓"
                            ModelStatus.FEHLER -> "Übersetzung: Download fehlgeschlagen - Internetverbindung prüfen"
                            ModelStatus.NICHT_VERFUEGBAR -> "Übersetzung: auf diesem Gerät nicht verfügbar"
                        },
                        color = if (translationStatus == ModelStatus.GELADEN) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    Text(
                        text = when (voiceStatus) {
                            SpeechOutput.VoiceStatus.NICHT_BEREIT ->
                                "Sprachausgabe: wird vorbereitet …"
                            SpeechOutput.VoiceStatus.OFFLINE_BEREIT ->
                                "Sprachausgabe: Offline-Stimme bereit ✓"
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
                    Text(
                        text = speechLine,
                        color = if (speechOk) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    voskAlternativeLine?.let {
                        Text(text = it, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
                val needsDownload = ModelDownloads.needsDownload(context, language)
                Button(
                    onClick = { ModelDownloads.start(context, language) },
                    enabled = needsDownload && !running,
                ) {
                    Text(
                        when {
                            running -> "Lädt …"
                            translationStatus == ModelStatus.GELADEN && speechReady -> "Geladen ✓"
                            else -> "Laden"
                        },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onListen) {
                    Text("Probehören")
                }
                if (voiceStatus == SpeechOutput.VoiceStatus.NUR_ONLINE ||
                    voiceStatus == SpeechOutput.VoiceStatus.FEHLT
                ) {
                    Button(onClick = { SpeechOutput.openTtsSettings(context) }) {
                        Text("Stimme installieren")
                    }
                }
            }
            if (voskAlternativeOffered) {
                // Zweigleisige Sprache (Chinesisch, Japanisch, Koreanisch,
                // Hindi): Das gebündelte Vosk-Modell ist der am Testgerät
                // erprobte Weg und hat Vorrang, sobald es geladen ist.
                Button(onClick = { ModelDownloads.startVosk(context, language) }) {
                    Text(
                        "Eigenes Sprachmodell laden" +
                            (VoskSpeechEngine.modelSizeMb(code)?.let { " (~$it MB)" } ?: ""),
                    )
                }
                Text(
                    text = "Alternative zur Android-Erkennung - z. B. wenn diese auf dem " +
                        "Gerät nicht verfügbar ist oder schlecht versteht. Hat nach dem " +
                        "Laden Vorrang.",
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}
