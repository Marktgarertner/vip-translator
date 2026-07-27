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

    private var ready = false
    private var rateApplied = false

    /** Beste Offline-Stimme je Sprachcode, einmal ermittelt und gemerkt. */
    private val voiceCache = HashMap<String, Voice>()

    private val tts = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }

    /**
     * Spricht [text] in [language]. Liefert `false`, wenn die Engine (noch)
     * nicht bereit ist oder auf diesem Gerät keine Stimme für die Sprache
     * verfügbar ist - dann bietet die UI [openTtsSettings] an.
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
        applyBestOfflineVoice(language.code, locale)
        return tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID) == TextToSpeech.SUCCESS
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
     * Wählt die beste installierte Offline-Stimme für die Sprache. Alles in
     * `runCatching`, weil manche TTS-Engines bei der Stimmen-Abfrage
     * Laufzeitfehler werfen - dann bleibt es einfach bei der Standardstimme
     * von [TextToSpeech.setLanguage].
     */
    private fun applyBestOfflineVoice(languageCode: String, locale: Locale) {
        val cached = voiceCache[languageCode]
        if (cached != null) {
            runCatching { tts.voice = cached }
            return
        }
        val best = runCatching {
            tts.voices
                ?.filter { it.locale.language == locale.language && !it.isNetworkConnectionRequired }
                ?.maxByOrNull { it.quality }
        }.getOrNull() ?: return
        voiceCache[languageCode] = best
        runCatching { tts.voice = best }
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
