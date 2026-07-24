package de.vip.liveuebersetzer

import com.google.mlkit.nl.translate.TranslateLanguage

/**
 * Eine der 11 vom ViP Live-Übersetzer unterstützten Sprachen.
 *
 * @param code ISO-639-1-Code, intern durchgängig als Schlüssel verwendet.
 * @param displayName Anzeigename in der (deutschsprachigen) UI.
 * @param greeting Begrüßung in der Sprache selbst - wird auf der Kundenseite
 *   des Splitscreens angezeigt.
 * @param mlKitLanguage ML-Kit-Translate-Sprachkonstante (siehe [TranslateLanguage]).
 * @param speechLocaleTag BCP-47-Tag für [SpeechEngine], oder `null` wenn diese
 *   Sprache im Live-Modus nicht unterstützt wird (dann nur getippter Modus).
 */
data class Language(
    val code: String,
    val displayName: String,
    val greeting: String,
    val mlKitLanguage: String,
    val speechLocaleTag: String?,
) {
    val liveSpeechSupported: Boolean get() = speechLocaleTag != null
}

/**
 * Zentrale Sprachliste des ViP Live-Übersetzers.
 *
 * Übersetzung (ML Kit Translate, GA) deckt alle 11 Sprachen ab.
 *
 * Live-Spracherkennung (ML Kit GenAI Speech Recognition, Alpha,
 * `com.google.mlkit:genai-speech-recognition:1.0.0-alpha1`) deckt nur 9 der
 * 11 Sprachen ab. Ukrainisch und Arabisch bleiben bewusst auf den getippten
 * Modus beschränkt:
 *  - Ukrainisch taucht im Basic-Modus der Speech-Recognition-API nicht in der
 *    Sprachliste auf.
 *  - Arabisch ist dort nur im "Advanced"-Modus verfügbar, der wiederum
 *    exklusiv auf Pixel-10-Geräten läuft (Stand Google-Doku, Juli 2026).
 * Ein Cloud-Fallback für diese beiden Sprachen kommt nicht infrage (siehe
 * README, Abschnitt "Datenschutz") und der Advanced-Modus wird bewusst nicht
 * genutzt, um die App nicht von einem einzelnen Gerätemodell abhängig zu
 * machen. Siehe auch [SpeechEngine] für die Laufzeitprüfung.
 */
object LanguageCatalog {

    val all: List<Language> = listOf(
        Language("de", "Deutsch", "Herzlich willkommen!", TranslateLanguage.GERMAN, "de-DE"),
        Language("en", "Englisch", "Welcome!", TranslateLanguage.ENGLISH, "en-US"),
        Language("ru", "Russisch", "Добро пожаловать!", TranslateLanguage.RUSSIAN, "ru-RU"),
        Language("tr", "Türkisch", "Hoş geldiniz!", TranslateLanguage.TURKISH, "tr-TR"),
        Language("pl", "Polnisch", "Witamy!", TranslateLanguage.POLISH, "pl-PL"),
        Language("vi", "Vietnamesisch", "Chào mừng quý khách!", TranslateLanguage.VIETNAMESE, "vi-VN"),
        Language("fr", "Französisch", "Bienvenue !", TranslateLanguage.FRENCH, "fr-FR"),
        Language("es", "Spanisch", "¡Bienvenido!", TranslateLanguage.SPANISH, "es-ES"),
        Language("it", "Italienisch", "Benvenuti!", TranslateLanguage.ITALIAN, "it-IT"),
        Language("uk", "Ukrainisch", "Ласкаво просимо!", TranslateLanguage.UKRAINIAN, null),
        Language("ar", "Arabisch", "أهلاً وسهلاً!", TranslateLanguage.ARABIC, null),
    )

    val liveSupported: List<Language> = all.filter { it.liveSpeechSupported }

    fun byCode(code: String): Language =
        all.first { it.code == code }

    val defaultSource: Language = byCode("de")
    val defaultTarget: Language = byCode("en")
}
