package de.vip.liveuebersetzer

import android.content.Context
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/** Laufende Vosk-Aufnahme; [cancel] bricht sie ab (mehrfacher Aufruf ist unschädlich). */
class VoskSpeechSession internal constructor(private val teardown: () -> Unit) {
    fun cancel() = teardown()
}

/**
 * Zweite Live-Erkennungs-Engine: [Vosk](https://alphacephei.com/vosk)
 * (Apache-2.0, `com.alphacephei:vosk-android`), direkt in die App gebündelt
 * statt über einen Android-Systemdienst zu laufen. Einziger Weg für
 * Ukrainisch, Arabisch und Persisch (ML Kit Basic listet sie nicht; ein
 * Praxistest zeigte, dass auch die geräteinterne Android-Systemerkennung
 * Ukrainisch/Arabisch "nicht unterstützt" meldet) und erprobter Vorzugsweg
 * für Chinesisch, Japanisch, Koreanisch und Hindi - siehe
 * [SpeechEngine.engineFor].
 *
 * Bewusste Entscheidungen:
 *  - Modelle werden NICHT in der APK gebündelt (würde Debug-Builds unhandlich
 *    groß machen), sondern bei Bedarf im Menü "Sprachpakete" heruntergeladen
 *    und entpackt - derselbe "einmalig mit Internet vorbereiten"-Ablauf wie
 *    bei den ML-Kit-Modellen.
 *  - Deshalb: Live-Erkennung über Vosk wird erst angeboten, wenn das Modell
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
     * Längste Aufnahme ohne erkannten Satz. Vosk meldet nach wenigen Sekunden
     * Stille ein *leeres* Ergebnis - das wird in [listen] bewusst ignoriert
     * (sonst stand nach 5 s "Nichts erkannt", bevor der Kunde überhaupt
     * angefangen hatte). Damit die Aufnahme aber nicht endlos offen bleibt,
     * zieht Vosks eigener Timeout: danach kommt `onTimeout`.
     */
    private const val LISTEN_TIMEOUT_MILLIS = 30_000

    /**
     * Download-URLs der Offline-Modelle. Für Ukrainisch die "small"-Variante
     * (kleinere, für den mobilen Einsatz gedachte Modellgröße). Für Arabisch
     * bewusst NICHT die "small"-Variante: Ein Praxistest zeigte, dass
     * `vosk-model-small-ar-0.3` zwar aufnimmt, aber praktisch nichts brauchbar
     * erkennt (deutlich schwächeres, älteres Modell) - stattdessen das
     * MGB2-Modell, Vosks etablierteres/genaueres Arabisch-Modell (trainiert auf
     * dem MGB-2-Corpus arabischer Rundfunknachrichten), trotz des dafür
     * deutlich größeren Downloads. Alle URLs per HTTP-HEAD gegen die echten,
     * von alphacephei.com ausgelieferten Dateien verifiziert (siehe README,
     * Abschnitt "Vosk-Modelle").
     */
    private val modelUrls = mapOf(
        "uk" to "https://alphacephei.com/vosk/models/vosk-model-small-uk-v3-small.zip",
        "ar" to "https://alphacephei.com/vosk/models/vosk-model-ar-mgb2-0.4.zip",
        "fa" to "https://alphacephei.com/vosk/models/vosk-model-small-fa-0.5.zip",
        // Auf Wunsch aus dem Praxistest ergänzt. Die "small"-Modelle sind hier
        // erfreulich kompakt - alle zusammen bleiben unter dem einen
        // arabischen Modell. Vosk verwendet für Chinesisch das Kürzel "cn",
        // die App bleibt beim ISO-Code "zh".
        "zh" to "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip",
        "ja" to "https://alphacephei.com/vosk/models/vosk-model-small-ja-0.22.zip",
        "ko" to "https://alphacephei.com/vosk/models/vosk-model-small-ko-0.22.zip",
        "hi" to "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip",
    )

    /** Ungefähre Download-Größe in MB (für den Hinweis im Sprachpakete-Menü). */
    private val modelSizesMb = mapOf(
        "uk" to 137, "ar" to 318, "fa" to 59,
        "zh" to 41, "ja" to 47, "ko" to 82, "hi" to 42,
    )

    private val loadedModels = HashMap<String, Model>()

    /**
     * Gemerktes Ergebnis von [isModelReady] pro Sprache. Die Prüfung liest
     * eine Marker-Datei - und wird aus der Compose-UI bei jeder Recomposition
     * aufgerufen (Sprechtasten-Sichtbarkeit), also auch bei jedem Zwischen-
     * ergebnis der Erkennung. Datei-I/O auf dem Main-Thread in dieser
     * Frequenz ist unnötig; nach einem Download wird der Eintrag verworfen.
     */
    private val readyCache = ConcurrentHashMap<String, Boolean>()

    /** Ob für [languageCode] überhaupt ein Vosk-Modell hinterlegt ist. */
    fun isSupported(languageCode: String): Boolean = modelUrls.containsKey(languageCode)

    /** Ungefähre Download-Größe des Modells für [languageCode] in MB, falls bekannt. */
    fun modelSizeMb(languageCode: String): Int? = modelSizesMb[languageCode]

    private fun modelDir(context: Context, languageCode: String): File =
        File(context.filesDir, "vosk-models/$languageCode")

    private fun readyMarker(context: Context, languageCode: String): File =
        File(modelDir(context, languageCode), ".vosk-ready")

    /**
     * Ob das Modell für [languageCode] bereits heruntergeladen und entpackt
     * ist. Die Marker-Datei enthält die URL, mit der sie entpackt wurde - ändert
     * sich [modelUrls] (z. B. Wechsel auf ein anderes Modell wie beim
     * Arabisch-Wechsel von "small" auf MGB2 geschehen), gilt ein bereits
     * heruntergeladenes altes Modell automatisch als veraltet und wird beim
     * nächsten "Laden" ersetzt, statt fälschlich als bereit zu gelten.
     */
    fun isModelReady(context: Context, languageCode: String): Boolean {
        val expectedUrl = modelUrls[languageCode] ?: return false
        readyCache[languageCode]?.let { return it }
        val marker = readyMarker(context, languageCode)
        val ready = marker.exists() && runCatching { marker.readText() }.getOrNull() == expectedUrl
        readyCache[languageCode] = ready
        return ready
    }

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
            readyCache.remove(languageCode)
            target.deleteRecursively()
            target.mkdirs()
            try {
                unpackFrom(url, target, onProgress)
                readyMarker(context, languageCode).writeText(url)
                readyCache[languageCode] = true
                // Falls zuvor schon (ein jetzt veraltetes) Modell für diese
                // Sprache im Speicher gehalten wurde, muss das verworfen
                // werden - sonst würde listen() weiter das alte benutzen.
                synchronized(loadedModels) { loadedModels.remove(languageCode) }
                    ?.let { runCatching { it.close() } }
            } catch (e: Exception) {
                // Auch bei Abbruch (CancellationException): Ein halb entpacktes
                // Modell darf nie als bereit gelten.
                target.deleteRecursively()
                readyCache[languageCode] = false
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
     *
     * Der Fortschritt zählt die **heruntergeladenen** (komprimierten) Bytes,
     * weil nur die mit `Content-Length` vergleichbar sind. Vorher wurden die
     * entpackten Bytes gezählt - gegen die komprimierte Gesamtgröße gerechnet
     * stand dann "143 %" in der Anzeige.
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
        val counting = CountingInputStream(connection.inputStream) { downloaded -> onProgress(downloaded, total) }
        counting.use { rawInput ->
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
                        // Zip-Slip-Schutz: Ein Eintrag wie "../../datei" darf
                        // nie außerhalb des Zielordners landen. Die Quelle ist
                        // zwar fest verdrahtet (alphacephei.com, HTTPS), aber
                        // entpackter Fremdinhalt wird grundsätzlich geprüft.
                        if (!outFile.canonicalPath.startsWith(target.canonicalPath + File.separator)) {
                            throw IOException("Ungültiger Pfad im Modell-Archiv: $name")
                        }
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { out -> zip.copyTo(out, 64 * 1024) }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
    }

    /** Zählt die aus dem Netz gelesenen Bytes und meldet sie an [onRead]. */
    private class CountingInputStream(
        input: InputStream,
        private val onRead: (Long) -> Unit,
    ) : FilterInputStream(input) {
        private var count = 0L

        override fun read(): Int = super.read().also { if (it >= 0) report(1) }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            super.read(b, off, len).also { if (it > 0) report(it.toLong()) }

        private fun report(bytes: Long) {
            count += bytes
            onRead(count)
        }
    }

    /**
     * Lädt das Modell (einmalig pro Sprache, danach im Speicher gehalten).
     * Der `synchronized`-Block verhindert, dass zwei gleichzeitige Aufrufe
     * dasselbe Modell doppelt laden bzw. die HashMap unsynchronisiert
     * beschreiben.
     */
    private suspend fun loadModel(context: Context, languageCode: String): Model =
        withContext(Dispatchers.IO) {
            synchronized(loadedModels) {
                loadedModels.getOrPut(languageCode) {
                    Model(modelDir(context, languageCode).absolutePath)
                }
            }
        }

    /**
     * Startet eine Aufnahme über das Mikrofon. Tap-to-Talk: Der erste über
     * `onResult` gemeldete abgeschlossene Satz gilt als [onFinal] und beendet
     * die Aufnahme sofort - dieselbe Semantik wie bei [SpeechEngine.listen].
     * Leere Ergebnisse (Vosk meldet nach ein paar Sekunden Stille ein Ergebnis
     * ohne Text) werden ignoriert, die Aufnahme läuft weiter - bis zum ersten
     * Satz oder bis [LISTEN_TIMEOUT_MILLIS] abgelaufen ist.
     *
     * Kann werfen (z. B. `IOException`, wenn das Mikrofon nicht initialisierbar
     * oder das entpackte Modell beschädigt ist) - der Aufrufer muss das fangen.
     * Alle Callbacks kommen auf dem Main-Thread an (Vosks `SpeechService`
     * postet sie dorthin).
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
        // Eigener Name nötig: kollidiert sonst mit RecognitionListener.onError(Exception).
        val reportError = onError

        // Idempotent: teardown kann doppelt angestoßen werden (Stopp-Tipp des
        // Nutzers zeitgleich mit onResult/onError des Erkennungs-Threads).
        // recognizer.close() gibt den nativen Kaldi-Recognizer frei - ein
        // zweites free auf denselben Zeiger wäre ein nativer Absturz.
        // SpeechService.cancel() wartet auf das Ende des Erkennungs-Threads
        // (join), erst danach ist close() auf dem Recognizer sicher.
        val closed = AtomicBoolean(false)
        fun teardown() {
            if (!closed.compareAndSet(false, true)) return
            runCatching { speechService.cancel() }
            runCatching { speechService.shutdown() }
            runCatching { recognizer.close() }
        }

        val listener = object : RecognitionListener {
            override fun onPartialResult(hypothesis: String) {
                if (closed.get()) return
                extractText(hypothesis, "partial")?.let { if (it.isNotBlank()) onPartial(it) }
            }

            override fun onResult(hypothesis: String) {
                if (closed.get()) return
                val text = extractText(hypothesis, "text")
                // Leeres Ergebnis = nur Stille bis hierhin. Weiterhören statt
                // abzubrechen; der Timeout unten begrenzt die Aufnahme.
                if (text.isNullOrBlank()) return
                teardown()
                onFinal(text)
            }

            override fun onFinalResult(hypothesis: String) = Unit

            override fun onError(exception: Exception) {
                if (closed.get()) return
                teardown()
                reportError(exception.message ?: "Fehler bei der Vosk-Spracherkennung.")
            }

            override fun onTimeout() {
                if (closed.get()) return
                teardown()
                reportError("Nichts erkannt - bitte erneut versuchen.")
            }
        }

        val started = speechService.startListening(listener, LISTEN_TIMEOUT_MILLIS)
        if (!started) {
            // Kein Callback wird je kommen - sofort aufräumen und melden,
            // sonst bliebe die App im "hört zu"-Zustand hängen.
            teardown()
            reportError("Aufnahme konnte nicht gestartet werden - bitte erneut versuchen.")
        }
        return VoskSpeechSession(::teardown)
    }

    private fun extractText(hypothesis: String, key: String): String? =
        runCatching { JSONObject(hypothesis).optString(key) }.getOrNull()

    /** Gibt alle geladenen Modelle frei (z. B. beim Verlassen des Screens). */
    fun closeAll() {
        synchronized(loadedModels) {
            loadedModels.values.forEach { runCatching { it.close() } }
            loadedModels.clear()
        }
    }
}
