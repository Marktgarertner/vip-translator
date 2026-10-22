package de.vip.liveuebersetzer

import com.google.mlkit.nl.translate.TranslateLanguage

/**
 * Eine der vom ViP Live-Übersetzer unterstützten Sprachen.
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
 * @param speechLocaleTag BCP-47-Tag für die Stimmenwahl der Sprachausgabe
 *   ([SpeechOutput]).
 * @param mlKitSpeechLocaleTag Locale, unter der ML Kit GenAI Speech Recognition
 *   (Basic-Modus) diese Sprache führt - exakt so geschrieben wie in der
 *   Google-Doku, die für Chinesisch z. B. `cmn-Hans-CN` statt `zh-CN`
 *   verlangt. `null`, wenn der Basic-Modus die Sprache nicht listet; dann
 *   läuft die Live-Erkennung ausschließlich über die gebündelte
 *   Offline-Erkennung [VoskSpeechEngine] - siehe [SpeechEngine.engineFor].
 */
data class Language(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val greeting: String,
    val tapToSpeak: String,
    val mlKitLanguage: String,
    val speechLocaleTag: String,
    val mlKitSpeechLocaleTag: String?,
) {
    /** `true`, wenn ML Kit GenAI Speech Recognition (Basic-Modus) diese Sprache abdeckt. */
    val mlKitLiveSpeech: Boolean get() = mlKitSpeechLocaleTag != null
}

/**
 * Zentrale Sprachliste des ViP Live-Übersetzers.
 *
 * Übersetzung (ML Kit Translate, GA) deckt alle Sprachen dieser Liste ab.
 *
 * Live-Spracherkennung läuft zweigleisig, in beiden Fällen vollständig
 * on-device (siehe README, Abschnitt "Datenschutz"):
 *  - ML Kit GenAI Speech Recognition (Basic-Modus, Alpha,
 *    `com.google.mlkit:genai-speech-recognition:1.0.0-alpha1`). Die
 *    Locale-Liste des Basic-Modus stammt aus der aktuellen Google-Doku
 *    (Stand Oktober 2026): en-US, fr-FR, it-IT, de-DE, es-ES, hi-IN, ja-JP,
 *    pt-BR, tr-TR, pl-PL, cmn-Hans-CN, ko-KR, cmn-Hant-TW, ru-RU, vi-VN.
 *    Hindi, Japanisch, Chinesisch und Koreanisch sind dort inzwischen
 *    enthalten - frühere Fassungen dieser Datei kannten sie nur über Vosk.
 *  - [VoskSpeechEngine] (Vosk, Apache-2.0, gebündelt statt über einen
 *    Android-Systemdienst) für Ukrainisch, Arabisch und Persisch, die der
 *    Basic-Modus nicht listet (Arabisch nur im "Advanced"-Modus, der laut
 *    Doku exklusiv auf Pixel 10/11 läuft). Ein Praxistest zeigte außerdem,
 *    dass die geräteinterne Systemerkennung Ukrainisch/Arabisch nicht
 *    unterstützt - Vosk ist für diese Sprachen der einzige Weg.
 *  - Für Chinesisch, Japanisch, Koreanisch und Hindi gibt es **beides**:
 *    ein geladenes Vosk-Modell hat Vorrang (dieser Pfad ist am Testgerät
 *    erprobt), ohne Vosk-Modell versucht die App ML Kit - siehe
 *    [SpeechEngine.engineFor]. Ein Cloud-Fallback kommt weiterhin nicht
 *    infrage.
 */
object LanguageCatalog {

    val all: List<Language> = listOf(
        Language("de", "Deutsch", "Deutsch", "Herzlich willkommen!", "Zum Sprechen antippen", TranslateLanguage.GERMAN, "de-DE", "de-DE"),
        Language("en", "Englisch", "English", "Welcome!", "Tap to speak", TranslateLanguage.ENGLISH, "en-US", "en-US"),
        Language("ru", "Russisch", "Русский", "Добро пожаловать!", "Нажмите и говорите", TranslateLanguage.RUSSIAN, "ru-RU", "ru-RU"),
        Language("tr", "Türkisch", "Türkçe", "Hoş geldiniz!", "Konuşmak için dokunun", TranslateLanguage.TURKISH, "tr-TR", "tr-TR"),
        Language("pl", "Polnisch", "Polski", "Witamy!", "Dotknij, aby mówić", TranslateLanguage.POLISH, "pl-PL", "pl-PL"),
        Language("vi", "Vietnamesisch", "Tiếng Việt", "Chào mừng quý khách!", "Chạm để nói", TranslateLanguage.VIETNAMESE, "vi-VN", "vi-VN"),
        Language("fr", "Französisch", "Français", "Bienvenue !", "Appuyez pour parler", TranslateLanguage.FRENCH, "fr-FR", "fr-FR"),
        Language("es", "Spanisch", "Español", "¡Bienvenido!", "Toque para hablar", TranslateLanguage.SPANISH, "es-ES", "es-ES"),
        Language("it", "Italienisch", "Italiano", "Benvenuti!", "Tocca per parlare", TranslateLanguage.ITALIAN, "it-IT", "it-IT"),
        // Ab hier: kein ML-Kit-Live (siehe Objekt-Kdoc) - die Live-Erkennung
        // läuft über die gebündelten Vosk-Modelle, die im Menü "Sprachpakete"
        // geladen werden müssen (Größen siehe VoskSpeechEngine.modelUrls).
        Language("uk", "Ukrainisch", "Українська", "Ласкаво просимо!", "Натисніть, щоб говорити", TranslateLanguage.UKRAINIAN, "uk-UA", null),
        Language("ar", "Arabisch", "العربية", "أهلاً وسهلاً!", "انقر للتحدث", TranslateLanguage.ARABIC, "ar-SA", null),
        Language("fa", "Persisch", "فارسی", "خوش آمدید!", "برای صحبت ضربه بزنید", TranslateLanguage.PERSIAN, "fa-IR", null),
        // Auf Wunsch aus dem Praxistest ergänzt (asiatischer Raum). Zweigleisig:
        // ML Kit Basic listet sie inzwischen, ein geladenes Vosk-Modell hat
        // aber Vorrang (siehe Objekt-Kdoc).
        Language("zh", "Chinesisch", "中文", "欢迎光临！", "点击说话", TranslateLanguage.CHINESE, "zh-CN", "cmn-Hans-CN"),
        Language("ja", "Japanisch", "日本語", "いらっしゃいませ！", "タップして話す", TranslateLanguage.JAPANESE, "ja-JP", "ja-JP"),
        Language("ko", "Koreanisch", "한국어", "어서 오세요!", "눌러서 말하기", TranslateLanguage.KOREAN, "ko-KR", "ko-KR"),
        Language("hi", "Hindi", "हिन्दी", "आपका स्वागत है!", "बोलने के लिए टैप करें", TranslateLanguage.HINDI, "hi-IN", "hi-IN"),
    )

    fun byCode(code: String): Language =
        all.first { it.code == code }

    val defaultSource: Language = byCode("de")
    val defaultTarget: Language = byCode("en")
}
