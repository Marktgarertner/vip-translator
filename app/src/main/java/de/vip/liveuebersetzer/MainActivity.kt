package de.vip.liveuebersetzer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import com.google.mlkit.genai.speechrecognition.SpeechRecognizer
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

/**
 * Hält Zustand und Engines zusammen (Aufnahme, Übersetzung, Sprachausgabe,
 * Navigation) und reicht an die zustandslosen Bildschirme nur noch Daten und
 * Rückrufe weiter - die Darstellung selbst steht in [ConversationScreen],
 * [SetupScreen], [ModelsScreen], [GlossaryScreen] und [PhrasesScreen].
 */
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

    fun addEntry(
        fromCustomer: Boolean,
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
            ConversationEntry(nextEntryId++, fromCustomer, from, to, original, translated, recognized),
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
    fun translateAndAdd(fromCustomer: Boolean, from: Language, to: Language, text: String) {
        // ÖPNV-Fachwortschatz zuerst: Das korrigiert zerlegte Komposita
        // ("Kassen Automat") und Eigennamen ("Potstam") noch VOR der
        // Übersetzung - ML Kit bekommt dadurch den richtigen Begriff statt
        // zweier Bruchstücke. Siehe TransitGlossary.
        val corrected = TransitGlossary.correct(from, text)
        val recognized = text.takeIf { it != corrected }
        scope.launch {
            runCatching {
                TranslationEngine.translate(from, to, corrected)
            }.onSuccess { addEntry(fromCustomer, from, to, corrected, it, recognized) }
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
                            translateAndAdd(fromCustomer, from, to, text)
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
                        translateAndAdd(fromCustomer, from, to, text)
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
            ModelsScreen(
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
                    translateAndAdd(false, LanguageCatalog.defaultSource, customerLanguage, phrase)
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

    ConversationScreen(
        state = ConversationUiState(
            staffLanguage = staffLanguage,
            customerLanguage = customerLanguage,
            entries = conversation,
            listening = when {
                !isListening -> ListeningSide.NONE
                recordingFromCustomer -> ListeningSide.CUSTOMER
                else -> ListeningSide.STAFF
            },
            preparing = isPreparingSpeechModel,
            preparingMegabytes = preparingMegabytes,
            liveTranscript = liveTranscript,
            errorMessage = errorMessage,
            ttsSettingsHint = ttsSettingsHint,
            customerMicReason = customerMicReason,
            staffMicReason = staffMicReason,
            speechOutputEnabled = speechOutputEnabled,
        ),
        actions = ConversationActions(
            onStaffLanguage = { staffLanguage = it },
            onCustomerLanguage = { customerLanguage = it },
            onLogoClick = { screen = Screen.SETUP },
            onStaffMic = { onMicButtonClick(false) },
            onCustomerMic = { onMicButtonClick(true) },
            onSpeakForCustomer = { entry ->
                speakOrExplain(entry.textFor(customerLanguage), entry.languageFor(customerLanguage))
            },
            onSpeakForStaff = { entry -> speakOrExplain(entry.translatedText, entry.targetLanguage) },
            onToggleSpeechOutput = { speechOutputEnabled = !speechOutputEnabled },
            onOpenPhrases = { screen = Screen.PHRASES },
            onNextCustomer = { resetForNextCustomer() },
            onOpenTtsSettings = {
                if (!SpeechOutput.openTtsSettings(context)) {
                    errorMessage = "Die Sprachausgabe-Einstellungen ließen sich nicht öffnen."
                }
                ttsSettingsHint = false
            },
        ),
    )
}
