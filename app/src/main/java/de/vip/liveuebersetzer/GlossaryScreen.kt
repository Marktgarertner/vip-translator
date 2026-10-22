package de.vip.liveuebersetzer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Spellcheck
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import de.vip.liveuebersetzer.ui.components.EmptyState
import de.vip.liveuebersetzer.ui.components.VipButton
import de.vip.liveuebersetzer.ui.components.VipCard
import de.vip.liveuebersetzer.ui.components.VipTopBar
import de.vip.liveuebersetzer.ui.theme.VipTheme

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

    GlossaryContent(
        terms = terms,
        input = input,
        onInputChange = { input = it },
        onAdd = {
            val term = input.trim()
            // Sehr kurze Einträge bringen nichts: Die Nachkorrektur
            // vergleicht kurze Wörter ohnehin nur exakt.
            if (term.length >= 3 && terms.none { it.equals(term, ignoreCase = true) }) {
                terms.add(term)
                terms.sort()
                persist()
            }
            input = ""
        },
        onRemove = { term ->
            terms.remove(term)
            persist()
        },
        onClose = onClose,
    )
}

/** Zustandslose Darstellung des Fachbegriffe-Menüs. */
@Composable
fun GlossaryContent(
    terms: List<String>,
    input: String,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        VipTopBar(title = "Fachbegriffe", onBack = onClose)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                modifier = Modifier.padding(top = 12.dp),
                text = "Begriffe, die die Spracherkennung häufig falsch versteht - zum " +
                    "Beispiel Tarif- und Produktnamen oder Haltestellen. Bitte genau so " +
                    "schreiben, wie es richtig heißt. Die App setzt ähnlich klingende " +
                    "Erkennungen dann auf diese Schreibweise zurück.",
                style = MaterialTheme.typography.bodyMedium,
                color = VipTheme.colors.textMuted,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = VipTheme.colors.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                    label = { Text("Neuer Begriff") },
                )
                VipButton(
                    text = "Hinzufügen",
                    icon = Icons.Rounded.Add,
                    onClick = onAdd,
                    enabled = input.trim().length >= 3,
                )
            }
        }

        if (terms.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Spellcheck,
                title = "Noch keine eigenen Begriffe",
                text = "Der eingebaute ÖPNV-Wortschatz (Haltestellen, Tarifbegriffe) ist trotzdem aktiv.",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 64.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                item {
                    VipCard(contentPadding = PaddingValues(0.dp)) {
                        terms.forEachIndexed { index, term ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = VipTheme.colors.outline,
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = term,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                IconButton(onClick = { onRemove(term) }) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
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
}
