package de.vip.liveuebersetzer

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.vip.liveuebersetzer.ui.components.Badge
import de.vip.liveuebersetzer.ui.components.RowState
import de.vip.liveuebersetzer.ui.components.StatusRow
import de.vip.liveuebersetzer.ui.components.VipButton
import de.vip.liveuebersetzer.ui.components.VipCard
import de.vip.liveuebersetzer.ui.components.VipOutlinedButton
import de.vip.liveuebersetzer.ui.components.VipTextButton
import de.vip.liveuebersetzer.ui.components.VipTopBar
import de.vip.liveuebersetzer.ui.theme.VipTheme

private const val MEGABYTE = 1024L * 1024L

/**
 * Alles, was eine Sprachpaket-Karte zum Zeichnen braucht - aus
 * [ModelDownloads] und [SpeechEngine] zusammengetragen, damit die Karte
 * selbst zustandslos bleibt (Screenshot-Tests, Vorschau).
 */
data class PackageCardState(
    val language: Language,
    val engine: LiveEngine,
    val translation: ModelStatus,
    val voice: SpeechOutput.VoiceStatus,
    val mlKit: ModelStatus?,
    val mlKitMegabytes: Long?,
    val vosk: ModelStatus?,
    val voskPercent: Int?,
    val voskSupported: Boolean,
    val voskSizeMb: Int?,
    val running: Boolean,
    val speechReady: Boolean,
    val needsDownload: Boolean,
)

/**
 * Liest den Zustand für [language] aus den Snapshot-Maps. Muss innerhalb
 * einer Composition aufgerufen werden - die Lesezugriffe sorgen dafür, dass
 * die Karte neu gezeichnet wird, sobald ein Download den Zustand ändert.
 */
@Composable
fun packageCardState(context: Context, language: Language, voice: SpeechOutput.VoiceStatus): PackageCardState {
    val code = language.code
    val translation = ModelDownloads.translation[code] ?: ModelStatus.PRUEFEN
    val mlKit = ModelDownloads.mlKitSpeech[code]
    val vosk = ModelDownloads.vosk[code]
    return PackageCardState(
        language = language,
        engine = SpeechEngine.engineFor(context, code),
        translation = translation,
        voice = voice,
        mlKit = mlKit,
        mlKitMegabytes = ModelDownloads.mlKitSpeechBytes[code]?.let { it / MEGABYTE },
        vosk = vosk,
        voskPercent = ModelDownloads.voskProgress[code],
        voskSupported = VoskSpeechEngine.isSupported(code),
        voskSizeMb = VoskSpeechEngine.modelSizeMb(code),
        running = ModelDownloads.isRunning(language),
        speechReady = ModelDownloads.isSpeechReady(context, language),
        needsDownload = ModelDownloads.needsDownload(context, language),
    )
}

/**
 * Vollbild-Menü "Sprachpakete": Übersetzungs- und Erkennungsmodelle pro
 * Sprache vorab herunterladen, damit am Schalter keine Wartezeit durch
 * spontane Modell-Downloads entsteht. Zeigt außerdem pro Sprache die Lage der
 * **Sprachausgabe-Stimmen** (offline bereit / nur online / fehlt) mit
 * Probehören- und Installations-Button - so fällt eine fehlende Stimme VOR
 * dem Kundengespräch auf. Der Download-Zustand lebt in [ModelDownloads] und
 * überlebt das Verlassen des Menüs. Erreichbar über den Einrichtungs-
 * Assistenten (Logo antippen → "Sprachpakete öffnen").
 */
@Composable
fun ModelsScreen(
    onClose: () -> Unit,
    speechOutput: SpeechOutput,
) {
    val context = LocalContext.current
    val voiceStatuses = remember { mutableStateMapOf<String, SpeechOutput.VoiceStatus>() }

    LaunchedEffect(Unit) {
        // Erst auf die TTS-Engine warten - sonst stand direkt nach dem
        // App-Start bei allen Sprachen dauerhaft "wird vorbereitet".
        speechOutput.awaitReady()
        LanguageCatalog.all.forEach { voiceStatuses[it.code] = speechOutput.voiceStatus(it) }
        ModelDownloads.refresh(context)
    }

    ModelsContent(
        cards = LanguageCatalog.all.map { language ->
            packageCardState(
                context = context,
                language = language,
                voice = voiceStatuses[language.code] ?: SpeechOutput.VoiceStatus.NICHT_BEREIT,
            )
        },
        onLoad = { ModelDownloads.start(context, it) },
        onLoadVosk = { ModelDownloads.startVosk(context, it) },
        onListen = { language ->
            // Probehören spricht die Begrüßung - so lässt sich die Stimme
            // VOR dem Kundengespräch prüfen. Schlägt es fehl, direkt in die
            // Stimmen-Einstellungen springen.
            if (!speechOutput.speak(language.greeting, language)) {
                SpeechOutput.openTtsSettings(context)
            }
            voiceStatuses[language.code] = speechOutput.voiceStatus(language)
        },
        onInstallVoice = { SpeechOutput.openTtsSettings(context) },
        onClose = onClose,
    )
}

/** Zustandslose Darstellung des Sprachpakete-Menüs. */
@Composable
fun ModelsContent(
    cards: List<PackageCardState>,
    onLoad: (Language) -> Unit,
    onLoadVosk: (Language) -> Unit,
    onListen: (Language) -> Unit,
    onInstallVoice: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        VipTopBar(title = "Sprachpakete", onBack = onClose)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    modifier = Modifier.padding(bottom = 6.dp),
                    text = "Einmalig mit Internet (am besten WLAN) vorbereiten - danach " +
                        "arbeitet die App komplett offline. Downloads laufen weiter, auch " +
                        "wenn dieses Menü verlassen wird.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VipTheme.colors.textMuted,
                )
            }
            items(cards, key = { it.language.code }) { card ->
                LanguagePackageCard(
                    state = card,
                    onLoad = { onLoad(card.language) },
                    onLoadVosk = { onLoadVosk(card.language) },
                    onListen = { onListen(card.language) },
                    onInstallVoice = onInstallVoice,
                )
            }
        }
    }
}

/** Eine Karte des Sprachpakete-Menüs: drei Statuszeilen, "Laden", Probehören, Alternativen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguagePackageCard(
    state: PackageCardState,
    onLoad: () -> Unit,
    onLoadVosk: () -> Unit,
    onListen: () -> Unit,
    onInstallVoice: () -> Unit,
) {
    // Für das Schalterpersonal formuliert, nicht für Entwickler: "ML Kit"
    // und "Vosk" sagen dort niemandem etwas.
    val engineLabel = when (state.engine) {
        LiveEngine.VOSK -> "Eigenes Sprachmodell"
        LiveEngine.MLKIT -> "Android-Erkennung"
        LiveEngine.NONE -> "Keine Live-Erkennung"
    }

    // --- Live-Erkennung: Text und Zustand je nach zuständiger Engine ---------
    val speechState: RowState
    val speechText: String
    var speechProgress: Float? = null
    var speechShowProgress = false
    when (state.engine) {
        LiveEngine.VOSK -> {
            speechState = RowState.OK
            speechText = "Live-Erkennung: eigenes Sprachmodell bereit"
        }
        LiveEngine.MLKIT -> when (state.mlKit) {
            null, ModelStatus.PRUEFEN -> {
                speechState = RowState.PENDING
                speechText = "Live-Erkennung: prüfe …"
            }
            ModelStatus.FEHLT -> {
                speechState = RowState.WARN
                speechText = "Live-Erkennung: Erkennungsmodell noch nicht geladen"
            }
            ModelStatus.LAEDT -> {
                speechState = RowState.PENDING
                speechText = "Live-Erkennung: Erkennungsmodell wird geladen …" +
                    (state.mlKitMegabytes?.let { " $it MB" } ?: "")
                speechShowProgress = true
            }
            ModelStatus.GELADEN -> {
                speechState = RowState.OK
                speechText = "Live-Erkennung: bereit"
            }
            ModelStatus.FEHLER -> {
                speechState = RowState.ERROR
                speechText = "Live-Erkennung: Download fehlgeschlagen oder Status nicht ermittelbar - " +
                    "Internetverbindung prüfen und erneut laden"
            }
            ModelStatus.NICHT_VERFUEGBAR -> {
                speechState = RowState.ERROR
                speechText = "Live-Erkennung: über die Android-Erkennung auf diesem Gerät nicht verfügbar" +
                    if (state.voskSupported) {
                        " - stattdessen unten das eigene Sprachmodell laden"
                    } else {
                        " (in den Android-Einstellungen die Offline-Spracheingabe für diese Sprache installieren)"
                    }
            }
        }
        LiveEngine.NONE -> when {
            !state.voskSupported -> {
                speechState = RowState.ERROR
                speechText = "Live-Erkennung: braucht Android 12 oder neuer"
            }
            else -> when (state.vosk) {
                null, ModelStatus.PRUEFEN -> {
                    speechState = RowState.PENDING
                    speechText = "Live-Erkennung: prüfe …"
                }
                ModelStatus.LAEDT -> {
                    speechState = RowState.PENDING
                    speechText = "Live-Erkennung: Sprachmodell wird geladen …" +
                        (state.voskPercent?.let { " $it %" } ?: "")
                    speechShowProgress = true
                    speechProgress = state.voskPercent?.let { it / 100f }
                }
                ModelStatus.FEHLER -> {
                    speechState = RowState.ERROR
                    speechText = "Live-Erkennung: Download fehlgeschlagen - Internetverbindung prüfen"
                }
                else -> {
                    speechState = RowState.WARN
                    speechText = "Live-Erkennung: eigenes Sprachmodell noch nicht geladen" +
                        (state.voskSizeMb?.let { " (~$it MB, am besten über WLAN)" } ?: "")
                }
            }
        }
    }
    // Zweigleisige Sprache, deren Vosk-Alternative gerade lädt, während
    // ML Kit (noch) die aktive Engine ist.
    val voskAlternativeLoading = state.engine == LiveEngine.MLKIT && state.vosk == ModelStatus.LAEDT
    val voskAlternativeOffered = state.engine == LiveEngine.MLKIT && state.voskSupported && !state.running &&
        (state.vosk == ModelStatus.FEHLT || state.vosk == ModelStatus.FEHLER)

    VipCard(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.language.displayName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            Badge(
                text = engineLabel,
                tone = if (state.engine == LiveEngine.NONE) RowState.WARN else RowState.NEUTRAL,
            )
        }
        Column(
            modifier = Modifier.padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusRow(
                state = when (state.translation) {
                    ModelStatus.GELADEN -> RowState.OK
                    ModelStatus.PRUEFEN, ModelStatus.LAEDT -> RowState.PENDING
                    ModelStatus.FEHLT -> RowState.WARN
                    ModelStatus.FEHLER, ModelStatus.NICHT_VERFUEGBAR -> RowState.ERROR
                },
                text = when (state.translation) {
                    ModelStatus.PRUEFEN -> "Übersetzung: prüfe …"
                    ModelStatus.FEHLT -> "Übersetzung: noch nicht geladen"
                    ModelStatus.LAEDT -> "Übersetzung: wird heruntergeladen …"
                    ModelStatus.GELADEN -> "Übersetzung: bereit"
                    ModelStatus.FEHLER -> "Übersetzung: Download fehlgeschlagen - Internetverbindung prüfen"
                    ModelStatus.NICHT_VERFUEGBAR -> "Übersetzung: auf diesem Gerät nicht verfügbar"
                },
                showProgress = state.translation == ModelStatus.LAEDT,
            )
            StatusRow(
                state = when (state.voice) {
                    SpeechOutput.VoiceStatus.OFFLINE_BEREIT -> RowState.OK
                    SpeechOutput.VoiceStatus.NICHT_BEREIT -> RowState.PENDING
                    SpeechOutput.VoiceStatus.NUR_ONLINE -> RowState.WARN
                    SpeechOutput.VoiceStatus.FEHLT -> RowState.ERROR
                },
                text = when (state.voice) {
                    SpeechOutput.VoiceStatus.NICHT_BEREIT -> "Sprachausgabe: wird vorbereitet …"
                    SpeechOutput.VoiceStatus.OFFLINE_BEREIT -> "Sprachausgabe: Offline-Stimme bereit"
                    SpeechOutput.VoiceStatus.NUR_ONLINE ->
                        "Sprachausgabe: nur Online-Stimme installiert - wird aus " +
                            "Datenschutzgründen nicht genutzt, bitte Offline-Stimme laden"
                    SpeechOutput.VoiceStatus.FEHLT -> "Sprachausgabe: keine Stimme installiert"
                },
            )
            StatusRow(
                state = speechState,
                text = speechText,
                progress = speechProgress,
                showProgress = speechShowProgress,
            )
            if (voskAlternativeLoading) {
                StatusRow(
                    state = RowState.PENDING,
                    text = "Eigenes Sprachmodell wird geladen …" + (state.voskPercent?.let { " $it %" } ?: ""),
                    progress = state.voskPercent?.let { it / 100f },
                    showProgress = true,
                )
            }
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            VipButton(
                text = when {
                    state.running -> "Lädt …"
                    state.translation == ModelStatus.GELADEN && state.speechReady -> "Geladen ✓"
                    else -> "Laden"
                },
                onClick = onLoad,
                enabled = state.needsDownload && !state.running,
                icon = if (state.needsDownload && !state.running) Icons.Rounded.Download else null,
            )
            VipOutlinedButton(text = "Probehören", onClick = onListen, icon = Icons.Rounded.PlayArrow)
            if (state.voice == SpeechOutput.VoiceStatus.NUR_ONLINE || state.voice == SpeechOutput.VoiceStatus.FEHLT) {
                VipTextButton(text = "Stimme installieren", onClick = onInstallVoice)
            }
        }
        if (voskAlternativeOffered) {
            // Zweigleisige Sprache (Chinesisch, Japanisch, Koreanisch, Hindi):
            // Das gebündelte Vosk-Modell ist der am Testgerät erprobte Weg
            // und hat Vorrang, sobald es geladen ist.
            Column(modifier = Modifier.padding(top = 10.dp)) {
                VipOutlinedButton(
                    text = "Eigenes Sprachmodell laden" + (state.voskSizeMb?.let { " (~$it MB)" } ?: ""),
                    onClick = onLoadVosk,
                    icon = Icons.Rounded.Download,
                )
                Text(
                    modifier = Modifier.padding(top = 6.dp),
                    text = "Alternative zur Android-Erkennung - z. B. wenn diese auf dem Gerät " +
                        "nicht verfügbar ist oder schlecht versteht. Hat nach dem Laden Vorrang.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VipTheme.colors.textMuted,
                )
            }
        }
    }
}
