package de.vip.liveuebersetzer

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Einsatzbereitschaft einer Sprache auf diesem Gerät - Grundlage des
 * Einrichtungs-Assistenten.
 *
 * Eine Sprache ist erst dann wirklich einsatzbereit, wenn **alle drei** Teile
 * vorhanden sind. Genau das ist von außen nicht erkennbar und war der Grund,
 * warum ein fehlendes Vosk-Modell lange unbemerkt blieb: Die App sah fertig
 * aus, die Sprechtaste fehlte aber.
 */
data class LanguageReadiness(
    val language: Language,
    /** Übersetzungsmodell heruntergeladen (ML Kit Translate). */
    val translationReady: Boolean,
    /** Offline-Stimme für die Sprachausgabe installiert. */
    val voiceReady: Boolean,
    /** Live-Erkennung nutzbar (ML Kit bzw. geladenes Vosk-Modell). */
    val speechReady: Boolean,
) {
    val fullyReady: Boolean get() = translationReady && voiceReady && speechReady

    /** Was noch fehlt, in Klartext für die Einrichtungs-Checkliste. */
    val missingParts: List<String>
        get() = buildList {
            if (!translationReady) add("Übersetzung")
            if (!voiceReady) add("Stimme")
            if (!speechReady) add("Spracheingabe")
        }
}

object Readiness {

    /**
     * Prüft alle 11 Sprachen. Die Übersetzungsprüfung fragt ML Kit ab und ist
     * deshalb `suspend`.
     *
     * Hinweis zur Live-Erkennung: Für die ML-Kit-Sprachen gilt sie als bereit,
     * sobald das Gerät sie grundsätzlich unterstützt (Android 12+) - ML Kit
     * lädt sein Erkennungsmodell beim ersten Sprechen selbst nach, was nur
     * kurz dauert. Für Ukrainisch/Arabisch muss das deutlich größere
     * Vosk-Modell dagegen vorher vollständig geladen sein.
     */
    suspend fun check(context: Context, speechOutput: SpeechOutput): List<LanguageReadiness> {
        // Die TTS-Engine initialisiert asynchron und braucht nach dem App-Start
        // einen Moment. Ohne diese Wartezeit meldete der Assistent beim ersten
        // Öffnen fälschlich für alle Sprachen "Stimme fehlt".
        withTimeoutOrNull(3_000) {
            while (!speechOutput.isReady) delay(150)
        }
        return LanguageCatalog.all.map { language ->
            LanguageReadiness(
                language = language,
                translationReady = runCatching { TranslationEngine.isModelDownloaded(language) }
                    .getOrDefault(false),
                voiceReady = speechOutput.voiceStatus(language) == SpeechOutput.VoiceStatus.OFFLINE_BEREIT,
                speechReady = SpeechEngine.isLiveSupported(context, language.code),
            )
        }
    }
}
