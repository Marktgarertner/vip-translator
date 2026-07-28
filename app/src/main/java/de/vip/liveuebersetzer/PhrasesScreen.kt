package de.vip.liveuebersetzer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Menü "Schnellbausteine": häufige Schaltersätze auf Tastendruck.
 *
 * Ein Tipp auf einen Satz übersetzt ihn in die aktuell gewählte Kundensprache,
 * hängt ihn an den Gesprächsverlauf an und liest ihn vor - danach schließt
 * sich der Bildschirm wieder, damit man sofort weiterarbeiten kann.
 *
 * Die Satzliste ist eine anzupassende Startauswahl, siehe [StaffPhrases].
 */
@Composable
fun PhrasesScreen(
    customerLanguage: Language,
    onPhraseSelected: (String) -> Unit,
    onClose: () -> Unit,
) {
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
                text = "Schnellbausteine",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Text(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = "Antippen übersetzt den Satz nach ${customerLanguage.displayName}, " +
                "liest ihn vor und trägt ihn in den Verlauf ein.",
            color = MaterialTheme.colorScheme.tertiary,
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StaffPhrases.categories.forEach { category ->
                item(key = "kategorie-$category") {
                    Text(
                        text = category,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                items(
                    StaffPhrases.all.filter { it.category == category },
                    key = { it.text },
                ) { phrase ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPhraseSelected(phrase.text) },
                    ) {
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            text = phrase.text,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}
