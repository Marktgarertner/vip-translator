package de.vip.liveuebersetzer

import android.content.Context
import android.os.Build
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.audio.AudioSource
import com.google.mlkit.genai.speechrecognition.SpeechRecognition
import com.google.mlkit.genai.speechrecognition.SpeechRecognizer
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerResponse
import com.google.mlkit.genai.speechrecognition.speechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.speechRecognizerRequest
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Welche Live-Erkennungs-Engine für eine Sprache auf diesem Gerät zuständig ist. */
enum class LiveEngine { MLKIT, VOSK, NONE }

/**
 * Dünner Wrapper um ML Kit GenAI Speech Recognition
 * (`com.google.mlkit:genai-speech-recognition:1.0.0-alpha1`, Alpha-Status,
 * Paket `com.google.mlkit.genai.speechrecognition`) plus die Weiche
 * [engineFor], die pro Sprache zwischen ML Kit und [VoskSpeechEngine] entscheidet.
 *
 * Bewusste Entscheidungen (siehe README, bitte nicht ohne Rücksprache ändern):
 *  - Es wird ausschließlich der "Basic"-Modus verwendet. Der "Advanced"-Modus
 *    (Gemini-Nano-Qualität, breitere Sprachabdeckung inkl. Arabisch) läuft
 *    laut Google-Doku (Stand 10/2026) exklusiv auf Pixel 10/11 - die App
 *    soll aber auf der gesamten ViP-Geräteflotte lauffähig sein.
 *  - Kein Cloud-Fallback. Sprachen ohne ML-Kit-Live (Ukrainisch, Arabisch,
 *    Persisch) laufen über die gebündelte Offline-Erkennung
 *    [VoskSpeechEngine]. Ein Praxistest hat gezeigt, dass die geräteinterne
 *    Android-Systemerkennung (`SpeechRecognizer.checkRecognitionSupport`)
 *    auf realer Hardware "nicht unterstützt" für Ukrainisch/Arabisch
 *    zurückliefert - dieser zunächst naheliegende Weg (siehe frühere
 *    `SystemSpeechEngine`, mittlerweile entfernt) war also kein verlässlicher
 *    Ersatz und wurde durch Vosk abgelöst.
 *  - Für Chinesisch, Japanisch, Koreanisch und Hindi gibt es beide Wege
 *    (ML Kit Basic listet sie laut aktueller Doku): ein geladenes Vosk-Modell
 *    hat Vorrang, weil dieser Pfad am Testgerät erprobt ist - siehe [engineFor].
 */
object SpeechEngine {

    /**
     * ML Kit GenAI Speech Recognition Basic-Modus ist laut Google-Doku "generally
     * available on most Android devices with API level 31 and higher". Unterhalb
     * dieser Schwelle bleibt der Live-Button für die ML-Kit-Sprachen deaktiviert,
     * obwohl minSdk=26 die App selbst dort lauffähig hält. Vosk hat diese
     * Einschränkung nicht (reine Kotlin/JNA-Bibliothek ohne Android-Systemdienst)
     * und läuft bereits ab minSdk.
     */
    private const val MIN_LIVE_SDK_INT = Build.VERSION_CODES.S // API 31

    /** Zeitlimit für eine einzelne `checkStatus()`-Abfrage (siehe [mlKitModelStatus]). */
    private const val STATUS_TIMEOUT_MILLIS = 8_000L

    /**
     * Eigener Scope für das Aufräumen von Recognizern: `stopRecognition()` ist
     * eine suspend fun, [stopAndRelease] wird aber aus nicht-suspend Kontexten
     * (z. B. `DisposableEffect.onDispose`) aufgerufen. SupervisorJob, damit
     * ein fehlgeschlagenes Aufräumen die anderen nicht mitreißt.
     */
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val mlKitAvailableOnDevice: Boolean
        get() = Build.VERSION.SDK_INT >= MIN_LIVE_SDK_INT

    /**
     * Zuständige Live-Engine für [languageCode] auf diesem Gerät:
     *  - [LiveEngine.VOSK], sobald ein Vosk-Modell für die Sprache heruntergeladen
     *    ist (Menü "Sprachpakete") - auch für die zweigleisigen Sprachen, weil
     *    dieser Pfad am Testgerät erprobt ist. Anders als bei ML Kit wird hier
     *    NICHT beim ersten Mikro-Tap spontan nachgeladen, die Modelle sind dafür
     *    zu groß.
     *  - sonst [LiveEngine.MLKIT], wenn der Basic-Modus die Sprache listet und
     *    das Gerät Android 12+ hat (ob das Erkennungsmodell schon geladen ist,
     *    sagt [mlKitModelStatus]; fehlt es, lädt der erste Mikro-Tap nach).
     *  - sonst [LiveEngine.NONE].
     */
    fun engineFor(context: Context, languageCode: String): LiveEngine {
        val language = LanguageCatalog.byCode(languageCode)
        return when {
            VoskSpeechEngine.isModelReady(context, languageCode) -> LiveEngine.VOSK
            language.mlKitLiveSpeech && mlKitAvailableOnDevice -> LiveEngine.MLKIT
            else -> LiveEngine.NONE
        }
    }

    /**
     * Ob für [languageCode] Live-Spracherkennung angeboten werden darf. Steuert
     * in [de.vip.liveuebersetzer.MainActivity] direkt, ob der Live-Button aktiv ist.
     */
    fun isLiveSupported(context: Context, languageCode: String): Boolean =
        engineFor(context, languageCode) != LiveEngine.NONE

    /**
     * Klartext für die Mitarbeiterseite, warum es für [language] gerade keine
     * Sprechtaste gibt - oder `null`, wenn es eine gibt. Vorher verschwand die
     * Kundenseiten-Sprechtaste kommentarlos, und am Schalter war nicht
     * erkennbar, dass nur ein Download fehlt.
     */
    fun unavailableReason(context: Context, language: Language): String? {
        if (isLiveSupported(context, language.code)) return null
        return when {
            VoskSpeechEngine.isSupported(language.code) ->
                "Für ${language.displayName} ist das Sprachmodell noch nicht geladen - " +
                    "deshalb gibt es auf der Kundenseite keine Sprechtaste. " +
                    "Logo antippen → Sprachpakete."
            language.mlKitLiveSpeech ->
                "Live-Erkennung für ${language.displayName} braucht Android 12 oder neuer."
            else -> "Für ${language.displayName} gibt es auf diesem Gerät keine Live-Erkennung."
        }
    }

    /** Erstellt einen [SpeechRecognizer] für die übergebene ML-Kit-Live-Sprache. */
    fun createRecognizer(languageCode: String): SpeechRecognizer {
        val language = LanguageCatalog.byCode(languageCode)
        val localeTag = requireNotNull(language.mlKitSpeechLocaleTag) {
            "ML-Kit-Live-Spracherkennung wird für '$languageCode' nicht unterstützt."
        }
        val options = speechRecognizerOptions {
            locale = Locale.forLanguageTag(localeTag)
            preferredMode = SpeechRecognizerOptions.Mode.MODE_BASIC
        }
        return SpeechRecognition.getClient(options)
    }

    /**
     * Status des ML-Kit-Erkennungsmodells für [languageCode] auf diesem Gerät
     * (Sprachpakete-Menü, Einrichtungs-Assistent). [ModelStatus.NICHT_VERFUEGBAR]
     * auch dann, wenn die Abfrage selbst scheitert - z. B. weil der
     * AICore-Dienst auf dem Gerät fehlt oder noch initialisiert (siehe
     * "Common setup issues" in der Google-Doku).
     */
    suspend fun mlKitModelStatus(languageCode: String): ModelStatus {
        val language = LanguageCatalog.byCode(languageCode)
        if (!language.mlKitLiveSpeech || !mlKitAvailableOnDevice) return ModelStatus.NICHT_VERFUEGBAR
        val recognizer = createRecognizer(languageCode)
        return try {
            // Mit Zeitlimit: Bleibt der Systemdienst hängen, soll das Menü
            // nicht endlos "prüfe …" zeigen. FEHLER statt NICHT_VERFUEGBAR,
            // damit "Laden" einen neuen Versuch erlaubt.
            val status = withTimeoutOrNull(STATUS_TIMEOUT_MILLIS) { recognizer.checkStatus() }
            when (status) {
                null -> ModelStatus.FEHLER
                FeatureStatus.AVAILABLE -> ModelStatus.GELADEN
                FeatureStatus.DOWNLOADABLE -> ModelStatus.FEHLT
                FeatureStatus.DOWNLOADING -> ModelStatus.LAEDT
                else -> ModelStatus.NICHT_VERFUEGBAR
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ModelStatus.NICHT_VERFUEGBAR
        } finally {
            runCatching { recognizer.close() }
        }
    }

    /**
     * Lädt das Spracherkennungsmodell bei Bedarf herunter. `download()` liefert
     * einen `Flow<DownloadStatus>` mit den drei in der Google-Doku beschriebenen
     * Zuständen: `DownloadProgress` (→ [onProgress] mit den bisher geladenen
     * Bytes), `DownloadFailed` (→ Exception mit Klartext) und
     * `DownloadCompleted`. Vorher lief der Flow nur "bis zum Ende" durch - ein
     * fehlgeschlagener Download blieb dabei unbemerkt und endete erst beim
     * Sprechen mit einem kryptischen Fehler.
     */
    suspend fun ensureModelDownloaded(
        recognizer: SpeechRecognizer,
        onProgress: (bytesDownloaded: Long) -> Unit = {},
    ) {
        when (recognizer.checkStatus()) {
            FeatureStatus.AVAILABLE -> return
            FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> {
                var failure: Throwable? = null
                // Typen und Felder per javap gegen genai-common 1.0.0-beta3
                // verifiziert: DownloadStarted, DownloadProgress(totalBytesDownloaded),
                // DownloadFailed(e: GenAiException), DownloadCompleted.
                recognizer.download().collect { status ->
                    when (status) {
                        is DownloadStatus.DownloadProgress -> onProgress(status.totalBytesDownloaded)
                        is DownloadStatus.DownloadFailed -> failure = status.e
                        is DownloadStatus.DownloadStarted, is DownloadStatus.DownloadCompleted -> Unit
                    }
                }
                failure?.let { cause ->
                    throw IllegalStateException(
                        "Download des Erkennungsmodells fehlgeschlagen - Internetverbindung " +
                            "prüfen und erneut versuchen.",
                        cause,
                    )
                }
            }
            // UNAVAILABLE: z. B. fehlendes Offline-Sprachpaket der
            // System-Spracherkennung (Basic-Modus nutzt den Systemdienst).
            // Verständliche Meldung statt kryptischem Laufzeitfehler.
            else -> throw IllegalStateException(
                "Live-Erkennung ist für diese Sprache auf diesem Gerät nicht " +
                    "verfügbar. Bitte im Menü \"Sprachpakete\" laden - hilft das " +
                    "nicht, in den Android-Einstellungen die Offline-Spracheingabe " +
                    "für diese Sprache installieren oder dort das eigene " +
                    "Sprachmodell laden, falls angeboten.",
            )
        }
    }

    /**
     * Lädt das ML-Kit-Erkennungsmodell für [languageCode] vorab herunter (Menü
     * "Sprachpakete"). [onProgress] liefert die bisher geladenen Bytes; eine
     * Gesamtgröße nennt ML Kit nicht.
     */
    suspend fun prepareMlKitModel(languageCode: String, onProgress: (bytesDownloaded: Long) -> Unit = {}) {
        val recognizer = createRecognizer(languageCode)
        try {
            ensureModelDownloaded(recognizer, onProgress)
        } finally {
            runCatching { recognizer.close() }
        }
    }

    /**
     * Startet die Live-Erkennung über das Mikrofon und ruft [onPartial] für
     * vorläufigen und [onFinal] für endgültigen erkannten Text auf. Die
     * Nutzlast steckt im Antworttyp [SpeechRecognizerResponse] und wird über
     * `.text` gelesen (Typnamen gegen die echte Alpha-AAR verifiziert, siehe README).
     */
    fun listen(
        recognizer: SpeechRecognizer,
        scope: CoroutineScope,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (Throwable) -> Unit,
    ): Job {
        val request = speechRecognizerRequest {
            audioSource = AudioSource.fromMic()
        }
        return scope.launch {
            try {
                recognizer.startRecognition(request).collect { response ->
                    when (response) {
                        is SpeechRecognizerResponse.PartialTextResponse -> onPartial(response.text)
                        is SpeechRecognizerResponse.FinalTextResponse -> onFinal(response.text)
                        is SpeechRecognizerResponse.CompletedResponse -> Unit
                        is SpeechRecognizerResponse.ErrorResponse -> onError(response.e)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    /**
     * Stoppt die Erkennung und gibt den Recognizer frei - **in dieser
     * Reihenfolge**. Vorher lief `close()` synchron los, während
     * `stopRecognition()` noch in einer eigenen Coroutine anlief, d. h. der
     * Recognizer konnte geschlossen werden, bevor der Stopp bei ihm ankam.
     */
    fun stopAndRelease(recognizer: SpeechRecognizer) {
        cleanupScope.launch {
            runCatching { recognizer.stopRecognition() }
            runCatching { recognizer.close() }
        }
    }
}
