package de.vip.liveuebersetzer

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

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
         * Nur Netz-Stimmen installiert. Die App nutzt sie bewusst nicht
         * (der zu sprechende Text würde das Gerät verlassen - Datenschutz),
         * die Ausgabe bleibt stumm, bis eine Offline-Stimme installiert ist.
         */
        NUR_ONLINE,

        /** Keine Stimme für diese Sprache installiert. */
        FEHLT,
    }

    private var ready = false
    private var rateApplied = false

    /** Beste Offline-Stimme je Sprachcode, einmal ermittelt und gemerkt. */
    private val voiceCache = HashMap<String, Voice>()

    private val tts = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }

    /**
     * Spricht [text] in [language]. Liefert `false`, wenn die Engine (noch)
     * nicht bereit ist oder auf diesem Gerät keine **Offline**-Stimme für die
     * Sprache verfügbar ist - dann bietet die UI [openTtsSettings] an.
     */
    fun speak(text: String, language: Language): Boolean {
        if (!ready || text.isBlank()) return false
        val locale = Locale.forLanguageTag(language.speechLocaleTag)
        val availability = tts.setLanguage(locale)
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
        return tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID) == TextToSpeech.SUCCESS
    }

    /**
     * Stimmen-Lage für [language] - Grundlage der Statuszeile im Menü
     * "Sprachpakete", damit fehlende Stimmen VOR dem Kundengespräch auffallen.
     */
    fun voiceStatus(language: Language): VoiceStatus {
        if (!ready) return VoiceStatus.NICHT_BEREIT
        val locale = Locale.forLanguageTag(language.speechLocaleTag)
        val voices = runCatching { tts.voices }.getOrNull()
        val candidates = voices.orEmpty().filter { it.locale.language == locale.language }
        return when {
            candidates.any { !it.isNetworkConnectionRequired } -> VoiceStatus.OFFLINE_BEREIT
            candidates.isNotEmpty() -> VoiceStatus.NUR_ONLINE
            else -> {
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
        }
    }

    /** Bricht eine laufende Sprachausgabe ab (z. B. bevor das Mikrofon aufgeht). */
    fun stop() {
        tts.stop()
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    /**
     * Wählt die beste installierte Offline-Stimme für die Sprache. Liefert
     * `false`, wenn es für die Sprache **ausschließlich Netz-Stimmen** gibt -
     * die werden aus Datenschutzgründen nie verwendet (der zu sprechende
     * Text würde das Gerät verlassen). Listet die Engine gar keine Stimmen
     * (manche pflegen die Voice-API nicht), bleibt es bei der Standardstimme
     * von [TextToSpeech.setLanguage]. Alles in `runCatching`, weil manche
     * Engines bei der Stimmen-Abfrage Laufzeitfehler werfen.
     */
    private fun ensureOfflineVoice(languageCode: String, locale: Locale): Boolean {
        val cached = voiceCache[languageCode]
        if (cached != null) {
            runCatching { tts.voice = cached }
            return true
        }
        val voices = runCatching { tts.voices }.getOrNull() ?: return true
        val candidates = voices.filter { it.locale.language == locale.language }
        val best = candidates
            .filter { !it.isNetworkConnectionRequired }
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
         * Öffnet die Android-Einstellungen der Sprachausgabe (dort lassen sich
         * fehlende Offline-Stimmen nachinstallieren). Liefert `false`, wenn das
         * Gerät den Einstellungs-Screen nicht anbietet.
         */
        fun openTtsSettings(context: Context): Boolean =
            runCatching { context.startActivity(Intent("com.android.settings.TTS_SETTINGS")) }.isSuccess
    }
}
