package de.vip.liveuebersetzer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.vip.liveuebersetzer.ui.components.SectionLabel
import de.vip.liveuebersetzer.ui.components.VipCard
import de.vip.liveuebersetzer.ui.components.VipTopBar
import de.vip.liveuebersetzer.ui.theme.VipTheme

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
    ) {
        VipTopBar(title = "Schnellbausteine", onBack = onClose, backDescription = "Zurück zum Gespräch")

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    modifier = Modifier.padding(bottom = 4.dp),
                    text = "Antippen übersetzt den Satz nach ${customerLanguage.displayName}, " +
                        "liest ihn vor und trägt ihn in den Verlauf ein.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VipTheme.colors.textMuted,
                )
            }
            StaffPhrases.categories.forEach { category ->
                item(key = "kategorie-$category") {
                    SectionLabel(text = category, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                }
                item(key = "karte-$category") {
                    val phrases = StaffPhrases.all.filter { it.category == category }
                    VipCard(contentPadding = PaddingValues(0.dp)) {
                        phrases.forEachIndexed { index, phrase ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = VipTheme.colors.outline,
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPhraseSelected(phrase.text) }
                                    .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = phrase.text,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Icon(
                                    Icons.AutoMirrored.Rounded.VolumeUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(start = 12.dp)
                                        .size(22.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
