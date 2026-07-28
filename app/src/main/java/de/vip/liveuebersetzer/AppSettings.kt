package de.vip.liveuebersetzer

import android.content.Context

/**
 * Gerätelokale Einstellungen des ViP Live-Übersetzers.
 *
 * Bewusst nur Konfiguration - **keine Gesprächsinhalte**. Der
 * Konversationsverlauf bleibt weiterhin ausschließlich im Arbeitsspeicher und
 * wird nie gespeichert (siehe README, Abschnitt "Datenschutz").
 */
object AppSettings {

    private const val FILE = "vip-live-uebersetzer"
    private const val KEY_SETUP_DONE = "setup_done"
    private const val KEY_CUSTOM_TERMS = "custom_glossary_terms"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * Ob die Ersteinrichtung schon einmal abgeschlossen wurde. Beim ersten
     * Start öffnet die App sonst direkt den Einrichtungs-Assistenten, damit
     * niemand vor einem scheinbar fertigen, aber unvorbereiteten Gerät steht.
     */
    fun isSetupDone(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SETUP_DONE, false)

    fun markSetupDone(context: Context) {
        prefs(context).edit().putBoolean(KEY_SETUP_DONE, true).apply()
    }

    /**
     * Selbst gepflegte Fachbegriffe (Menü "Fachbegriffe"). Ergänzen den fest
     * eingebauten Wortschatz aus [TransitGlossary] - so kann das Kundencenter
     * eigene Tarif-, Produkt- und Haltestellennamen nachtragen, ohne dass
     * jemand den Code anfassen muss.
     */
    fun customTerms(context: Context): List<String> =
        prefs(context).getStringSet(KEY_CUSTOM_TERMS, emptySet())
            .orEmpty()
            .sorted()

    fun saveCustomTerms(context: Context, terms: Collection<String>) {
        prefs(context).edit()
            .putStringSet(KEY_CUSTOM_TERMS, terms.toSet())
            .apply()
        TransitGlossary.setCustomTerms(terms)
    }

    /** Lädt die gespeicherten Begriffe in [TransitGlossary] (beim App-Start). */
    fun applyCustomTerms(context: Context) {
        TransitGlossary.setCustomTerms(customTerms(context))
    }
}
