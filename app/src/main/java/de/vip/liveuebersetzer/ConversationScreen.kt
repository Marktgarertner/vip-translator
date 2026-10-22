package de.vip.liveuebersetzer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import de.vip.liveuebersetzer.ui.components.BannerKind
import de.vip.liveuebersetzer.ui.components.EmptyState
import de.vip.liveuebersetzer.ui.components.FloatingPillButton
import de.vip.liveuebersetzer.ui.components.HeaderIconButton
import de.vip.liveuebersetzer.ui.components.ListTopFade
import de.vip.liveuebersetzer.ui.components.MessageBubble
import de.vip.liveuebersetzer.ui.components.MicButton
import de.vip.liveuebersetzer.ui.components.PaneHeader
import de.vip.liveuebersetzer.ui.components.StatusBanner
import de.vip.liveuebersetzer.ui.components.VipTextButton

/**
 * Ein abgeschlossener Gesprächsbeitrag. Der Verlauf lebt bewusst nur im
 * Arbeitsspeicher (Datenschutz: nichts wird persistiert) und bleibt beim
 * Sprachwechsel vollständig erhalten - jeder Eintrag trägt sein eigenes
 * Sprachpaar.
 */
data class ConversationEntry(
    val id: Long,
    /** Von der Kundenseite gesprochen (sonst Mitarbeiterseite bzw. Schnellbaustein). */
    val fromCustomer: Boolean,
    val sourceLanguage: Language,
    val targetLanguage: Language,
    val originalText: String,
    val translatedText: String,
    /**
     * Ursprünglich erkannter Wortlaut, falls [TransitGlossary] etwas korrigiert
     * hat - sonst `null`. Wird auf der Mitarbeiterseite als Hinweis angezeigt,
     * damit eine Fehlkorrektur auffällt und nicht unbemerkt bleibt.
     */
    val recognizedText: String? = null,
) {
    /** Text dieses Beitrags, wie ihn die Bildschirmhälfte mit Sprache [pane] zeigt. */
    fun textFor(pane: Language): String = when (pane.code) {
        sourceLanguage.code -> originalText
        else -> translatedText
    }

    /** Das jeweils andere Gegenstück zu [textFor]. */
    fun counterpartFor(pane: Language): String = when (pane.code) {
        sourceLanguage.code -> translatedText
        else -> originalText
    }

    /**
     * Sprache, in der [textFor] tatsächlich geschrieben ist. Nicht zwingend
     * [pane]: Wurde die Kundensprache nach diesem Beitrag gewechselt, zeigt
     * die Kundenseite die Übersetzung in der *damaligen* Zielsprache - und
     * genau mit deren Stimme muss sie vorgelesen werden, nicht mit der jetzt
     * gewählten.
     */
    fun languageFor(pane: Language): Language = when (pane.code) {
        sourceLanguage.code -> sourceLanguage
        else -> targetLanguage
    }
}

/** Welche Seite gerade aufnimmt. */
enum class ListeningSide { NONE, STAFF, CUSTOMER }

/**
 * Alles, was der Gesprächsbildschirm zum Zeichnen braucht - ohne Engines,
 * ohne Kontext. Dadurch lässt sich der Bildschirm in Screenshot-Tests mit
 * erfundenen Daten rendern, und die Logik in [MainActivity] bleibt getrennt
 * von der Darstellung.
 */
data class ConversationUiState(
    val staffLanguage: Language,
    val customerLanguage: Language,
    /** Neuester Beitrag zuerst. */
    val entries: List<ConversationEntry>,
    val listening: ListeningSide = ListeningSide.NONE,
    val preparing: Boolean = false,
    val preparingMegabytes: Long? = null,
    val liveTranscript: String = "",
    val errorMessage: String? = null,
    val ttsSettingsHint: Boolean = false,
    /** Warum die Kundenseite keine Sprechtaste hat - `null`, wenn sie eine hat. */
    val customerMicReason: String? = null,
    val staffMicReason: String? = null,
    val speechOutputEnabled: Boolean = true,
)

/** Alle Reaktionen des Gesprächsbildschirms, gebündelt. */
class ConversationActions(
    val onStaffLanguage: (Language) -> Unit = {},
    val onCustomerLanguage: (Language) -> Unit = {},
    val onLogoClick: () -> Unit = {},
    val onStaffMic: () -> Unit = {},
    val onCustomerMic: () -> Unit = {},
    val onSpeakForCustomer: (ConversationEntry) -> Unit = {},
    val onSpeakForStaff: (ConversationEntry) -> Unit = {},
    val onToggleSpeechOutput: () -> Unit = {},
    val onOpenPhrases: () -> Unit = {},
    val onNextCustomer: () -> Unit = {},
    val onOpenTtsSettings: () -> Unit = {},
)

/**
 * Der Splitscreen: oben die um 180° gedrehte Kundenseite, unten die
 * Mitarbeiterseite - beide identisch aufgebaut (Kopfleiste mit Logo und
 * Sprachauswahl, Verlauf, Sprechtaste unten links), damit jede Seite ihre
 * Taste an derselben Stelle findet.
 */
@Composable
fun ConversationScreen(state: ConversationUiState, actions: ConversationActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        CustomerPane(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .rotate(180f),
            state = state,
            actions = actions,
        )
        // Die "Falz" zwischen den Hälften.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
        StaffPane(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.25f),
            state = state,
            actions = actions,
        )
    }
}

@Composable
private fun CustomerPane(
    modifier: Modifier,
    state: ConversationUiState,
    actions: ConversationActions,
) {
    val language = state.customerLanguage
    val listState = rememberLazyListState()
    LaunchedEffect(state.entries.size) {
        if (state.entries.isNotEmpty()) listState.animateScrollToItem(0)
    }
    val micVisible = state.customerMicReason == null
    val recording = state.listening == ListeningSide.CUSTOMER

    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            PaneHeader(
                selected = language,
                labelFor = { it.nativeName },
                onSelected = actions.onCustomerLanguage,
                onLogoClick = actions.onLogoClick,
            )
            Text(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
                text = language.greeting,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.headlineSmall,
            )
            if (recording) {
                StatusBanner(
                    kind = BannerKind.LIVE,
                    text = state.liveTranscript.ifBlank { "…" },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = if (micVisible) 120.dp else 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.entries, key = { it.id }) { entry ->
                        MessageBubble(
                            primary = entry.textFor(language),
                            secondary = entry.counterpartFor(language),
                            mine = entry.fromCustomer,
                            onSpeak = { actions.onSpeakForCustomer(entry) },
                            primaryStyle = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                ListTopFade(modifier = Modifier.align(Alignment.TopCenter))
            }
        }

        // Sprechtaste der Kundenseite: unten links (aus Kundensicht),
        // beschriftet in der Kundensprache. Ohne Live-Modus ausgeblendet -
        // die Mitarbeiterseite zeigt dann in Klartext, warum.
        if (micVisible) {
            MicButton(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 12.dp),
                isRecording = recording,
                isPreparing = state.preparing && recording,
                enabled = true,
                label = language.tapToSpeak,
                contentDescription = language.tapToSpeak,
                onClick = actions.onCustomerMic,
            )
        }
    }
}

@Composable
private fun StaffPane(
    modifier: Modifier,
    state: ConversationUiState,
    actions: ConversationActions,
) {
    val language = state.staffLanguage
    val listState = rememberLazyListState()
    LaunchedEffect(state.entries.size) {
        if (state.entries.isNotEmpty()) listState.animateScrollToItem(0)
    }
    val recording = state.listening == ListeningSide.STAFF

    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            PaneHeader(
                selected = language,
                labelFor = { it.displayName },
                onSelected = actions.onStaffLanguage,
                onLogoClick = actions.onLogoClick,
                actions = {
                    HeaderIconButton(
                        icon = if (state.speechOutputEnabled) {
                            Icons.AutoMirrored.Rounded.VolumeUp
                        } else {
                            Icons.AutoMirrored.Rounded.VolumeOff
                        },
                        contentDescription = if (state.speechOutputEnabled) {
                            "Sprachausgabe ausschalten"
                        } else {
                            "Sprachausgabe einschalten"
                        },
                        active = state.speechOutputEnabled,
                        onClick = actions.onToggleSpeechOutput,
                    )
                    HeaderIconButton(
                        icon = Icons.AutoMirrored.Rounded.List,
                        contentDescription = "Schnellbausteine öffnen",
                        onClick = actions.onOpenPhrases,
                    )
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.errorMessage?.let { message ->
                    StatusBanner(
                        kind = BannerKind.ERROR,
                        text = message,
                        action = if (state.ttsSettingsHint) {
                            { VipTextButton("Einstellungen öffnen", actions.onOpenTtsSettings) }
                        } else {
                            null
                        },
                    )
                }
                listOfNotNull(
                    state.customerMicReason,
                    state.staffMicReason.takeIf { language.code != state.customerLanguage.code },
                ).forEach { reason ->
                    StatusBanner(kind = BannerKind.WARN, text = reason)
                }
                if (state.listening != ListeningSide.NONE) {
                    StatusBanner(
                        kind = BannerKind.LIVE,
                        text = when {
                            state.preparing && state.preparingMegabytes != null ->
                                "Erkennungsmodell wird einmalig geladen … ${state.preparingMegabytes} MB"
                            state.preparing -> "Spracherkennung wird vorbereitet …"
                            recording -> "Sprechen Sie jetzt - stoppt nach dem Satz automatisch"
                            else -> "Kunde spricht gerade …"
                        },
                        detail = state.liveTranscript.takeIf { recording && it.isNotBlank() },
                    )
                }
            }

            if (state.entries.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.Mic,
                    title = "Bereit für das Gespräch",
                    text = "Grünes Mikrofon unten links antippen und sprechen. " +
                        "Der Kunde hat auf seiner Seite eine eigene Sprechtaste.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 48.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        reverseLayout = true,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.entries, key = { it.id }) { entry ->
                            MessageBubble(
                                caption = "${entry.sourceLanguage.displayName} → ${entry.targetLanguage.displayName}",
                                primary = entry.textFor(language),
                                secondary = entry.counterpartFor(language),
                                mine = !entry.fromCustomer,
                                // Nur wenn der Fachwortschatz etwas geändert hat:
                                // Original zeigen, damit eine Fehlkorrektur auffällt.
                                note = entry.recognizedText?.let { "wörtlich erkannt: $it" },
                                onSpeak = { actions.onSpeakForStaff(entry) },
                            )
                        }
                    }
                    ListTopFade(modifier = Modifier.align(Alignment.TopCenter))
                }
            }
        }

        // Sprechtaste der Mitarbeiterseite: unten links, wie auf der
        // Kundenseite - beide Seiten bedienen sich identisch.
        MicButton(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 12.dp),
            isRecording = recording,
            isPreparing = state.preparing && recording,
            enabled = state.staffMicReason == null && state.listening != ListeningSide.CUSTOMER,
            label = "Sprechen",
            contentDescription = "Zum Sprechen antippen (${language.displayName})",
            onClick = actions.onStaffMic,
        )

        // Ersetzt den früheren Papierkorb: räumt alles für den nächsten
        // Kunden ab, nicht nur den Verlauf. Unten rechts, auf Höhe der
        // Sprechtaste - beide Hauptaktionen liegen so in Daumenreichweite.
        FloatingPillButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 54.dp),
            text = "Nächster Kunde",
            icon = Icons.Rounded.Refresh,
            onClick = actions.onNextCustomer,
        )
    }
}
