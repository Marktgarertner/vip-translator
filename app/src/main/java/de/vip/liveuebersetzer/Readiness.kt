package de.vip.liveuebersetzer

import android.content.Context

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
    /** Live-Erkennung nutzbar (ML-Kit-Modell geladen bzw. Vosk-Modell entpackt). */
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
     * Prüft alle Sprachen des Katalogs. Fragt ML Kit (Übersetzungs- und
     * Erkennungsmodelle) ab und ist deshalb `suspend`.
     *
     * Die Live-Erkennung gilt für die ML-Kit-Sprachen erst als bereit, wenn
     * das Erkennungsmodell laut `checkStatus()` tatsächlich auf dem Gerät
     * ist - früher galt sie pauschal als bereit, sobald das Gerät Android 12
     * hatte, und der erste Mikro-Tap am Schalter löste dann den Download aus.
     * Für die Vosk-Sprachen muss das Modell entpackt sein.
     */
    suspend fun check(context: Context, speechOutput: SpeechOutput): List<LanguageReadiness> {
        // Die TTS-Engine initialisiert asynchron und braucht nach dem App-Start
        // einen Moment. Ohne diese Wartezeit meldete der Assistent beim ersten
        // Öffnen fälschlich für alle Sprachen "Stimme fehlt".
        speechOutput.awaitReady()
        ModelDownloads.refresh(context)
        return LanguageCatalog.all.map { language ->
            LanguageReadiness(
                language = language,
                translationReady = ModelDownloads.translation[language.code] == ModelStatus.GELADEN,
                voiceReady = speechOutput.voiceStatus(language) == SpeechOutput.VoiceStatus.OFFLINE_BEREIT,
                speechReady = ModelDownloads.isSpeechReady(context, language),
            )
        }
    }
}
