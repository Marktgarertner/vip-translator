package de.vip.liveuebersetzer

import android.util.LruCache
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
 *
 * Translator werden pro Sprachpaar in einem kleinen [LruCache] gehalten (das
 * von der ML-Kit-Doku empfohlene Muster) statt bei jedem Sprachwechsel sofort
 * geschlossen zu werden: Das sofortige Schließen führte zu
 * "Translation closed"-Fehlern, wenn eine laufende Übersetzung (oder der
 * Live-Modus) den gerade geschlossenen Translator noch benutzte. Geschlossen
 * wird jetzt nur noch bei Cache-Verdrängung und in [closeAll].
 */
object TranslationEngine {

    private const val MAX_CACHED_TRANSLATORS = 4

    private val translators = object : LruCache<String, Translator>(MAX_CACHED_TRANSLATORS) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Translator, newValue: Translator?) {
            oldValue.close()
        }
    }

    @Synchronized
    private fun translatorFor(source: Language, target: Language): Translator {
        val key = "${source.code}->${target.code}"
        translators.get(key)?.let { return it }
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(source.mlKitLanguage)
            .setTargetLanguage(target.mlKitLanguage)
            .build()
        return Translation.getClient(options).also { translators.put(key, it) }
    }

    /**
     * Übersetzt [text] von [source] nach [target]. Lädt das Sprachmodellpaar
     * bei Bedarf einmalig herunter (ohne WLAN-Zwang, kleine Modelle).
     */
    suspend fun translate(source: Language, target: Language, text: String): String {
        val translator = translatorFor(source, target)
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
        return translator.translate(text).await()
    }

    /** Gibt alle gecachten Translator frei (beim Verlassen des Screens). */
    @Synchronized
    fun closeAll() {
        translators.evictAll()
    }
}
