package de.vip.liveuebersetzer

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService

/** Laufende Vosk-Aufnahme; [cancel] bricht sie ab (mehrfacher Aufruf ist unschädlich). */
class VoskSpeechSession internal constructor(private val teardown: () -> Unit) {
    fun cancel() = teardown()
}

/**
 * Dritte Live-Erkennungs-Engine: [Vosk](https://alphacephei.com/vosk)
 * (Apache-2.0, `com.alphacephei:vosk-android`), direkt in die App gebündelt
 * statt über einen Android-Systemdienst zu laufen. Zuständig für Ukrainisch
 * und Arabisch: Ein Praxistest hat gezeigt, dass die geräteinterne
 * Android-Systemerkennung (`SpeechRecognizer.checkRecognitionSupport`) für
 * diese beiden Sprachen "nicht unterstützt" zurückliefert - der zunächst
 * naheliegende, garantiert geräteinterne Systemweg war also kein
 * verlässlicher Ersatz für die fehlende ML-Kit-Abdeckung.
 *
 * Bewusste Entscheidungen:
 *  - Modelle werden NICHT in der APK gebündelt (würde Debug-Builds unhandlich
 *    groß machen), sondern bei Bedarf im Menü "Sprachpakete" heruntergeladen
 *    und entpackt - derselbe "einmalig mit Internet vorbereiten"-Ablauf wie
 *    bei den ML-Kit-Modellen.
 *  - Deshalb: Live-Erkennung für uk/ar wird erst angeboten, wenn das Modell
 *    tatsächlich heruntergeladen UND entpackt ist ([isModelReady]) - anders
 *    als bei ML Kit wird hier NICHT beim ersten Mikro-Tap spontan
 *    nachgeladen, dafür sind die Modelle (zig MB) zu groß.
 *  - Bleibt trotz Download vollständig on-device: Die Modelle werden
 *    einmalig heruntergeladen, die eigentliche Spracherkennung läuft danach
 *    komplett offline auf dem Gerät (Kaldi-Engine über JNA/native Bibliothek).
 */
object VoskSpeechEngine {

    private const val SAMPLE_RATE = 16000f

    /**
     * Download-URLs der "small"-Offline-Modelle (kleinere, für den mobilen
     * Einsatz gedachte Variante statt der großen Referenzmodelle). Per HTTP-
     * HEAD gegen die echten, von alphacephei.com ausgelieferten Dateien
     * verifiziert (siehe README, Abschnitt "Vosk-Modelle") - die Sandbox, in
     * der dieser Code entstand, konnte alphacephei.com selbst nicht direkt
     * erreichen, daher lief diese Prüfung über einen temporären Schritt im
     * CI-Workflow.
     */
    private val modelUrls = mapOf(
        "uk" to "https://alphacephei.com/vosk/models/vosk-model-small-uk-v3-small.zip", // ~137 MB
        "ar" to "https://alphacephei.com/vosk/models/vosk-model-small-ar-0.3.zip", // ~100 MB
    )

    private val loadedModels = HashMap<String, Model>()

    /** Ob für [languageCode] überhaupt ein Vosk-Modell hinterlegt ist. */
    fun isSupported(languageCode: String): Boolean = modelUrls.containsKey(languageCode)

    private fun modelDir(context: Context, languageCode: String): File =
        File(context.filesDir, "vosk-models/$languageCode")

    private fun readyMarker(context: Context, languageCode: String): File =
        File(modelDir(context, languageCode), ".vosk-ready")

    /** Ob das Modell für [languageCode] bereits heruntergeladen und entpackt ist. */
    fun isModelReady(context: Context, languageCode: String): Boolean =
        readyMarker(context, languageCode).exists()

    /**
     * Lädt das Vosk-Modell für [languageCode] herunter und entpackt es (Menü
     * "Sprachpakete"). [onProgress] liefert die bisher geladenen Bytes und -
     * falls vom Server bekannt - die Gesamtgröße. No-Op, wenn das Modell
     * bereits bereitsteht. Läuft auf [Dispatchers.IO].
     */
    suspend fun downloadAndUnpack(
        context: Context,
        languageCode: String,
        onProgress: (downloaded: Long, total: Long?) -> Unit = { _, _ -> },
    ) {
        if (isModelReady(context, languageCode)) return
        val url = modelUrls[languageCode]
            ?: throw IllegalArgumentException("Kein Vosk-Modell für '$languageCode' hinterlegt.")
        withContext(Dispatchers.IO) {
            val target = modelDir(context, languageCode)
            target.deleteRecursively()
            target.mkdirs()
            try {
                unpackFrom(url, target, onProgress)
                readyMarker(context, languageCode).writeText("ok")
            } catch (e: Exception) {
                target.deleteRecursively()
                throw e
            }
        }
    }

    /**
     * Lädt [url] herunter und entpackt den ZIP-Stream direkt (ohne Zwischendatei)
     * nach [target]. Vosk-Modell-ZIPs enthalten einen gemeinsamen Wurzelordner
     * (z. B. `vosk-model-.../am/final.mdl`) - der wird beim Entpacken entfernt,
     * damit [target] direkt die vom [org.vosk.Model]-Konstruktor erwartete
     * Modell-Struktur enthält.
     */
    private fun unpackFrom(url: String, target: File, onProgress: (Long, Long?) -> Unit) {
        var currentUrl = url
        var connection: HttpURLConnection
        var redirects = 0
        while (true) {
            connection = (URI(currentUrl).toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
            }
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                    ?: throw IOException("Umleitung ohne Ziel-URL ($currentUrl).")
                connection.disconnect()
                redirects++
                if (redirects > 5) throw IOException("Zu viele Umleitungen beim Modell-Download.")
                currentUrl = location
                continue
            }
            if (code != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                throw IOException("Modell-Download fehlgeschlagen (HTTP $code).")
            }
            break
        }
        val total = connection.contentLengthLong.takeIf { it > 0 }
        var downloaded = 0L
        connection.inputStream.use { rawInput ->
            ZipInputStream(rawInput).use { zip ->
                var rootPrefix: String? = null
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val firstSlash = name.indexOf('/')
                    if (rootPrefix == null && firstSlash > 0) rootPrefix = name.substring(0, firstSlash + 1)
                    val relative = rootPrefix?.let { name.removePrefix(it) } ?: name
                    if (relative.isNotBlank()) {
                        val outFile = File(target, relative)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { out ->
                                val buffer = ByteArray(64 * 1024)
                                var read = zip.read(buffer)
                                while (read >= 0) {
                                    out.write(buffer, 0, read)
                                    downloaded += read
                                    onProgress(downloaded, total)
                                    read = zip.read(buffer)
                                }
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
    }

    /** Lädt das Modell (einmalig pro Sprache, danach im Speicher gehalten). */
    private suspend fun loadModel(context: Context, languageCode: String): Model =
        loadedModels[languageCode] ?: withContext(Dispatchers.IO) {
            loadedModels.getOrPut(languageCode) {
                Model(modelDir(context, languageCode).absolutePath)
            }
        }

    /**
     * Startet eine Aufnahme über das Mikrofon. Tap-to-Talk: Der erste über
     * `onResult` gemeldete abgeschlossene Satz gilt als [onFinal] und beendet
     * die Aufnahme sofort - dieselbe Semantik wie bei [SpeechEngine.listen].
     */
    suspend fun listen(
        context: Context,
        languageCode: String,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit,
    ): VoskSpeechSession {
        val model = loadModel(context, languageCode)
        val recognizer = Recognizer(model, SAMPLE_RATE)
        val speechService = SpeechService(recognizer, SAMPLE_RATE)
        // Eigener Name noetig: kollidiert sonst mit RecognitionListener.onError(Exception).
        val reportError = onError

        fun teardown() {
            runCatching { speechService.cancel() }
            runCatching { speechService.shutdown() }
            runCatching { recognizer.close() }
        }

        speechService.startListening(object : org.vosk.android.RecognitionListener {
            override fun onPartialResult(hypothesis: String) {
                extractText(hypothesis, "partial")?.let { if (it.isNotBlank()) onPartial(it) }
            }

            override fun onResult(hypothesis: String) {
                val text = extractText(hypothesis, "text")
                teardown()
                if (text.isNullOrBlank()) {
                    reportError("Nichts erkannt - bitte erneut versuchen.")
                } else {
                    onFinal(text)
                }
            }

            override fun onFinalResult(hypothesis: String) = Unit

            override fun onError(exception: Exception) {
                teardown()
                reportError(exception.message ?: "Fehler bei der Vosk-Spracherkennung.")
            }

            override fun onTimeout() {
                teardown()
                reportError("Nichts erkannt - bitte erneut versuchen.")
            }
        })
        return VoskSpeechSession(::teardown)
    }

    private fun extractText(hypothesis: String, key: String): String? =
        runCatching { JSONObject(hypothesis).optString(key) }.getOrNull()

    /** Gibt alle geladenen Modelle frei (z. B. beim Verlassen des Screens). */
    fun closeAll() {
        loadedModels.values.forEach { runCatching { it.close() } }
        loadedModels.clear()
    }
}
