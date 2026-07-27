package de.vip.liveuebersetzer

import android.content.Context
import android.os.Build
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Welche Live-Erkennungs-Engine für eine Sprache auf diesem Gerät zuständig ist. */
enum class LiveEngine { MLKIT, SYSTEM, NONE }

/**
 * Dünner Wrapper um ML Kit GenAI Speech Recognition
 * (`com.google.mlkit:genai-speech-recognition:1.0.0-alpha1`, Alpha-Status,
 * Paket `com.google.mlkit.genai.speechrecognition`) plus die Weiche
 * [engineFor], die pro Sprache zwischen ML Kit und der
 * Android-Systemerkennung ([SystemSpeechEngine]) entscheidet.
 *
 * Bewusste Entscheidungen (siehe README, bitte nicht ohne Rücksprache ändern):
 *  - Es wird ausschließlich der "Basic"-Modus verwendet. Der "Advanced"-Modus
 *    (Gemini-Nano-Qualität, breitere Sprachabdeckung inkl. Arabisch) läuft
 *    laut Google-Doku (Stand 07/2026) exklusiv auf Pixel-10-Geräten - die App
 *    soll aber auf der gesamten ViP-Gerätefotte lauffähig sein.
 *  - Kein Cloud-Fallback. Sprachen ohne ML-Kit-Live (Ukrainisch, Arabisch)
 *    laufen über die garantiert geräteinterne Systemerkennung, oder gar nicht.
 */
object SpeechEngine {

    /**
     * Beide Live-Engines setzen Android 12 (API 31) voraus: ML Kit GenAI Speech
     * Recognition Basic-Modus ist laut Google-Doku "generally available on most
     * Android devices with API level 31 and higher", und
     * `SpeechRecognizer.createOnDeviceSpeechRecognizer` (der einzige garantiert
     * geräteinterne Weg der Systemerkennung) existiert erst ab API 31. Unterhalb
     * dieser Schwelle bleibt der Live-Button deaktiviert, obwohl minSdk=26 die
     * App selbst dort lauffähig hält.
     */
    private const val MIN_LIVE_SDK_INT = Build.VERSION_CODES.S // API 31

    /**
     * Zuständige Live-Engine für [languageCode] auf diesem Gerät:
     * [LiveEngine.MLKIT] für die 9 ML-Kit-Sprachen, [LiveEngine.SYSTEM] für
     * Sprachen ohne ML-Kit-Abdeckung (Ukrainisch, Arabisch), sofern das Gerät
     * die geräteinterne Systemerkennung anbietet, sonst [LiveEngine.NONE].
     */
    fun engineFor(context: Context, languageCode: String): LiveEngine {
        if (Build.VERSION.SDK_INT < MIN_LIVE_SDK_INT) return LiveEngine.NONE
        val language = LanguageCatalog.byCode(languageCode)
        return when {
            language.mlKitLiveSpeech -> LiveEngine.MLKIT
            SystemSpeechEngine.isAvailable(context) -> LiveEngine.SYSTEM
            else -> LiveEngine.NONE
        }
    }

    /**
     * Ob für [languageCode] Live-Spracherkennung angeboten werden darf. Steuert
     * in [de.vip.liveuebersetzer.MainActivity] direkt, ob der Live-Button aktiv ist.
     */
    fun isLiveSupported(context: Context, languageCode: String): Boolean =
        engineFor(context, languageCode) != LiveEngine.NONE

    /** Erstellt einen [SpeechRecognizer] für die übergebene ML-Kit-Live-Sprache. */
    fun createRecognizer(languageCode: String): SpeechRecognizer {
        val language = LanguageCatalog.byCode(languageCode)
        require(language.mlKitLiveSpeech) {
            "ML-Kit-Live-Spracherkennung wird für '$languageCode' nicht unterstützt."
        }
        val options = speechRecognizerOptions {
            locale = Locale.forLanguageTag(language.speechLocaleTag)
            preferredMode = SpeechRecognizerOptions.Mode.MODE_BASIC
        }
        return SpeechRecognition.getClient(options)
    }

    /** Prüft, ob das Modell bereits verfügbar ist oder erst heruntergeladen werden muss. */
    suspend fun checkFeatureStatus(recognizer: SpeechRecognizer): Int =
        recognizer.checkStatus()

    /**
     * Lädt das Spracherkennungsmodell bei Bedarf herunter. `download()` liefert (laut
     * CI-Diagnose der realen Alpha-AAR) direkt einen `Flow<DownloadStatus>` statt eines
     * callback-basierten Downloads - hier bis zum Abschluss durchlaufen. Der genaue Aufbau
     * von `DownloadStatus` ist nicht verifiziert, daher aktuell ohne Fortschrittsanzeige.
     */
    suspend fun ensureModelDownloaded(recognizer: SpeechRecognizer) {
        when (checkFeatureStatus(recognizer)) {
            FeatureStatus.AVAILABLE -> return
            FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING ->
                recognizer.download().collect { /* Fortschritt aktuell nicht ausgewertet */ }
            // UNAVAILABLE: z. B. fehlendes Offline-Sprachpaket der
            // System-Spracherkennung (Basic-Modus nutzt den Systemdienst).
            // Verständliche Meldung statt kryptischem Laufzeitfehler.
            else -> throw IllegalStateException(
                "Live-Erkennung ist für diese Sprache auf diesem Gerät nicht " +
                    "verfügbar. Bitte im Menü \"Sprachpakete\" laden - hilft das " +
                    "nicht, in den Android-Einstellungen die Offline-Spracheingabe " +
                    "für diese Sprache installieren.",
            )
        }
    }

    /**
     * Lädt das Live-Erkennungsmodell für [languageCode] vorab herunter
     * (Menü "Sprachpakete") - je nach zuständiger Engine über ML Kit oder die
     * Systemerkennung. Für Sprachen ganz ohne Live-Unterstützung ein No-Op.
     */
    suspend fun prepareModel(context: Context, languageCode: String) {
        when (engineFor(context, languageCode)) {
            LiveEngine.MLKIT -> {
                val recognizer = createRecognizer(languageCode)
                try {
                    ensureModelDownloaded(recognizer)
                } finally {
                    recognizer.close()
                }
            }
            LiveEngine.SYSTEM -> SystemSpeechEngine.triggerModelDownload(
                context,
                LanguageCatalog.byCode(languageCode).speechLocaleTag,
            )
            LiveEngine.NONE -> Unit
        }
    }

    /**
     * Startet die Live-Erkennung über das Mikrofon und ruft [onPartial] für
     * vorläufigen und [onFinal] für endgültigen erkannten Text auf. Ersetzt den
     * früheren Platzhalter `response.toString()`: die eigentliche Nutzlast steckt
     * im Antworttyp [SpeechRecognizerResponse] und wird über `.text` gelesen.
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
     * `stopRecognition()` ist eine suspend fun; da [stopListening] aus nicht-suspend
     * Kontexten (z. B. `DisposableEffect.onDispose`) aufgerufen wird, läuft der Aufruf
     * in einem eigenen, von der Compose-Lifecycle unabhängigen Scope.
     */
    fun stopListening(recognizer: SpeechRecognizer) {
        CoroutineScope(Dispatchers.Default).launch {
            runCatching { recognizer.stopRecognition() }
        }
    }

    fun release(recognizer: SpeechRecognizer) {
        recognizer.close()
    }
}
