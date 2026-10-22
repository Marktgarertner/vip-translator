package de.vip.liveuebersetzer

import androidx.compose.foundation.layout.fillMaxSize
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import de.vip.liveuebersetzer.ui.theme.VipLiveUebersetzerTheme
import org.junit.Rule
import org.junit.Test

/**
 * Screenshot-Tests: rendern die Bildschirme der App auf der JVM (Paparazzi,
 * layoutlib) - ohne Gerät und ohne Emulator. Zweck ist weniger das
 * Regressions-Sicherheitsnetz als die Sichtbarkeit: Wer am Layout arbeitet,
 * sieht nach `gradle recordPaparazziDebug` unter
 * `app/src/test/snapshots/images/` genau, was am Schalter erscheinen würde.
 * Die Bildschirme werden dafür mit erfundenen Gesprächsdaten gefüllt; keine
 * Engine (ML Kit, Vosk, TTS) wird dabei angefasst.
 */
class ScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
        theme = "android:Theme.Material.Light.NoActionBar",
        showSystemUi = false,
    )

    private val de = LanguageCatalog.byCode("de")
    private val tr = LanguageCatalog.byCode("tr")
    private val uk = LanguageCatalog.byCode("uk")

    private val sampleEntries = listOf(
        ConversationEntry(4, true, tr, de, "Ne kadar?", "Wie viel kostet das?"),
        ConversationEntry(
            3, false, de, tr,
            "Sie brauchen einen Einzelfahrausweis für den Tarifbereich AB.",
            "AB tarife bölgesi için tek yön bilet almanız gerekiyor.",
        ),
        ConversationEntry(
            2, true, tr, de,
            "Potsdam Hauptbahnhof'a gitmek istiyorum.",
            "Ich möchte zum Potsdam Hauptbahnhof fahren.",
            recognizedText = "Potstam Hauptbahnhof'a gitmek istiyorum.",
        ),
        ConversationEntry(
            1, false, de, tr,
            "Herzlich willkommen! Wie kann ich Ihnen helfen?",
            "Hoş geldiniz! Size nasıl yardımcı olabilirim?",
        ),
    )

    private fun snapshot(dark: Boolean = false, content: @androidx.compose.runtime.Composable () -> Unit) {
        paparazzi.snapshot {
            VipLiveUebersetzerTheme(darkTheme = dark) {
                androidx.compose.material3.Surface(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                ) { content() }
            }
        }
    }

    @Test
    fun gespraech_leer() = snapshot {
        ConversationScreen(
            state = ConversationUiState(staffLanguage = de, customerLanguage = tr, entries = emptyList()),
            actions = ConversationActions(),
        )
    }

    @Test
    fun gespraech_mit_beitraegen_kunde_spricht() = snapshot {
        ConversationScreen(
            state = ConversationUiState(
                staffLanguage = de,
                customerLanguage = tr,
                entries = sampleEntries,
                listening = ListeningSide.CUSTOMER,
                liveTranscript = "Teşekkür ederim",
            ),
            actions = ConversationActions(),
        )
    }

    @Test
    fun gespraech_mit_hinweisen() = snapshot {
        ConversationScreen(
            state = ConversationUiState(
                staffLanguage = de,
                customerLanguage = uk,
                entries = sampleEntries.take(2),
                errorMessage = "Ukrainisch: nur Online-Stimme installiert - bleibt aus " +
                    "Datenschutzgründen stumm. Bitte die Offline-Stimme nachladen.",
                ttsSettingsHint = true,
                customerMicReason = "Ukrainisch: Sprachmodell noch nicht geladen, deshalb keine " +
                    "Sprechtaste. Logo antippen → Sprachpakete.",
            ),
            actions = ConversationActions(),
        )
    }

    @Test
    fun gespraech_dunkel() = snapshot(dark = true) {
        ConversationScreen(
            state = ConversationUiState(
                staffLanguage = de,
                customerLanguage = tr,
                entries = sampleEntries,
                listening = ListeningSide.STAFF,
                liveTranscript = "Der nächste Bus fährt in wenigen",
            ),
            actions = ConversationActions(),
        )
    }

    @Test
    fun gespraech_tablet() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_C)
        snapshot {
            ConversationScreen(
                state = ConversationUiState(staffLanguage = de, customerLanguage = tr, entries = sampleEntries),
                actions = ConversationActions(),
            )
        }
    }

    @Test
    fun einrichtung() = snapshot {
        SetupContent(
            micGranted = true,
            readiness = LanguageCatalog.all.mapIndexed { index, language ->
                LanguageReadiness(
                    language = language,
                    translationReady = index % 5 != 3,
                    voiceReady = index % 3 != 2,
                    speechReady = index < 11,
                )
            },
            versionName = "1.3",
            onRequestMic = {}, onOpenModels = {}, onOpenGlossary = {}, onOpenPhrases = {}, onDone = {}, onClose = {},
        )
    }

    @Test
    fun einrichtung_ohne_mikrofon_prueft_noch() = snapshot {
        SetupContent(
            micGranted = false,
            readiness = null,
            versionName = "1.3",
            onRequestMic = {}, onOpenModels = {}, onOpenGlossary = {}, onOpenPhrases = {}, onDone = {}, onClose = {},
        )
    }

    private fun card(
        language: Language,
        engine: LiveEngine,
        translation: ModelStatus = ModelStatus.GELADEN,
        voice: SpeechOutput.VoiceStatus = SpeechOutput.VoiceStatus.OFFLINE_BEREIT,
        mlKit: ModelStatus? = null,
        mlKitMegabytes: Long? = null,
        vosk: ModelStatus? = null,
        voskPercent: Int? = null,
        running: Boolean = false,
        speechReady: Boolean = true,
        needsDownload: Boolean = false,
    ) = PackageCardState(
        language = language,
        engine = engine,
        translation = translation,
        voice = voice,
        mlKit = mlKit,
        mlKitMegabytes = mlKitMegabytes,
        vosk = vosk,
        voskPercent = voskPercent,
        voskSupported = VoskSpeechEngine.isSupported(language.code),
        voskSizeMb = VoskSpeechEngine.modelSizeMb(language.code),
        running = running,
        speechReady = speechReady,
        needsDownload = needsDownload,
    )

    private val sampleCards = listOf(
        card(de, LiveEngine.MLKIT, mlKit = ModelStatus.GELADEN),
        card(
            tr, LiveEngine.MLKIT, translation = ModelStatus.FEHLT, mlKit = ModelStatus.FEHLT,
            voice = SpeechOutput.VoiceStatus.NUR_ONLINE, speechReady = false, needsDownload = true,
        ),
        card(
            LanguageCatalog.byCode("pl"), LiveEngine.MLKIT, translation = ModelStatus.LAEDT, mlKit = ModelStatus.LAEDT,
            mlKitMegabytes = 23, running = true, speechReady = false,
        ),
        card(
            uk, LiveEngine.NONE, vosk = ModelStatus.LAEDT, voskPercent = 42, running = true, speechReady = false,
        ),
        card(
            LanguageCatalog.byCode("ar"), LiveEngine.NONE, vosk = ModelStatus.FEHLT,
            voice = SpeechOutput.VoiceStatus.FEHLT, speechReady = false, needsDownload = true,
        ),
        card(
            LanguageCatalog.byCode("ja"), LiveEngine.MLKIT, mlKit = ModelStatus.NICHT_VERFUEGBAR, vosk = ModelStatus.FEHLT,
            speechReady = false,
        ),
        card(LanguageCatalog.byCode("zh"), LiveEngine.VOSK, vosk = ModelStatus.GELADEN),
    )

    @Test
    fun sprachpakete() = snapshot {
        ModelsContent(
            cards = sampleCards,
            onLoad = {}, onLoadVosk = {}, onListen = {}, onInstallVoice = {}, onClose = {},
        )
    }

    @Test
    fun sprachpakete_dunkel() = snapshot(dark = true) {
        ModelsContent(
            cards = sampleCards,
            onLoad = {}, onLoadVosk = {}, onListen = {}, onInstallVoice = {}, onClose = {},
        )
    }

    @Test
    fun schnellbausteine() = snapshot {
        PhrasesScreen(customerLanguage = tr, onPhraseSelected = {}, onClose = {})
    }

    @Test
    fun fachbegriffe() = snapshot {
        GlossaryContent(
            terms = listOf("PotsdamPass", "Schwielowseeticket", "Viereckremise"),
            input = "",
            onInputChange = {}, onAdd = {}, onRemove = {}, onClose = {},
        )
    }

    @Test
    fun fachbegriffe_leer() = snapshot {
        GlossaryContent(terms = emptyList(), input = "Kirchsteig", onInputChange = {}, onAdd = {}, onRemove = {}, onClose = {})
    }
}
