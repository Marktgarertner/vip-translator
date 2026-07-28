package de.vip.liveuebersetzer

/**
 * Schnellbausteine: Sätze, die am Schalter ständig gebraucht werden und sich
 * per Tastendruck übersetzen und vorlesen lassen - ohne sie jedes Mal zu
 * sprechen.
 *
 * **Diese Liste ist ausdrücklich eine Startauswahl** und stammt nicht aus den
 * echten Abläufen des ViP-Kundencenters. Sie soll vom Kundencenter
 * überschrieben werden: Welche Sätze hier stehen müssen, wissen nur die
 * Kolleg:innen am Schalter. Ändern heißt: unten Einträge anpassen, ergänzen
 * oder löschen - mehr ist nicht nötig.
 *
 * Die Sätze sind auf Deutsch formuliert und werden beim Antippen in die
 * aktuell gewählte Kundensprache übersetzt.
 */
object StaffPhrases {

    data class Phrase(val category: String, val text: String)

    val all: List<Phrase> = listOf(
        Phrase("Begrüßung", "Herzlich willkommen! Wie kann ich Ihnen helfen?"),
        Phrase("Begrüßung", "Bitte nehmen Sie Platz."),
        Phrase("Begrüßung", "Einen Moment bitte."),
        Phrase("Begrüßung", "Ich hole eine Kollegin, die Ihnen weiterhelfen kann."),

        Phrase("Unterlagen", "Dürfte ich bitte Ihren Ausweis sehen?"),
        Phrase("Unterlagen", "Bitte zeigen Sie mir Ihre Fahrkarte."),
        Phrase("Unterlagen", "Dafür brauchen wir ein Passfoto."),
        Phrase("Unterlagen", "Bitte füllen Sie dieses Formular aus."),

        Phrase("Bezahlen", "Sie können hier am Schalter oder am Automaten bezahlen."),
        Phrase("Bezahlen", "Möchten Sie bar oder mit Karte bezahlen?"),
        Phrase("Bezahlen", "Bitte geben Sie Ihre PIN ein."),

        Phrase("Auskunft", "Diese Fahrkarte ist leider abgelaufen."),
        Phrase("Auskunft", "Die Fahrkarte gilt für diese Fahrt nicht."),
        Phrase("Auskunft", "Sie müssen einmal umsteigen."),
        Phrase("Auskunft", "Die Haltestelle ist wegen einer Baustelle verlegt."),
        Phrase("Auskunft", "Der nächste Bus fährt in wenigen Minuten."),

        Phrase("Ablauf", "Das dauert etwa zehn Minuten."),
        Phrase("Ablauf", "Bitte ziehen Sie eine Wartenummer."),
        Phrase("Ablauf", "Bitte kommen Sie damit noch einmal wieder."),
        Phrase("Ablauf", "Das kann ich hier leider nicht erledigen."),

        Phrase("Abschluss", "Haben Sie noch eine Frage?"),
        Phrase("Abschluss", "Hat alles geklappt?"),
        Phrase("Abschluss", "Vielen Dank und gute Fahrt!"),
    )

    val categories: List<String> = all.map { it.category }.distinct()
}
