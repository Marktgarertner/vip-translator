package de.vip.liveuebersetzer

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Sprachausgabe der Übersetzungen über die systemeigene Android-TTS-Engine
 * (`android.speech.tts`). Das ist ein lokaler Systemdienst - aus der App
 * heraus wird keine Dritt-Cloud-API aufgerufen (siehe README, Abschnitt
 * "Datenschutz"). Für garantiert netzunabhängige Ausgabe können die
 * Offline-Sprachpakete der TTS-Engine in den Android-Einstellungen
 * installiert werden.
 */
class SpeechOutput(context: Context) {

    private var ready = false

    private val tts = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }

    /**
     * Spricht [text] in [language]. Liefert `false`, wenn die Engine (noch)
     * nicht bereit ist oder auf diesem Gerät keine Stimme für die Sprache
     * verfügbar ist.
     */
    fun speak(text: String, language: Language): Boolean {
        if (!ready || text.isBlank()) return false
        val availability = tts.setLanguage(Locale.forLanguageTag(language.code))
        if (availability == TextToSpeech.LANG_MISSING_DATA ||
            availability == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            return false
        }
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

    private companion object {
        const val UTTERANCE_ID = "vip-uebersetzung"
    }
}
