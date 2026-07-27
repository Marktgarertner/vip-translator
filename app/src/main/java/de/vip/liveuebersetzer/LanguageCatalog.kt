package de.vip.liveuebersetzer

import com.google.mlkit.nl.translate.TranslateLanguage

/**
 * Eine der 11 vom ViP Live-Übersetzer unterstützten Sprachen.
 *
 * @param code ISO-639-1-Code, intern durchgängig als Schlüssel verwendet.
 * @param displayName Anzeigename in der (deutschsprachigen) Mitarbeiter-UI.
 * @param nativeName Name der Sprache in der Sprache selbst - für die
 *   Sprachauswahl auf der Kundenseite.
 * @param greeting Begrüßung in der Sprache selbst - wird auf der Kundenseite
 *   des Splitscreens angezeigt.
 * @param tapToSpeak "Zum Sprechen antippen" in der Sprache selbst - Beschriftung
 *   der Sprechtaste auf der Kundenseite.
 * @param mlKitLanguage ML-Kit-Translate-Sprachkonstante (siehe [TranslateLanguage]).
 * @param speechLocaleTag BCP-47-Tag für die Spracherkennung ([SpeechEngine] /
 *   [VoskSpeechEngine]) und die Stimmenwahl der Sprachausgabe ([SpeechOutput]).
 * @param mlKitLiveSpeech `true`, wenn ML Kit GenAI Speech Recognition (Basic-Modus)
 *   diese Sprache abdeckt. Sprachen ohne ML-Kit-Abdeckung (Ukrainisch, Arabisch)
 *   laufen stattdessen über die gebündelte Offline-Erkennung [VoskSpeechEngine] -
 *   siehe [SpeechEngine.engineFor].
 */
data class Language(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val greeting: String,
    val tapToSpeak: String,
    val mlKitLanguage: String,
    val speechLocaleTag: String,
    val mlKitLiveSpeech: Boolean,
)

/**
 * Zentrale Sprachliste des ViP Live-Übersetzers.
 *
 * Übersetzung (ML Kit Translate, GA) deckt alle 11 Sprachen ab.
 *
 * Live-Spracherkennung läuft zweigleisig, in beiden Fällen vollständig
 * on-device (siehe README, Abschnitt "Datenschutz"):
 *  - 9 Sprachen über ML Kit GenAI Speech Recognition (Basic-Modus, Alpha,
 *    `com.google.mlkit:genai-speech-recognition:1.0.0-alpha1`).
 *  - Ukrainisch und Arabisch über [VoskSpeechEngine] (Vosk, Apache-2.0,
 *    gebündelt statt über einen Android-Systemdienst - ein Praxistest zeigte,
 *    dass die geräteinterne Systemerkennung diese beiden Sprachen nicht
 *    unterstützt). Hintergrund für die fehlende ML-Kit-Abdeckung: Ukrainisch
 *    fehlt im Basic-Modus der ML-Kit-API, Arabisch gibt es dort nur im
 *    "Advanced"-Modus, der exklusiv auf Pixel-10-Geräten läuft (Stand
 *    Google-Doku, Juli 2026). Ein Cloud-Fallback kommt weiterhin nicht infrage.
 */
object LanguageCatalog {

    val all: List<Language> = listOf(
        Language("de", "Deutsch", "Deutsch", "Herzlich willkommen!", "Zum Sprechen antippen", TranslateLanguage.GERMAN, "de-DE", true),
        Language("en", "Englisch", "English", "Welcome!", "Tap to speak", TranslateLanguage.ENGLISH, "en-US", true),
        Language("ru", "Russisch", "Русский", "Добро пожаловать!", "Нажмите и говорите", TranslateLanguage.RUSSIAN, "ru-RU", true),
        Language("tr", "Türkisch", "Türkçe", "Hoş geldiniz!", "Konuşmak için dokunun", TranslateLanguage.TURKISH, "tr-TR", true),
        Language("pl", "Polnisch", "Polski", "Witamy!", "Dotknij, aby mówić", TranslateLanguage.POLISH, "pl-PL", true),
        Language("vi", "Vietnamesisch", "Tiếng Việt", "Chào mừng quý khách!", "Chạm để nói", TranslateLanguage.VIETNAMESE, "vi-VN", true),
        Language("fr", "Französisch", "Français", "Bienvenue !", "Appuyez pour parler", TranslateLanguage.FRENCH, "fr-FR", true),
        Language("es", "Spanisch", "Español", "¡Bienvenido!", "Toque para hablar", TranslateLanguage.SPANISH, "es-ES", true),
        Language("it", "Italienisch", "Italiano", "Benvenuti!", "Tocca per parlare", TranslateLanguage.ITALIAN, "it-IT", true),
        // uk/ar: kein ML-Kit-Live (siehe Objekt-Kdoc) - Live-Erkennung läuft
        // hier über die Android-Systemerkennung, wo verfügbar.
        Language("uk", "Ukrainisch", "Українська", "Ласкаво просимо!", "Натисніть, щоб говорити", TranslateLanguage.UKRAINIAN, "uk-UA", false),
        Language("ar", "Arabisch", "العربية", "أهلاً وسهلاً!", "انقر للتحدث", TranslateLanguage.ARABIC, "ar-SA", false),
    )

    fun byCode(code: String): Language =
        all.first { it.code == code }

    val defaultSource: Language = byCode("de")
    val defaultTarget: Language = byCode("en")
}
