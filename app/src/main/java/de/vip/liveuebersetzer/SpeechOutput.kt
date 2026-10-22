package de.vip.liveuebersetzer

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Sprachausgabe der Übersetzungen über die systemeigene Android-TTS-Engine
 * (`android.speech.tts`). Das ist ein lokaler Systemdienst - aus der App
 * heraus wird keine Dritt-Cloud-API aufgerufen (siehe README, Abschnitt
 * "Datenschutz").
 *
 * Verbesserungen gegenüber der Standard-Nutzung:
 *  - Pro Sprache wird automatisch die **beste installierte Offline-Stimme**
 *    gewählt (höchste Qualitätsstufe, keine Netzverbindung nötig) statt der
 *    Engine-Standardstimme. Netz-Stimmen werden bewusst ignoriert - erstens
 *    wegen des Offline-Anspruchs, zweitens damit die Ausgabe am Schalter
 *    nicht von der Verbindung abhängt.
 *  - Stimmen, deren Sprachdaten die Engine zwar kennt, aber noch nicht
 *    heruntergeladen hat (Feature `notInstalled`), zählen nicht als
 *    vorhanden - Google TTS listet solche Stimmen mit, sie würden aber beim
 *    Sprechen stumm bleiben oder zur Netz-Stimme greifen.
 *  - Leicht reduziertes Sprechtempo ([SPEECH_RATE]) - am Schalter
 *    verständlicher, gerade für Zuhörer, die die Zielsprache nur teilweise
 *    beherrschen.
 *  - [openTtsSettings] führt direkt in die Android-Sprachausgabe-Einstellungen,
 *    wenn für eine Sprache keine Stimme installiert ist.
 */
class SpeechOutput(context: Context) {

    /** Stimmen-Lage für eine Sprache auf diesem Gerät (Menü "Sprachpakete"). */
    enum class VoiceStatus {
        /** TTS-Engine initialisiert noch. */
        NICHT_BEREIT,

        /** Offline-Stimme vorhanden - Ausgabe funktioniert netzunabhängig. */
        OFFLINE_BEREIT,

        /**
         * Nur Netz-Stimmen installiert (oder Offline-Stimmen, deren Daten noch
         * nicht geladen sind). Die App nutzt Netz-Stimmen bewusst nicht
         * (der zu sprechende Text würde das Gerät verlassen - Datenschutz),
         * die Ausgabe bleibt stumm, bis eine Offline-Stimme installiert ist.
         */
        NUR_ONLINE,

        /** Keine Stimme für diese Sprache installiert. */
        FEHLT,
    }

    @Volatile
    private var ready = false
    private var rateApplied = false

    /**
     * Ob die TTS-Engine ihre asynchrone Initialisierung abgeschlossen hat.
     * Direkt nach dem App-Start ist sie kurz `false` - Statusabfragen sollten
     * darauf warten ([awaitReady]), statt fälschlich "keine Stimme" zu melden.
     */
    val isReady: Boolean get() = ready

    /** Beste Offline-Stimme je Sprachcode, einmal ermittelt und gemerkt. */
    private val voiceCache = HashMap<String, Voice>()

    private val tts = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }

    /**
     * Wartet (höchstens [timeoutMillis]), bis die Engine initialisiert ist.
     * Ohne diese Wartezeit meldeten Einrichtungs-Assistent und
     * Sprachpakete-Menü direkt nach dem App-Start für alle Sprachen
     * "Stimme fehlt" bzw. blieben dauerhaft bei "wird vorbereitet".
     */
    suspend fun awaitReady(timeoutMillis: Long = 3_000): Boolean {
        withTimeoutOrNull(timeoutMillis) {
            while (!ready) delay(100)
        }
        return ready
    }

    /**
     * Spricht [text] in [language]. Liefert `false`, wenn die Engine (noch)
     * nicht bereit ist oder auf diesem Gerät keine **Offline**-Stimme für die
     * Sprache verfügbar ist - dann bietet die UI [openTtsSettings] an.
     */
    fun speak(text: String, language: Language): Boolean {
        if (!ready || text.isBlank()) return false
        val locale = Locale.forLanguageTag(language.speechLocaleTag)
        val availability = runCatching { tts.setLanguage(locale) }
            .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (availability == TextToSpeech.LANG_MISSING_DATA ||
            availability == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            return false
        }
        if (!rateApplied) {
            runCatching { tts.setSpeechRate(SPEECH_RATE) }
            rateApplied = true
        }
        if (!ensureOfflineVoice(language.code, locale)) return false
        return runCatching { tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID) }
            .getOrDefault(TextToSpeech.ERROR) == TextToSpeech.SUCCESS
    }

    /**
     * Stimmen-Lage für [language] - Grundlage der Statuszeile im Menü
     * "Sprachpakete", damit fehlende Stimmen VOR dem Kundengespräch auffallen.
     */
    fun voiceStatus(language: Language): VoiceStatus {
        if (!ready) return VoiceStatus.NICHT_BEREIT
        val locale = Locale.forLanguageTag(language.speechLocaleTag)
        val candidates = voicesFor(locale)
        return when {
            candidates.isNullOrEmpty() -> {
                // Manche Engines pflegen die Voice-API nicht - dann entscheidet
                // die klassische Verfügbarkeitsabfrage.
                val avail = runCatching { tts.isLanguageAvailable(locale) }
                    .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
                if (avail == TextToSpeech.LANG_MISSING_DATA ||
                    avail == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    VoiceStatus.FEHLT
                } else {
                    VoiceStatus.OFFLINE_BEREIT
                }
            }
            candidates.any { it.isUsableOffline() } -> VoiceStatus.OFFLINE_BEREIT
            else -> VoiceStatus.NUR_ONLINE
        }
    }

    /**
     * Klartext für die Mitarbeiterseite, wenn [speak] `false` geliefert hat -
     * je nach Ursache ein anderer Rat (Engine startet noch / nur Netz-Stimme /
     * gar keine Stimme).
     */
    fun explainSilence(language: Language): String = when (voiceStatus(language)) {
        VoiceStatus.NICHT_BEREIT ->
            "Sprachausgabe startet noch - bitte gleich noch einmal versuchen."
        VoiceStatus.NUR_ONLINE ->
            "${language.displayName}: nur Online-Stimme installiert - bleibt aus " +
                "Datenschutzgründen stumm. Bitte die Offline-Stimme nachladen."
        VoiceStatus.FEHLT ->
            "${language.displayName}: keine Stimme installiert."
        VoiceStatus.OFFLINE_BEREIT ->
            "Sprachausgabe für ${language.displayName} ist gerade nicht möglich."
    }

    /** Bricht eine laufende Sprachausgabe ab (z. B. bevor das Mikrofon aufgeht). */
    fun stop() {
        runCatching { tts.stop() }
    }

    fun shutdown() {
        runCatching { tts.stop() }
        runCatching { tts.shutdown() }
    }

    /**
     * Alle Stimmen der Engine für die Sprache von [locale] (nur Sprachcode,
     * z. B. jede "uk-*"-Stimme) oder `null`, wenn die Engine die Voice-API
     * nicht anbietet bzw. dabei einen Laufzeitfehler wirft (kommt bei
     * manchen Hersteller-Engines vor).
     */
    private fun voicesFor(locale: Locale): List<Voice>? =
        runCatching { tts.voices }.getOrNull()
            ?.filter { it.locale.language == locale.language }

    /**
     * Wählt die beste installierte Offline-Stimme für die Sprache. Liefert
     * `false`, wenn es für die Sprache **ausschließlich Netz-Stimmen** (oder
     * nicht installierte Stimmen) gibt - die werden aus Datenschutzgründen
     * nie verwendet (der zu sprechende Text würde das Gerät verlassen).
     * Listet die Engine gar keine Stimmen (manche pflegen die Voice-API
     * nicht), bleibt es bei der Standardstimme von [TextToSpeech.setLanguage].
     */
    private fun ensureOfflineVoice(languageCode: String, locale: Locale): Boolean {
        val cached = voiceCache[languageCode]
        if (cached != null) {
            runCatching { tts.voice = cached }
            return true
        }
        val candidates = voicesFor(locale) ?: return true
        val best = candidates
            .filter { it.isUsableOffline() }
            .maxByOrNull { it.quality }
        if (best != null) {
            voiceCache[languageCode] = best
            runCatching { tts.voice = best }
            return true
        }
        return candidates.isEmpty()
    }

    companion object {
        private const val UTTERANCE_ID = "vip-uebersetzung"

        /** 1.0 = Engine-Standard; leicht verlangsamt für bessere Verständlichkeit. */
        private const val SPEECH_RATE = 0.9f

        /**
         * Offline nutzbar heißt: keine Netzverbindung nötig UND die
         * Sprachdaten sind tatsächlich auf dem Gerät. Google TTS führt
         * Stimmen für noch nicht geladene Sprachen mit dem Feature
         * `notInstalled` in der Liste - die dürfen nicht als bereit gelten.
         */
        private fun Voice.isUsableOffline(): Boolean =
            !isNetworkConnectionRequired &&
                features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true

        /**
         * Öffnet die Android-Einstellungen der Sprachausgabe (dort lassen sich
         * fehlende Offline-Stimmen nachinstallieren). Liefert `false`, wenn das
         * Gerät den Einstellungs-Screen nicht anbietet.
         */
        fun openTtsSettings(context: Context): Boolean =
            runCatching {
                context.startActivity(
                    Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.isSuccess
    }
}
