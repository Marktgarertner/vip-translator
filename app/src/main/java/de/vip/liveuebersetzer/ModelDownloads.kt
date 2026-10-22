package de.vip.liveuebersetzer

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Status eines Sprachpakets (Übersetzung, ML-Kit-Erkennung oder Vosk-Modell). */
enum class ModelStatus {
    PRUEFEN,
    FEHLT,
    LAEDT,
    GELADEN,
    FEHLER,

    /** Auf diesem Gerät nicht möglich (z. B. ML Kit unter Android 12, AICore fehlt). */
    NICHT_VERFUEGBAR,
}

/**
 * App-weiter Zustand der Sprachpaket-Downloads.
 *
 * Warum ein Objekt außerhalb der Compose-Hierarchie: Vorher liefen die
 * Downloads im `rememberCoroutineScope()` des Sprachpakete-Menüs. Wer das
 * Menü mit "Zurück" verließ, brach damit still einen laufenden Download ab -
 * beim arabischen Vosk-Modell sind das 318 MB, die dann nochmal von vorn
 * geladen werden mussten, ohne dass irgendwo stand, warum. Jetzt laufen
 * Downloads in einem eigenen Scope weiter, und das Menü zeigt beim nächsten
 * Öffnen den aktuellen Stand, weil der Zustand hier lebt.
 *
 * Alle Maps sind Snapshot-State: Composables, die sie lesen, werden bei
 * Änderungen automatisch neu gezeichnet. Geschrieben wird ausschließlich auf
 * dem Main-Thread (Scope mit `Dispatchers.Main.immediate`); die
 * Fortschritts-Callbacks aus dem IO-Thread werden auf ganze Prozent gerundet,
 * damit nicht jeder 64-KB-Block eine Recomposition auslöst.
 */
object ModelDownloads {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Übersetzungsmodell (ML Kit Translate) pro Sprachcode. */
    val translation: SnapshotStateMap<String, ModelStatus> = mutableStateMapOf()

    /** ML-Kit-Erkennungsmodell pro Sprachcode - nur für Sprachen mit [Language.mlKitLiveSpeech]. */
    val mlKitSpeech: SnapshotStateMap<String, ModelStatus> = mutableStateMapOf()

    /** Bisher geladene Bytes des ML-Kit-Erkennungsmodells (ML Kit nennt keine Gesamtgröße). */
    val mlKitSpeechBytes: SnapshotStateMap<String, Long> = mutableStateMapOf()

    /** Vosk-Modell pro Sprachcode - nur für Sprachen mit [VoskSpeechEngine.isSupported]. */
    val vosk: SnapshotStateMap<String, ModelStatus> = mutableStateMapOf()

    /** Fortschritt des Vosk-Downloads in Prozent, falls der Server die Größe nennt. */
    val voskProgress: SnapshotStateMap<String, Int> = mutableStateMapOf()

    /**
     * Sprachcodes, für die gerade ein Download läuft (Schutz vor Doppelstart).
     * Ebenfalls Snapshot-State, damit "Lädt …" am Button ohne Umweg über die
     * Statusmaps erscheint und wieder verschwindet.
     */
    private val running: SnapshotStateMap<String, Boolean> = mutableStateMapOf()

    /**
     * Liest den Ist-Zustand aller Pakete neu ein (Öffnen von Einrichtung bzw.
     * Sprachpakete). Pakete, die gerade geladen werden, bleiben unangetastet.
     */
    suspend fun refresh(context: Context) {
        val app = context.applicationContext
        LanguageCatalog.all.forEach { language ->
            val code = language.code
            if (isRunning(language)) return@forEach
            if (translation[code] != ModelStatus.GELADEN) translation[code] = ModelStatus.PRUEFEN
            translation[code] = runCatching { TranslationEngine.isModelDownloaded(language) }
                .fold(
                    { downloaded -> if (downloaded) ModelStatus.GELADEN else ModelStatus.FEHLT },
                    { ModelStatus.FEHLT },
                )
            if (language.mlKitLiveSpeech) {
                mlKitSpeech[code] = SpeechEngine.mlKitModelStatus(code)
            }
            if (VoskSpeechEngine.isSupported(code)) {
                vosk[code] = if (VoskSpeechEngine.isModelReady(app, code)) ModelStatus.GELADEN else ModelStatus.FEHLT
            }
        }
    }

    /**
     * Ob die Live-Erkennung für [language] wirklich benutzbar ist: Engine
     * vorhanden UND - bei ML Kit - das Erkennungsmodell laut letzter Prüfung
     * geladen. Grundlage für "Es fehlt noch: Spracheingabe" im Assistenten.
     */
    fun isSpeechReady(context: Context, language: Language): Boolean =
        when (SpeechEngine.engineFor(context, language.code)) {
            LiveEngine.VOSK -> true
            LiveEngine.MLKIT -> mlKitSpeech[language.code] == ModelStatus.GELADEN
            LiveEngine.NONE -> false
        }

    /** Ob gerade irgendein Download für [language] läuft. */
    fun isRunning(language: Language): Boolean = running[language.code] == true

    /** Markiert [code] als laufend; `false`, wenn dafür schon ein Download läuft. */
    private fun claim(code: String): Boolean {
        if (running[code] == true) return false
        running[code] = true
        return true
    }

    /**
     * Ob "Laden" für [language] etwas zu tun hätte: Übersetzung fehlt, oder
     * das Erkennungsmodell des zuständigen Weges fehlt bzw. ist fehlgeschlagen.
     */
    fun needsDownload(context: Context, language: Language): Boolean {
        val code = language.code
        if (translation[code].needsLoad()) return true
        return when (SpeechEngine.engineFor(context, code)) {
            LiveEngine.VOSK -> false
            LiveEngine.MLKIT -> mlKitSpeech[code].needsLoad()
            // Keine Engine aktiv: Vosk-Sprache ohne Modell (oder ML Kit unter
            // Android 12 - dann hilft Laden nicht, needsLoad() ist dafür false).
            LiveEngine.NONE -> VoskSpeechEngine.isSupported(code) && vosk[code].needsLoad()
        }
    }

    private fun ModelStatus?.needsLoad(): Boolean =
        this == ModelStatus.FEHLT || this == ModelStatus.FEHLER

    /**
     * Lädt alles, was [language] für den Schalter braucht: Übersetzungsmodell
     * und das Erkennungsmodell des zuständigen Weges (ML Kit, wo der
     * Basic-Modus die Sprache listet und kein Vosk-Modell geladen ist; sonst
     * Vosk). Beide Teile werden getrennt gemeldet - ein Fehler beim
     * Erkennungsmodell macht eine fertige Übersetzung nicht mehr rückgängig
     * (vorher hieß es dann "Übersetzung: Download fehlgeschlagen", obwohl nur
     * die Erkennung betroffen war).
     */
    fun start(context: Context, language: Language) {
        val app = context.applicationContext
        val code = language.code
        if (!claim(code)) return
        scope.launch {
            try {
                if (translation[code] != ModelStatus.GELADEN) {
                    translation[code] = ModelStatus.LAEDT
                    translation[code] = runCatching { TranslationEngine.downloadModel(language) }
                        .fold({ ModelStatus.GELADEN }, { rethrowCancellation(it); ModelStatus.FEHLER })
                }
                when (SpeechEngine.engineFor(app, code)) {
                    LiveEngine.MLKIT -> if (mlKitSpeech[code] != ModelStatus.GELADEN) downloadMlKit(code)
                    LiveEngine.NONE -> if (VoskSpeechEngine.isSupported(code)) downloadVosk(app, code)
                    LiveEngine.VOSK -> Unit
                }
            } finally {
                running.remove(code)
            }
        }
    }

    /**
     * Lädt nur das Vosk-Modell für [language] - für die zweigleisigen Sprachen
     * (Chinesisch, Japanisch, Koreanisch, Hindi) als Alternative zu ML Kit,
     * etwa wenn der Basic-Modus auf dem Gerät nicht verfügbar ist oder
     * schlecht erkennt. Ein geladenes Vosk-Modell hat danach Vorrang.
     */
    fun startVosk(context: Context, language: Language) {
        val app = context.applicationContext
        val code = language.code
        if (!VoskSpeechEngine.isSupported(code) || !claim(code)) return
        scope.launch {
            try {
                downloadVosk(app, code)
            } finally {
                running.remove(code)
            }
        }
    }

    private suspend fun downloadMlKit(code: String) {
        mlKitSpeech[code] = ModelStatus.LAEDT
        mlKitSpeechBytes.remove(code)
        mlKitSpeech[code] = try {
            SpeechEngine.prepareMlKitModel(code) { bytes ->
                // Nur in 1-MB-Schritten melden, siehe Objekt-Kdoc.
                val rounded = bytes / (1024 * 1024) * (1024 * 1024)
                if (mlKitSpeechBytes[code] != rounded) mlKitSpeechBytes[code] = rounded
            }
            ModelStatus.GELADEN
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            // Von SpeechEngine.ensureModelDownloaded: UNAVAILABLE (Gerät kann
            // die Sprache nicht) oder DownloadFailed (Netz).
            if (e.cause != null) ModelStatus.FEHLER else ModelStatus.NICHT_VERFUEGBAR
        } catch (e: Exception) {
            ModelStatus.FEHLER
        }
    }

    private suspend fun downloadVosk(context: Context, code: String) {
        vosk[code] = ModelStatus.LAEDT
        voskProgress.remove(code)
        vosk[code] = try {
            VoskSpeechEngine.downloadAndUnpack(context, code) { downloaded, total ->
                if (total != null && total > 0) {
                    val percent = (downloaded * 100 / total).toInt().coerceIn(0, 100)
                    if (voskProgress[code] != percent) voskProgress[code] = percent
                }
            }
            ModelStatus.GELADEN
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ModelStatus.FEHLER
        }
    }

    private fun rethrowCancellation(throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
    }
}
