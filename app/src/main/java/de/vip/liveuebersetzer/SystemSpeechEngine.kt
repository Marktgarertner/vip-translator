package de.vip.liveuebersetzer

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat

/**
 * Lage der Systemerkennung für eine konkrete Sprache auf diesem Gerät -
 * Grundlage der Statuszeile "Live-Erkennung" im Menü "Sprachpakete", damit
 * ein "funktioniert nicht" eine benennbare Ursache bekommt.
 */
enum class SystemLiveStatus {
    /** Android < 12: keine garantiert geräteinterne Systemerkennung. */
    ANDROID_ZU_ALT,

    /** Gerät bietet keine geräteinterne Systemerkennung an. */
    KEIN_SYSTEMDIENST,

    /** Android 12: Dienst vorhanden, Sprachstatus erst ab Android 13 abfragbar. */
    UNBEKANNT,

    /** Offline-Sprachpaket installiert - Live-Erkennung sollte funktionieren. */
    PAKET_INSTALLIERT,

    /** Offline-Sprachpaket wird gerade heruntergeladen. */
    PAKET_LAEDT,

    /** Sprache wird unterstützt, Paket muss aber noch geladen werden. */
    PAKET_LADBAR,

    /** Die Systemerkennung dieses Geräts unterstützt die Sprache nicht. */
    NICHT_UNTERSTUETZT,
}

/**
 * Laufende Aufnahme der Android-Systemerkennung. Die Erkennung stoppt nach dem
 * gesprochenen Satz von selbst (passt zum Tap-to-Talk-Konzept der App);
 * [cancel] bricht vorzeitig ab und verwirft das bisher Erkannte.
 */
class SystemSpeechSession internal constructor(private val recognizer: SpeechRecognizer) {
    fun cancel() {
        runCatching { recognizer.cancel() }
        runCatching { recognizer.destroy() }
    }
}

/**
 * Zweite Live-Erkennungs-Engine neben [SpeechEngine]: die Spracherkennung des
 * Android-Systems (`android.speech.SpeechRecognizer`). Sie deckt - abhängig
 * von den auf dem Gerät installierten Offline-Sprachpaketen - auch Sprachen
 * ab, die der ML-Kit-Basic-Modus nicht kennt (Ukrainisch, Arabisch).
 *
 * Datenschutz-Entscheidung (hartes Requirement, siehe README - bitte nicht
 * ohne Rücksprache ändern): Es wird ausschließlich
 * `SpeechRecognizer.createOnDeviceSpeechRecognizer()` (Android 12+/API 31)
 * verwendet, denn nur dieser Weg **garantiert** eine geräteinterne
 * Verarbeitung. Der ältere Weg (`createSpeechRecognizer` +
 * `EXTRA_PREFER_OFFLINE`) würde die App auch unter Android < 12 lauffähig
 * machen, "bevorzugt" offline aber nur - Audio könnte dort trotzdem an einen
 * Cloud-Dienst gehen. Deshalb bewusst kein Einsatz unterhalb von API 31.
 */
object SystemSpeechEngine {

    /** Ob dieses Gerät die garantiert geräteinterne Systemerkennung anbietet. */
    fun isAvailable(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    /**
     * Startet eine Aufnahme über das Mikrofon (muss vom Main-Thread aufgerufen
     * werden - Vorgabe von [SpeechRecognizer]). [onPartial] liefert vorläufigen,
     * [onFinal] den endgültigen Text; danach ist die Session beendet. [onError]
     * liefert eine bereits nutzerverständliche deutsche Meldung.
     */
    fun listen(
        context: Context,
        languageTag: String,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit,
    ): SystemSpeechSession {
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) onPartial(text)
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                runCatching { recognizer.destroy() }
                if (text.isNullOrBlank()) {
                    onError("Nichts erkannt - bitte erneut versuchen.")
                } else {
                    onFinal(text)
                }
            }

            override fun onError(error: Int) {
                runCatching { recognizer.destroy() }
                onError(describeError(error))
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer.startListening(recognizerIntent(languageTag))
        return SystemSpeechSession(recognizer)
    }

    /**
     * Fragt asynchron ab, wie es auf diesem Gerät um die Systemerkennung für
     * [languageTag] steht. Die eigentliche Abfrage
     * (`SpeechRecognizer.checkRecognitionSupport`) existiert erst ab
     * Android 13 (API 33) - darunter wird bestmöglich geantwortet.
     * [onResult] kommt auf dem Main-Thread.
     */
    fun checkSupport(context: Context, languageTag: String, onResult: (SystemLiveStatus) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            onResult(SystemLiveStatus.ANDROID_ZU_ALT)
            return
        }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            onResult(SystemLiveStatus.KEIN_SYSTEMDIENST)
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onResult(SystemLiveStatus.UNBEKANNT)
            return
        }
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        val language = languageTag.substringBefore('-')
        fun List<String>.containsLanguage(): Boolean =
            any { it.substringBefore('-').equals(language, ignoreCase = true) }
        runCatching {
            recognizer.checkRecognitionSupport(
                recognizerIntent(languageTag),
                ContextCompat.getMainExecutor(context),
                object : RecognitionSupportCallback {
                    override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                        runCatching { recognizer.destroy() }
                        onResult(
                            when {
                                recognitionSupport.installedOnDeviceLanguages.containsLanguage() ->
                                    SystemLiveStatus.PAKET_INSTALLIERT
                                recognitionSupport.pendingOnDeviceLanguages.containsLanguage() ->
                                    SystemLiveStatus.PAKET_LAEDT
                                recognitionSupport.supportedOnDeviceLanguages.containsLanguage() ->
                                    SystemLiveStatus.PAKET_LADBAR
                                else -> SystemLiveStatus.NICHT_UNTERSTUETZT
                            },
                        )
                    }

                    override fun onError(error: Int) {
                        runCatching { recognizer.destroy() }
                        onResult(SystemLiveStatus.UNBEKANNT)
                    }
                },
            )
        }.onFailure {
            runCatching { recognizer.destroy() }
            onResult(SystemLiveStatus.UNBEKANNT)
        }
    }

    /**
     * Stößt den Download des Offline-Sprachpakets der Systemerkennung an
     * (Menü "Sprachpakete"). Die API dafür existiert erst ab Android 13
     * (API 33); darunter bleibt nur der manuelle Weg über die
     * Android-Einstellungen ("Offline-Spracheingabe"). Der Download selbst
     * läuft asynchron im Systemdienst weiter.
     */
    fun triggerModelDownload(context: Context, languageTag: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (!isAvailable(context)) return
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        runCatching { recognizer.triggerModelDownload(recognizerIntent(languageTag)) }
        runCatching { recognizer.destroy() }
    }

    private fun recognizerIntent(languageTag: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

    private fun describeError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        -> "Nichts erkannt - bitte erneut versuchen."
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
        -> "Für diese Sprache ist kein Offline-Sprachpaket der Android-" +
            "Spracherkennung installiert. Im Menü \"Sprachpakete\" laden oder " +
            "in den Android-Einstellungen die Offline-Spracheingabe installieren."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Ohne Mikrofonzugriff ist der Live-Modus nicht möglich."
        else -> "Spracherkennung fehlgeschlagen (Fehlercode $error)."
    }
}
