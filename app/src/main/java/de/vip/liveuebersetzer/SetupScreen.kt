package de.vip.liveuebersetzer

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * Einrichtungs-Assistent und zugleich Einstiegsmenü (per Klick auf das Logo,
 * beim allerersten Start automatisch).
 *
 * Zweck: Am Schalter arbeiten Kolleg:innen, die nicht einschätzen können, ob
 * ein Gerät "fertig" ist - die App braucht Mikrofonfreigabe,
 * Übersetzungsmodelle, Offline-Stimmen und für Ukrainisch/Arabisch große
 * Vosk-Modelle. Dieser Bildschirm sagt in Klartext, was bereit ist und was
 * noch fehlt, und führt mit einem Tipp genau dorthin.
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Zurück zum Gespräch",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text(
                text = "Einrichtung",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(text = "Mikrofon", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = if (micGranted) {
                                "Freigegeben ✓"
                            } else {
                                "Noch nicht freigegeben - ohne Mikrofon ist kein Sprechen möglich."
                            },
                            color = if (micGranted) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                        if (!micGranted) {
                            Button(onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                                Text("Mikrofon freigeben")
                            }
                        }
                    }
                }
            }

            item {
                val list = readiness
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(text = "Sprachen", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = when {
                                list == null -> "Prüfe …"
                                else -> {
                                    val ready = list.count { it.fullyReady }
                                    "$ready von ${list.size} Sprachen vollständig einsatzbereit"
                                }
                            },
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        Text(
                            text = "Vollständig heißt: Übersetzung geladen, Offline-Stimme " +
                                "vorhanden und Spracheingabe nutzbar. Am besten einmal mit " +
                                "WLAN vorbereiten - danach arbeitet die App offline.",
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        Button(onClick = onOpenModels) {
                            Text("Sprachpakete öffnen")
                        }
                    }
                }
            }

            items(readiness.orEmpty(), key = { it.language.code }) { entry ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = entry.language.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = if (entry.fullyReady) {
                                    "Einsatzbereit ✓"
                                } else {
                                    "Es fehlt noch: " + entry.missingParts.joinToString(", ")
                                },
                                color = if (entry.fullyReady) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(text = "Weitere Einstellungen", style = MaterialTheme.typography.bodyLarge)
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onOpenGlossary,
                        ) {
                            Text("Fachbegriffe pflegen")
                        }
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onOpenPhrases,
                        ) {
                            Text("Schnellbausteine ansehen")
                        }
                    }
                }
            }

            item {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        AppSettings.markSetupDone(context)
                        onClose()
                    },
                ) {
                    Text("Fertig - zum Gespräch")
                }
            }

            item {
                // Beim Verteilen von Test-Versionen war nie klar, welcher
                // Stand auf einem Geraet laeuft - deshalb hier sichtbar.
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = "ViP Live-Übersetzer, Version ${BuildConfig.VERSION_NAME}",
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}
