package de.vip.liveuebersetzer

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.vip.liveuebersetzer.ui.components.Badge
import de.vip.liveuebersetzer.ui.components.RowState
import de.vip.liveuebersetzer.ui.components.SectionLabel
import de.vip.liveuebersetzer.ui.components.StatusRow
import de.vip.liveuebersetzer.ui.components.VipButton
import de.vip.liveuebersetzer.ui.components.VipCard
import de.vip.liveuebersetzer.ui.components.VipOutlinedButton
import de.vip.liveuebersetzer.ui.components.VipTopBar
import de.vip.liveuebersetzer.ui.theme.VipTheme

/**
 * Einrichtungs-Assistent und zugleich Einstiegsmenü (per Klick auf das Logo,
 * beim allerersten Start automatisch).
 *
 * Zweck: Am Schalter arbeiten Kolleg:innen, die nicht einschätzen können, ob
 * ein Gerät "fertig" ist - die App braucht Mikrofonfreigabe,
 * Übersetzungsmodelle, Offline-Stimmen und für die Vosk-Sprachen große
 * Modelle. Dieser Bildschirm sagt in Klartext, was bereit ist und was noch
 * fehlt, und führt mit einem Tipp genau dorthin.
 */
@Composable
fun SetupScreen(
    speechOutput: SpeechOutput,
    onOpenModels: () -> Unit,
    onOpenGlossary: () -> Unit,
    onOpenPhrases: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var readiness by remember { mutableStateOf<List<LanguageReadiness>?>(null) }
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> micGranted = granted }

    LaunchedEffect(Unit) {
        readiness = Readiness.check(context, speechOutput)
    }

    SetupContent(
        micGranted = micGranted,
        readiness = readiness,
        versionName = BuildConfig.VERSION_NAME,
        onRequestMic = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        onOpenModels = onOpenModels,
        onOpenGlossary = onOpenGlossary,
        onOpenPhrases = onOpenPhrases,
        onDone = {
            AppSettings.markSetupDone(context)
            onClose()
        },
        onClose = onClose,
    )
}

/** Zustandslose Darstellung des Einrichtungs-Assistenten. */
@Composable
fun SetupContent(
    micGranted: Boolean,
    readiness: List<LanguageReadiness>?,
    versionName: String,
    onRequestMic: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenGlossary: () -> Unit,
    onOpenPhrases: () -> Unit,
    onDone: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        VipTopBar(title = "Einrichtung", onBack = onClose, backDescription = "Zurück zum Gespräch")

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                VipCard {
                    SectionLabel("Gerät")
                    StatusRow(
                        modifier = Modifier.padding(top = 10.dp),
                        state = if (micGranted) RowState.OK else RowState.WARN,
                        text = if (micGranted) {
                            "Mikrofon freigegeben"
                        } else {
                            "Mikrofon noch nicht freigegeben - ohne Mikrofon ist kein Sprechen möglich."
                        },
                    )
                    if (!micGranted) {
                        VipButton(
                            modifier = Modifier.padding(top = 12.dp),
                            text = "Mikrofon freigeben",
                            icon = Icons.Rounded.Mic,
                            onClick = onRequestMic,
                        )
                    }
                }
            }

            item {
                val list = readiness
                val ready = list?.count { it.fullyReady } ?: 0
                val total = list?.size ?: LanguageCatalog.all.size
                VipCard {
                    SectionLabel("Sprachen")
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            text = if (list == null) "–" else "$ready",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = if (list == null) "  prüfe …" else "  von $total vollständig einsatzbereit",
                            modifier = Modifier.padding(bottom = 6.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = VipTheme.colors.textMuted,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { if (total == 0) 0f else ready / total.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = VipTheme.colors.surfaceSoft,
                        strokeCap = StrokeCap.Round,
                    )
                    Text(
                        modifier = Modifier.padding(top = 12.dp),
                        text = "Vollständig heißt: Übersetzung geladen, Offline-Stimme vorhanden " +
                            "und Spracheingabe nutzbar. Am besten einmal mit WLAN vorbereiten - " +
                            "danach arbeitet die App offline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VipTheme.colors.textMuted,
                    )
                    VipButton(
                        modifier = Modifier.padding(top = 12.dp),
                        text = "Sprachpakete öffnen",
                        onClick = onOpenModels,
                    )
                }
            }

            readiness?.let { list ->
                item {
                    VipCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                        list.forEachIndexed { index, entry ->
                            if (index > 0) {
                                HorizontalDivider(color = VipTheme.colors.outline)
                            }
                            LanguageReadinessRow(entry)
                        }
                    }
                }
            }

            item {
                VipCard {
                    SectionLabel("Weitere Einstellungen")
                    VipOutlinedButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        text = "Fachbegriffe pflegen",
                        onClick = onOpenGlossary,
                    )
                    VipOutlinedButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        text = "Schnellbausteine ansehen",
                        onClick = onOpenPhrases,
                    )
                }
            }

            item {
                VipButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    text = "Fertig - zum Gespräch",
                    onClick = onDone,
                )
            }

            item {
                // Beim Verteilen von Test-Versionen war nie klar, welcher
                // Stand auf einem Gerät läuft - deshalb hier sichtbar.
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    text = "ViP Live-Übersetzer · Version $versionName",
                    style = MaterialTheme.typography.labelMedium,
                    color = VipTheme.colors.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Eine Zeile der Sprachenliste: Name links, Haken oder "fehlt: …"-Abzeichen rechts. */
@Composable
private fun LanguageReadinessRow(entry: LanguageReadiness) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.language.displayName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
        )
        if (entry.fullyReady) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(VipTheme.colors.ok, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    Icons.Rounded.Check,
                    contentDescription = "Einsatzbereit",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                entry.missingParts.forEach { part -> Badge(text = part, tone = RowState.WARN) }
            }
        }
    }
}
