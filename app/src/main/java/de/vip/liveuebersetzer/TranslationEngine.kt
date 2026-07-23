package de.vip.liveuebersetzer

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

/**
 * Dünner Wrapper um ML Kit Translate (GA, `com.google.mlkit:translate`).
 *
 * Übersetzt ausschließlich on-device: Nach dem einmaligen Download des
 * Sprachmodellpaars läuft die eigentliche Übersetzung ohne jede
 * Netzwerkanfrage und ohne dass Text das Gerät verlässt. Ein Cloud-Fallback
 * ist bewusst nicht vorgesehen (siehe README).
 */
object TranslationEngine {

    /** Erstellt einen [Translator] für das übergebene Sprachpaar (interne [Language]-Codes). */
    fun createTranslator(source: Language, target: Language): Translator {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(source.mlKitLanguage)
            .setTargetLanguage(target.mlKitLanguage)
            .build()
        return Translation.getClient(options)
    }

    /**
     * Lädt das Sprachmodell bei Bedarf herunter. Wird ohne WLAN-Zwang
     * ausgeführt, da es sich um kleine, einmalige Modell-Downloads handelt.
     */
    suspend fun ensureModelDownloaded(translator: Translator) {
        val conditions = DownloadConditions.Builder().build()
        translator.downloadModelIfNeeded(conditions).await()
    }

    /** Übersetzt [text] mit dem übergebenen, bereits vorbereiteten [translator]. */
    suspend fun translate(translator: Translator, text: String): String =
        translator.translate(text).await()

    /** Gibt die vom Translator gehaltenen nativen Ressourcen frei. */
    fun close(translator: Translator) {
        translator.close()
    }
}
