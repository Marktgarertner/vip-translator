package de.vip.liveuebersetzer

import com.google.mlkit.nl.translate.TranslateLanguage

/**
 * Eine der 11 vom ViP Live-Übersetzer unterstützten Sprachen.
 *
 * @param code ISO-639-1-Code, intern durchgängig als Schlüssel verwendet.
 * @param displayName Anzeigename in der (deutschsprachigen) UI.
 * @param greeting Begrüßung in der Sprache selbst - wird auf der Kundenseite
 *   des Splitscreens angezeigt.
 * @param tapToSpeak "Zum Sprechen antippen" in der Sprache selbst - Beschriftung
 *   der Sprechtaste auf der Kundenseite.
 * @param mlKitLanguage ML-Kit-Translate-Sprachkonstante (siehe [TranslateLanguage]).
 * @param speechLocaleTag BCP-47-Tag für [SpeechEngine], oder `null` wenn diese
 *   Sprache im Live-Modus nicht unterstützt wird (dann nur getippter Modus).
 */
data class Language(
    val code: String,
    val displayName: String,
    val greeting: String,
    val tapToSpeak: String,
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
        Language("de", "Deutsch", "Herzlich willkommen!", "Zum Sprechen antippen", TranslateLanguage.GERMAN, "de-DE"),
        Language("en", "Englisch", "Welcome!", "Tap to speak", TranslateLanguage.ENGLISH, "en-US"),
        Language("ru", "Russisch", "Добро пожаловать!", "Нажмите и говорите", TranslateLanguage.RUSSIAN, "ru-RU"),
        Language("tr", "Türkisch", "Hoş geldiniz!", "Konuşmak için dokunun", TranslateLanguage.TURKISH, "tr-TR"),
        Language("pl", "Polnisch", "Witamy!", "Dotknij, aby mówić", TranslateLanguage.POLISH, "pl-PL"),
        Language("vi", "Vietnamesisch", "Chào mừng quý khách!", "Chạm để nói", TranslateLanguage.VIETNAMESE, "vi-VN"),
        Language("fr", "Französisch", "Bienvenue !", "Appuyez pour parler", TranslateLanguage.FRENCH, "fr-FR"),
        Language("es", "Spanisch", "¡Bienvenido!", "Toque para hablar", TranslateLanguage.SPANISH, "es-ES"),
        Language("it", "Italienisch", "Benvenuti!", "Tocca per parlare", TranslateLanguage.ITALIAN, "it-IT"),
        // uk/ar: kein Live-Modus (siehe Klassen-Kdoc) - tapToSpeak bleibt fuer
        // Vollstaendigkeit gepflegt, die Sprechtaste wird aber nicht angezeigt.
        Language("uk", "Ukrainisch", "Ласкаво просимо!", "Натисніть, щоб говорити", TranslateLanguage.UKRAINIAN, null),
        Language("ar", "Arabisch", "أهلاً وسهلاً!", "انقر للتحدث", TranslateLanguage.ARABIC, null),
    )

    val liveSupported: List<Language> = all.filter { it.liveSpeechSupported }

    fun byCode(code: String): Language =
        all.first { it.code == code }

    val defaultSource: Language = byCode("de")
    val defaultTarget: Language = byCode("en")
}
