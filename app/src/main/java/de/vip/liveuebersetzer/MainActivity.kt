package de.vip.liveuebersetzer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.mlkit.genai.speechrecognition.SpeechRecognizer
import de.vip.liveuebersetzer.ui.theme.VipLiveUebersetzerTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Status eines Sprachpakets im "Sprachpakete"-Menü. */
private enum class ModelStatus { PRUEFEN, FEHLT, LAEDT, GELADEN, FEHLER }

/**
 * Ein abgeschlossener Gesprächsbeitrag. Der Verlauf lebt bewusst nur im
 * Arbeitsspeicher (Datenschutz: nichts wird persistiert) und bleibt beim
 * Sprachwechsel vollständig erhalten - jeder Eintrag trägt sein eigenes
 * Sprachpaar.
 */
private data class ConversationEntry(
    val id: Long,
    val sourceLanguage: Language,
    val targetLanguage: Language,
    val originalText: String,
    val translatedText: String,
) {
    /** Text dieses Beitrags in der Sprache der jeweiligen Bildschirmhälfte. */
    fun textFor(pane: Language): String = when (pane.code) {
        sourceLanguage.code -> originalText
        else -> translatedText
    }

    /** Das jeweils andere Gegenstück zu [textFor]. */
    fun counterpartFor(pane: Language): String = when (pane.code) {
        sourceLanguage.code -> translatedText
        else -> originalText
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VipLiveUebersetzerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LiveUebersetzerScreen()
                }
            }
        }
    }
}

@Composable
private fun LiveUebersetzerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var staffLanguage by remember { mutableStateOf(LanguageCatalog.defaultSource) }
    var customerLanguage by remember { mutableStateOf(LanguageCatalog.defaultTarget) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    val conversation = remember { mutableStateListOf<ConversationEntry>() }
    var nextEntryId by remember { mutableStateOf(0L) }

    var liveTranscript by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    // Welche Seite gerade aufnimmt: Wer seine Sprechtaste drückt, bestimmt die
    // Übersetzungsrichtung - es gibt bewusst keinen Richtungs-Umschalter und
    // keine Texteingabe mehr, die App konzentriert sich aufs Sprechen.
    var recordingFromCustomer by remember { mutableStateOf(false) }
    var isPreparingSpeechModel by remember { mutableStateOf(false) }
    var recognizerJob by remember { mutableStateOf<Job?>(null) }
    var activeRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    val speechOutput = remember { SpeechOutput(context) }
    var speechOutputEnabled by remember { mutableStateOf(true) }
    var showModelManager by remember { mutableStateOf(false) }

    // Auto-Scroll zum neuesten Beitrag (Index 0, liegt bei reverseLayout "unten").
    val staffListState = rememberLazyListState()
    LaunchedEffect(conversation.size) {
        if (conversation.isNotEmpty()) staffListState.animateScrollToItem(0)
    }

    fun addEntry(from: Language, to: Language, original: String, translated: String) {
        // Neuester Eintrag an Index 0; die Listen rendern mit reverseLayout,
        // sodass er auf beiden Bildschirmhälften wie in einem Chat "unten" steht.
        conversation.add(0, ConversationEntry(nextEntryId++, from, to, original, translated))
        // Nie sprechen, solange das Mikrofon offen ist - sonst erkennt die
        // Erkennung die eigene Ausgabe als Eingabe (Rückkopplungsschleife).
        if (speechOutputEnabled && !isListening) speechOutput.speak(translated, to)
    }

    fun speakOrExplain(text: String, language: Language) {
        if (!speechOutput.speak(text, language)) {
            errorMessage = "Sprachausgabe für ${language.displayName} ist auf diesem Gerät nicht verfügbar."
        }
    }

    fun stopLive() {
        recognizerJob?.cancel()
        recognizerJob = null
        activeRecognizer?.let { recognizer ->
            SpeechEngine.stopListening(recognizer)
            SpeechEngine.release(recognizer)
        }
        activeRecognizer = null
        isListening = false
        isPreparingSpeechModel = false
    }

    fun startLive(fromCustomer: Boolean) {
        // Sprachpaar zum Startzeitpunkt festhalten: Die gedrückte Sprechtaste
        // bestimmt die Richtung dieses Beitrags.
        val from = if (fromCustomer) customerLanguage else staffLanguage
        val to = if (fromCustomer) staffLanguage else customerLanguage
        if (!SpeechEngine.isLiveSupported(from.code) || isListening) return
        errorMessage = null
        // Laufende Sprachausgabe abbrechen, bevor das Mikrofon aufgeht.
        speechOutput.stop()
        liveTranscript = ""
        recordingFromCustomer = fromCustomer
        val recognizer = SpeechEngine.createRecognizer(from.code)
        activeRecognizer = recognizer
        isListening = true
        recognizerJob = scope.launch {
            try {
                isPreparingSpeechModel = true
                SpeechEngine.ensureModelDownloaded(recognizer)
                isPreparingSpeechModel = false
                val listenJob = SpeechEngine.listen(
                    recognizer = recognizer,
                    scope = this,
                    onPartial = { text -> liveTranscript = text },
                    onFinal = { text ->
                        liveTranscript = text
                        // Tap-to-Talk: Nach dem ersten fertigen Satz stoppt die
                        // Aufnahme automatisch, damit die anschließende
                        // Sprachausgabe nicht wieder als Eingabe erkannt wird
                        // (Rückkopplungsschleife). Für den nächsten Satz die
                        // Sprechtaste einfach erneut antippen.
                        stopLive()
                        scope.launch {
                            runCatching {
                                TranslationEngine.translate(from, to, text)
                            }.onSuccess { addEntry(from, to, text, it) }
                                .onFailure { errorMessage = it.message ?: "Übersetzung fehlgeschlagen." }
                        }
                    },
                    onError = { throwable ->
                        errorMessage = throwable.message ?: "Fehler bei der Live-Spracherkennung."
                    },
                )
                listenJob.join()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.message ?: "Fehler bei der Live-Spracherkennung."
            } finally {
                isPreparingSpeechModel = false
                isListening = false
            }
        }
    }

    var pendingFromCustomer by remember { mutableStateOf(false) }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startLive(pendingFromCustomer)
        } else {
            errorMessage = "Ohne Mikrofonzugriff ist der Live-Modus nicht möglich."
        }
    }

    fun onMicButtonClick(fromCustomer: Boolean) {
        if (isListening) {
            // Nur die Seite, die gerade aufnimmt, kann die Aufnahme stoppen.
            if (recordingFromCustomer == fromCustomer) stopLive()
            return
        }
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            startLive(fromCustomer)
        } else {
            pendingFromCustomer = fromCustomer
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Jeder Sprachwechsel stoppt eine laufende Aufnahme, denn der Recognizer
    // ist fest auf seine Startsprache gebunden. Der Verlauf bleibt erhalten.
    LaunchedEffect(staffLanguage, customerLanguage) {
        if (isListening) stopLive()
    }

    DisposableEffect(Unit) {
        onDispose {
            stopLive()
            speechOutput.shutdown()
            TranslationEngine.closeAll()
        }
    }

    if (showModelManager) {
        ModelManagerScreen(onClose = { showModelManager = false })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // ===== Kundenseite: um 180° gedreht, damit das Gegenüber alles in =====
        // ===== seiner Leserichtung sieht - mit eigener Sprach- und Sprechtaste. =====
        CustomerPane(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .rotate(180f),
            language = customerLanguage,
            onLanguageSelected = { customerLanguage = it },
            onLogoClick = { showModelManager = true },
            entries = conversation,
            pendingTranscript = liveTranscript.takeIf { isListening && recordingFromCustomer && it.isNotBlank() },
            micVisible = SpeechEngine.isLiveSupported(customerLanguage.code),
            isRecording = isListening && recordingFromCustomer,
            isPreparing = isPreparingSpeechModel && recordingFromCustomer,
            onMicClick = { onMicButtonClick(true) },
            onSpeakClick = { entry -> speakOrExplain(entry.textFor(customerLanguage), customerLanguage) },
        )

        // Trennlinie in ViP-Grün zwischen den beiden Hälften.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(MaterialTheme.colorScheme.primary),
        )

        // ===== Mitarbeiterseite: eigene Sprach- und Sprechtaste (Ecke). =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.3f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.vip_logo),
                        contentDescription = "Einstellungen (Sprachpakete)",
                        modifier = Modifier
                            .size(28.dp)
                            .clickable { showModelManager = true },
                    )
                    LanguagePickerRow(
                        modifier = Modifier.weight(1f),
                        selected = staffLanguage,
                        labelFor = { it.displayName },
                        onSelected = { staffLanguage = it },
                    )
                    IconButton(onClick = { speechOutputEnabled = !speechOutputEnabled }) {
                        Icon(
                            imageVector = if (speechOutputEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                            contentDescription = if (speechOutputEnabled) {
                                "Sprachausgabe ausschalten"
                            } else {
                                "Sprachausgabe einschalten"
                            },
                        )
                    }
                    IconButton(
                        onClick = {
                            conversation.clear()
                            liveTranscript = ""
                            errorMessage = null
                        },
                        enabled = conversation.isNotEmpty(),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Konversation löschen")
                    }
                }

                if (conversation.isEmpty()) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        text = "Noch keine Beiträge. Einfach das Mikrofon unten rechts " +
                            "antippen und sprechen - der Kunde hat auf seiner Seite " +
                            "eine eigene Sprech- und Sprachtaste.",
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        state = staffListState,
                        reverseLayout = true,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(conversation, key = { it.id }) { entry ->
                            StaffEntryCard(
                                entry = entry,
                                staffLanguage = staffLanguage,
                                onSpeakClick = { speakOrExplain(entry.translatedText, entry.targetLanguage) },
                            )
                        }
                    }
                }

                errorMessage?.let { message ->
                    Text(text = message, color = MaterialTheme.colorScheme.error)
                }

                if (isListening) {
                    Text(
                        text = if (recordingFromCustomer) {
                            "Kunde spricht gerade …"
                        } else {
                            "Sprechen Sie jetzt - stoppt nach dem Satz automatisch"
                        },
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (!recordingFromCustomer && liveTranscript.isNotBlank()) {
                        Text(text = liveTranscript)
                    }
                }
                if (isPreparingSpeechModel && !recordingFromCustomer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Text(" Spracherkennungsmodell wird vorbereitet …")
                    }
                }
            }

            // Sprechtaste der Mitarbeiterseite: bewusst in die Ecke statt in
            // eine volle Zeile, damit oben mehr Platz für den Gesprächsverlauf
            // bleibt.
            IconButton(
                onClick = { onMicButtonClick(false) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                enabled = SpeechEngine.isLiveSupported(staffLanguage.code) &&
                    !(isListening && recordingFromCustomer),
            ) {
                Icon(
                    imageVector = if (isListening && !recordingFromCustomer) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = "Zum Sprechen antippen (${staffLanguage.displayName})",
                    modifier = Modifier.size(48.dp),
                    tint = if (isListening && !recordingFromCustomer) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        }
    }
}

@Composable
private fun CustomerPane(
    modifier: Modifier = Modifier,
    language: Language,
    onLanguageSelected: (Language) -> Unit,
    onLogoClick: () -> Unit,
    entries: List<ConversationEntry>,
    pendingTranscript: String?,
    micVisible: Boolean,
    isRecording: Boolean,
    isPreparing: Boolean,
    onMicClick: () -> Unit,
    onSpeakClick: (ConversationEntry) -> Unit,
) {
    // Auto-Scroll zum neuesten Beitrag auch auf der Kundenseite.
    val listState = rememberLazyListState()
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.vip_logo),
                contentDescription = "Einstellungen (Sprachpakete)",
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = onLogoClick),
            )
            LanguagePickerRow(
                modifier = Modifier.weight(1f),
                selected = language,
                labelFor = { it.nativeName },
                onSelected = onLanguageSelected,
            )
        }

        Text(
            text = language.greeting,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge,
        )

        pendingTranscript?.let { transcript ->
            Text(text = transcript, color = MaterialTheme.colorScheme.tertiary)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            state = listState,
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(entries, key = { it.id }) { entry ->
                CustomerEntryCard(
                    entry = entry,
                    language = language,
                    onSpeakClick = { onSpeakClick(entry) },
                )
            }
        }

        // Sprechtaste der Kundenseite, beschriftet in der Kundensprache. Für
        // Sprachen ohne Live-Modus (Ukrainisch, Arabisch) ausgeblendet - siehe
        // README ("Offene Punkte"): ohne Texteingabe ist die Kommunikation für
        // diese beiden Sprachen aktuell eine Einbahnstraße.
        if (micVisible) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (isPreparing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onMicClick) {
                    Icon(
                        imageVector = if (isRecording) Icons.Filled.MicOff else Icons.Filled.Mic,
                        contentDescription = language.tapToSpeak,
                        modifier = Modifier.size(56.dp),
                        tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
                Text(text = language.tapToSpeak, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/**
 * Kompakte Sprachauswahl: Ein Button zeigt die aktuell gewählte Sprache
 * (Beschriftung über [labelFor]); ein Antippen blendet eine horizontal
 * scrollbare Reihe aller 11 Sprachen ein. Bewusst ohne Popup-Menü (das würde
 * in der um 180° gedrehten Kundenhälfte falsch positioniert/orientiert
 * erscheinen) - stattdessen normales Layout, das mit der Elternhälfte
 * mitrotiert.
 */
@Composable
private fun LanguagePickerRow(
    modifier: Modifier = Modifier,
    selected: Language,
    labelFor: (Language) -> String,
    onSelected: (Language) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Button(onClick = { expanded = !expanded }) {
            Text(labelFor(selected))
        }
        if (expanded) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LanguageCatalog.all, key = { it.code }) { language ->
                    FilterChip(
                        selected = language.code == selected.code,
                        onClick = {
                            onSelected(language)
                            expanded = false
                        },
                        label = { Text(labelFor(language)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomerEntryCard(
    entry: ConversationEntry,
    language: Language,
    onSpeakClick: () -> Unit,
) {
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
                    text = entry.textFor(language),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = entry.counterpartFor(language),
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            IconButton(onClick = onSpeakClick) {
                Icon(Icons.Filled.VolumeUp, contentDescription = "Vorlesen")
            }
        }
    }
}

@Composable
private fun StaffEntryCard(
    entry: ConversationEntry,
    staffLanguage: Language,
    onSpeakClick: () -> Unit,
) {
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
                    text = "${entry.sourceLanguage.displayName} → ${entry.targetLanguage.displayName}",
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    text = entry.textFor(staffLanguage),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = entry.counterpartFor(staffLanguage),
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            IconButton(onClick = onSpeakClick) {
                Icon(Icons.Filled.VolumeUp, contentDescription = "Übersetzung erneut vorlesen")
            }
        }
    }
}

/**
 * Vollbild-Menü "Sprachpakete": Übersetzungsmodelle (und Live-Erkennung, wo
 * verfügbar) pro Sprache vorab herunterladen, damit am Schalter keine
 * Wartezeit durch spontane Modell-Downloads entsteht. Erreichbar über einen
 * Klick auf das ViP-Logo (auf beiden Bildschirmhälften).
 */
@Composable
private fun ModelManagerScreen(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val statuses = remember { mutableStateMapOf<String, ModelStatus>() }

    LaunchedEffect(Unit) {
        LanguageCatalog.all.forEach { language ->
            statuses[language.code] = ModelStatus.PRUEFEN
            statuses[language.code] = runCatching { TranslationEngine.isModelDownloaded(language) }
                .fold(
                    { downloaded -> if (downloaded) ModelStatus.GELADEN else ModelStatus.FEHLT },
                    { ModelStatus.FEHLT },
                )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
            }
            Text(
                text = "Sprachpakete",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Text(
            text = "Einmalig mit Internet (am besten WLAN) vorbereiten - danach " +
                "übersetzt und spricht die App komplett offline, ohne Wartezeit " +
                "beim Kunden. Empfehlung: alle häufig gebrauchten Sprachen vorab laden.",
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(LanguageCatalog.all, key = { it.code }) { language ->
                val status = statuses[language.code] ?: ModelStatus.PRUEFEN
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(text = language.displayName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = when (status) {
                                    ModelStatus.PRUEFEN -> "Prüfe …"
                                    ModelStatus.FEHLT -> "Noch nicht geladen"
                                    ModelStatus.LAEDT -> "Wird heruntergeladen …"
                                    ModelStatus.GELADEN ->
                                        if (language.liveSpeechSupported) {
                                            "Bereit (inkl. Live-Erkennung)"
                                        } else {
                                            "Bereit (nur Übersetzung, kein Live-Mikro)"
                                        }
                                    ModelStatus.FEHLER -> "Download fehlgeschlagen - Internetverbindung prüfen"
                                },
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        Button(
                            onClick = {
                                statuses[language.code] = ModelStatus.LAEDT
                                scope.launch {
                                    runCatching {
                                        TranslationEngine.downloadModel(language)
                                        SpeechEngine.prepareModel(language.code)
                                    }.onSuccess { statuses[language.code] = ModelStatus.GELADEN }
                                        .onFailure { statuses[language.code] = ModelStatus.FEHLER }
                                }
                            },
                            enabled = status == ModelStatus.FEHLT || status == ModelStatus.FEHLER,
                        ) {
                            Text(
                                when (status) {
                                    ModelStatus.GELADEN -> "Geladen ✓"
                                    ModelStatus.LAEDT -> "Lädt …"
                                    else -> "Laden"
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
