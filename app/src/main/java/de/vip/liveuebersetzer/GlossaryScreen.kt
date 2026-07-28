package de.vip.liveuebersetzer

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Menü "Fachbegriffe": eigene Begriffe zum ÖPNV-Wortschatz nachtragen.
 *
 * Hintergrund: Der fest eingebaute Wortschatz in [TransitGlossary] ist eine
 * Startauswahl. Welche Tarif-, Produkt- und Haltestellennamen am Schalter
 * wirklich gebraucht werden, weiß nur das Kundencenter - deshalb lassen sie
 * sich hier direkt am Gerät ergänzen, ohne Umweg über eine neue App-Version.
 *
 * Die Begriffe wirken sofort auf die Nachkorrektur der Spracherkennung: Wird
 * ein Begriff lautähnlich verschrieben oder als Kompositum zerlegt erkannt,
 * setzt ihn die App auf die hier hinterlegte Schreibweise zurück.
 */
@Composable
fun GlossaryScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val terms = remember { mutableStateListOf<String>().apply { addAll(AppSettings.customTerms(context)) } }
    var input by remember { mutableStateOf("") }

    fun persist() {
        AppSettings.saveCustomTerms(context, terms.toList())
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
                    contentDescription = "Zurück",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text(
                text = "Fachbegriffe",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Text(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = "Begriffe, die die Spracherkennung häufig falsch versteht - zum " +
                "Beispiel Tarif- und Produktnamen oder Haltestellen. Bitte genau so " +
                "schreiben, wie es richtig heißt. Die App setzt ähnlich klingende " +
                "Erkennungen dann auf diese Schreibweise zurück.",
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                label = { Text("Neuer Begriff") },
            )
            Button(
                onClick = {
                    val term = input.trim()
                    // Sehr kurze Eintraege bringen nichts: Die Nachkorrektur
                    // vergleicht kurze Woerter ohnehin nur exakt.
                    if (term.length >= 3 && terms.none { it.equals(term, ignoreCase = true) }) {
                        terms.add(term)
                        terms.sort()
                        persist()
                    }
                    input = ""
                },
                enabled = input.trim().length >= 3,
            ) {
                Text("Hinzufügen")
            }
        }

        if (terms.isEmpty()) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                text = "Noch keine eigenen Begriffe hinterlegt. Der eingebaute " +
                    "ÖPNV-Wortschatz ist trotzdem aktiv.",
                color = MaterialTheme.colorScheme.tertiary,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(terms.toList(), key = { it }) { term ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = term,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            IconButton(
                                onClick = {
                                    terms.remove(term)
                                    persist()
                                },
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Begriff \"$term\" entfernen",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
