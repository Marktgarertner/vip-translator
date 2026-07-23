package de.vip.liveuebersetzer

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

/**
 * Dünner Wrapper um ML Kit GenAI Speech Recognition
 * (`com.google.mlkit:genai-speech-recognition:1.0.0-alpha1`, Alpha-Status,
 * Paket `com.google.mlkit.genai.speechrecognition`).
 *
 * Bewusste Entscheidungen (siehe README, bitte nicht ohne Rücksprache ändern):
 *  - Es wird ausschließlich der "Basic"-Modus verwendet. Der "Advanced"-Modus
 *    (Gemini-Nano-Qualität, breitere Sprachabdeckung inkl. Arabisch) läuft
 *    laut Google-Doku (Stand 07/2026) exklusiv auf Pixel-10-Geräten - die App
 *    soll aber auf der gesamten ViP-Gerätefotte lauffähig sein.
 *  - Kein Cloud-Fallback für Sprachen ohne Live-Unterstützung (Ukrainisch,
 *    Arabisch). Diese bleiben auf den getippten Modus beschränkt, siehe
 *    [LanguageCatalog].
 */
object SpeechEngine {

    /**
     * ML Kit GenAI Speech Recognition Basic-Modus ist laut Google-Doku "generally
     * available on most Android devices with API level 31 and higher". Unterhalb
     * dieser Schwelle bleibt der Live-Button deaktiviert, obwohl minSdk=26 die App
     * selbst dort lauffähig hält (getippter Modus funktioniert überall).
     */
    private const val MIN_LIVE_SDK_INT = Build.VERSION_CODES.S // API 31

    /**
     * Ob für [languageCode] Live-Spracherkennung angeboten werden darf. Steuert
     * in [de.vip.liveuebersetzer.MainActivity] direkt, ob der Live-Button aktiv ist.
     */
    fun isLiveSupported(languageCode: String): Boolean {
        if (Build.VERSION.SDK_INT < MIN_LIVE_SDK_INT) return false
        return LanguageCatalog.byCode(languageCode).liveSpeechSupported
    }

    /** Erstellt einen [SpeechRecognizer] für die übergebene, live-fähige Sprache. */
    fun createRecognizer(languageCode: String): SpeechRecognizer {
        val language = LanguageCatalog.byCode(languageCode)
        val localeTag = requireNotNull(language.speechLocaleTag) {
            "Live-Spracherkennung wird für '$languageCode' nicht unterstützt."
        }
        val options = speechRecognizerOptions {
            locale = Locale.forLanguageTag(localeTag)
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
        if (checkFeatureStatus(recognizer) != FeatureStatus.DOWNLOADABLE) return
        recognizer.download().collect { /* DownloadStatus: Fortschritt aktuell nicht ausgewertet */ }
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
