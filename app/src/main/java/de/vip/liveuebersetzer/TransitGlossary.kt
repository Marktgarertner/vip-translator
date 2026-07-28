package de.vip.liveuebersetzer

import java.util.Locale

/**
 * Fachwortschatz des ÖPNV bzw. der ViP Potsdam - Grundlage einer
 * **Nachkorrektur des erkannten Textes**.
 *
 * Hintergrund (Praxistest-Befund): Allgemeine Spracherkennungsmodelle kennen
 * weder Tarifvokabular ("Einzelfahrausweis", "Anschlussfahrausweis") noch
 * lokale Eigennamen ("Luisenplatz", "Griebnitzsee") gut. Typische Fehler:
 *  - Deutsche Komposita werden in Einzelwörter zerlegt: "Kassen Automat",
 *    "Einzel Fahrausweis".
 *  - Eigennamen werden lautähnlich verschrieben: "Potstam", "Babelsberch".
 *  - Mehrwort-Haltestellennamen kommen unsauber an: "alter markt".
 *
 * Deshalb läuft jeder **fertig erkannte** Satz durch [correct]: Ein
 * normalisierter Ähnlichkeitsvergleich (Levenshtein) gegen diese Wortliste
 * ersetzt Beinahe-Treffer durch die kanonische Schreibweise, wobei auch
 * Fenster aus zwei oder drei Token geprüft werden, um zerlegte Komposita und
 * Mehrwort-Namen wieder zusammenzusetzen.
 *
 * Das verbessert **zugleich die Übersetzung**: ML Kit Translate bekommt dann
 * das korrekte Kompositum statt zweier Bruchstücke und liefert eine deutlich
 * brauchbarere Übersetzung.
 *
 * Die Schwellen sind bewusst konservativ gewählt (Mindestlängen,
 * Ähnlichkeit >= 82 % bzw. 85 % beim Zusammenziehen), damit normale Wörter
 * nicht fälschlich zu Fachbegriffen "korrigiert" werden. Falschkorrekturen
 * bleiben sichtbar: Wurde etwas geändert, zeigt die Mitarbeiterseite den
 * ursprünglich erkannten Wortlaut als Hinweiszeile.
 *
 * **Erweitern:** Einfach unten in [properNouns] bzw. [generalTerms] Begriffe
 * in korrekter Schreibweise ergänzen - kein weiterer Code nötig. Die Listen
 * sind eine Startauswahl und **nicht** als vollständiges oder amtlich
 * geprüftes Haltestellenverzeichnis zu verstehen; für den Rollout sollte ViP
 * sie gegen die eigenen Tarif- und Haltestellendaten abgleichen.
 */
object TransitGlossary {

    /**
     * Ab dieser normalisierten Länge wird überhaupt unscharf verglichen. Kurze
     * Wörter bleiben außen vor - sie werden nur noch exakt erkannt: Bei ihnen
     * wiegt eine einzelne Abweichung relativ so schwer, dass Alltagswörter
     * fälschlich zu Fachbegriffen würden. Im Test aufgefallener Fall bei
     * Länge 6: "wir werden ..." wurde zu "wir Werder ...", weil sich das
     * häufige Verb "werden" und der Ortsname "Werder" nur um einen
     * Buchstaben unterscheiden.
     */
    private const val MIN_FUZZY_LENGTH = 7

    /** Mindestähnlichkeit für ein einzelnes Token. */
    private const val SINGLE_THRESHOLD = 0.80

    /** Zusammengezogene Treffer müssen mindestens so lang sein. */
    private const val MIN_MERGED_LENGTH = 8

    /** Größtes geprüftes Token-Fenster (z. B. "Platz der Einheit"). */
    private const val MAX_WINDOW = 3

    /** Längenunterschied, ab dem ein Kandidat gar nicht verglichen wird. */
    private const val MAX_LENGTH_DIFF = 3

    /**
     * Eigennamen: Orte, Haltestellen, Marken. Sprachunabhängig - ein Kunde
     * nennt die Haltestelle auch auf Englisch "Luisenplatz". Wird deshalb für
     * **alle** Sprachen angewendet (bei anderen Schriftsystemen wie Kyrillisch
     * oder Arabisch greift der Vergleich schlicht nie, was unschädlich ist).
     */
    val properNouns: List<String> = listOf(
        // Unternehmen, Verbund, Produkte
        "ViP", "Verkehrsbetrieb Potsdam", "Stadtwerke Potsdam", "SWP",
        "VBB", "Verkehrsverbund Berlin-Brandenburg", "PotsdamPass",
        "Deutschlandticket", "Semesterticket", "Firmenticket", "Sozialticket",
        "Umweltkarte",
        // Stadt und Ortsteile
        "Potsdam", "Babelsberg", "Bornstedt", "Bornim", "Drewitz", "Eiche",
        "Fahrland", "Golm", "Groß Glienicke", "Hermannswerder", "Kirchsteigfeld",
        "Marquardt", "Sacrow", "Schlaatz", "Waldstadt", "Jungfernsee",
        // Haltestellen und Ziele im Stadtgebiet
        "Hauptbahnhof", "Luisenplatz", "Platz der Einheit", "Alter Markt",
        "Bassinplatz", "Nauener Tor", "Brandenburger Tor", "Sanssouci",
        "Charlottenhof", "Griebnitzsee", "Rehbrücke", "Am Stern",
        "Stern-Center", "Johannes-Kepler-Platz", "Glienicker Brücke",
        "Filmpark Babelsberg", "Volkspark", "Karl-Liebknecht-Stadion",
        "Klinikum Ernst von Bergmann", "Rathaus", "Dortustraße", "Burgstraße",
        "Puschkinallee", "Reiterweg", "Kurfürstenstraße", "Rote Kaserne",
        "Viereckremise", "Marie-Juchacz-Straße", "Campus Jungfernsee",
        // Umland und Anschlüsse
        "Werder", "Havel", "Caputh", "Michendorf", "Teltow", "Stahnsdorf",
        "Kleinmachnow", "Beelitz", "Schwielowsee", "Wannsee", "Berlin",
    )

    /**
     * Deutschsprachiger Fachwortschatz (Tarif, Technik, Betrieb). Wird nur auf
     * deutschsprachig erkannten Text angewendet, damit fremdsprachige Wörter
     * nicht fälschlich zu deutschen Fachbegriffen korrigiert werden.
     */
    val generalTerms: List<String> = listOf(
        // Fahrausweise und Tarif
        "Einzelfahrausweis", "Einzelfahrschein", "Anschlussfahrausweis",
        "Tageskarte", "Wochenkarte", "Monatskarte", "Jahreskarte",
        "Kleingruppenkarte", "Gruppenkarte", "Kurzstrecke", "Zeitkarte",
        "Fahrkarte", "Fahrschein", "Fahrausweis", "Abonnement",
        "Kinderfahrausweis", "Fahrradkarte", "Fahrradtageskarte", "Hundekarte",
        "Ermäßigung", "Ermäßigungsberechtigung", "Wertmarke", "Preisstufe",
        "Tarifzone", "Tarifbereich", "Tarifwabe", "Geltungsbereich",
        "Schwerbehindertenausweis", "Beförderungsentgelt",
        // Automaten, Service, Infrastruktur
        "Fahrkartenautomat", "Kassenautomat", "Fahrausweisautomat", "Entwerter",
        "Kundencenter", "Servicecenter", "Fahrkartenschalter", "Fundbüro",
        "Fahrgastinformation", "Fahrplanauskunft", "Fahrplanaushang",
        "Anzeigetafel", "Wartehalle", "Haltestelle", "Haltestellenmast",
        "Bahnsteig", "Betriebshof",
        // Fahrzeuge und Betrieb
        "Straßenbahn", "Niederflurbahn", "Gelenkbus", "Ersatzverkehr",
        "Schienenersatzverkehr", "Umleitung", "Baustelle", "Anschluss",
        "Anschlusssicherung", "Fahrplan", "Fahrplanwechsel", "Verspätung",
        "Nachtverkehr", "Taktverkehr", "Umstieg", "Endhaltestelle", "Fähre",
        // Kontrolle, Recht, Barrierefreiheit
        "Fahrausweisprüfer", "Beförderungsbedingungen", "Beschwerde",
        "Fahrgastrechte", "Fundsachen", "barrierefrei", "Rollstuhl",
        "Rollstuhlrampe", "Kinderwagen", "Aufzug", "Blindenleitsystem",
        "Mobilitätseingeschränkte",
    )

    /**
     * Nachschlagetabellen: exakter Schlüssel ohne Umlaut-Faltung (siehe
     * [key]/[foldedKey]) plus die gefalteten Formen für den unscharfen
     * Vergleich.
     */
    private class Index(terms: List<String>) {
        val exact: Map<String, String> = terms.associateBy(::key)
        val fuzzy: List<Pair<String, String>> = terms.map { foldedKey(it) to it }
    }

    /**
     * Im Menü "Fachbegriffe" selbst hinterlegte Begriffe (siehe [AppSettings]).
     * Sie gelten für **alle** Sprachen, weil es typischerweise ViP-eigene
     * Eigennamen sind (Produkt-, Tarif-, Haltestellennamen), die ein Kunde
     * auch in seiner Sprache so ausspricht.
     */
    private var customTerms: List<String> = emptyList()

    private var properNounIndex = Index(properNouns)

    private var fullIndex = Index(properNouns + generalTerms)

    /** Übernimmt die selbst gepflegten Begriffe und baut die Tabellen neu auf. */
    @Synchronized
    fun setCustomTerms(terms: Collection<String>) {
        customTerms = terms.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        properNounIndex = Index(properNouns + customTerms)
        fullIndex = Index(properNouns + generalTerms + customTerms)
    }

    /**
     * Korrigiert [text] gegen den Fachwortschatz. Für Deutsch mit vollem
     * Glossar, für alle anderen Sprachen nur gegen die Eigennamen (siehe
     * [properNouns]) und die selbst gepflegten Begriffe.
     */
    fun correct(language: Language, text: String): String {
        val index = if (language.code == "de") fullIndex else properNounIndex
        return correctWith(index, text)
    }

    private fun correctWith(index: Index, text: String): String {
        if (text.isBlank()) return text
        val tokens = text.trim().split(Regex("\\s+"))
        val result = ArrayList<String>(tokens.size)
        var i = 0
        while (i < tokens.size) {
            var consumed = 0
            // Größere Fenster zuerst, damit die spezifischere Mehrwort-
            // Bezeichnung gewinnt ("Platz der Einheit" vor "Platz").
            var window = minOf(MAX_WINDOW, tokens.size - i)
            while (window >= 2) {
                val slice = tokens.subList(i, i + window)
                if (!crossesSentenceBoundary(slice)) {
                    val canonical = matchWindow(index, slice)
                    if (canonical != null) {
                        result += reattachAffixes(slice, canonical)
                        consumed = window
                        break
                    }
                }
                window--
            }
            if (consumed > 0) {
                i += consumed
                continue
            }
            result += matchSingle(index, tokens[i]) ?: tokens[i]
            i++
        }
        return result.joinToString(" ")
    }

    /**
     * Ein Fenster darf keine Satzgrenze überspannen: endet ein Token davor auf
     * Satzzeichen, gehören die Wörter nicht zusammen.
     */
    private fun crossesSentenceBoundary(slice: List<String>): Boolean =
        slice.dropLast(1).any { token ->
            affixes(token).third.any { it in ".,;:!?" }
        }

    /**
     * Zusammengezogene Fenster werden **ausschließlich exakt** verglichen -
     * bewusst kein unscharfer Vergleich.
     *
     * Grund (im Test aufgefallen): Unscharf zusammengezogen verschluckt die
     * Korrektur benachbarte Wörter. "Ein Schwielowsee Ticket bitte" wurde zu
     * "Schwielowseeticket bitte" - das "Ein" war weg, weil das Dreier-Fenster
     * dem Begriff ähnlich genug war. Ein verpasster Treffer ist harmlos,
     * verschluckte Wörter verfälschen dagegen die Aussage.
     *
     * Der praktisch wichtige Fall funktioniert exakt ohnehin: Die Erkennung
     * zerlegt Komposita ("Kassen Automat"), schreibt die Teile dabei aber
     * richtig. Ein zugleich zerlegtes *und* verschriebenes Kompositum bleibt
     * unkorrigiert - das ist der bewusst in Kauf genommene Preis.
     */
    private fun matchWindow(index: Index, slice: List<String>): String? {
        val joined = key(slice.joinToString("") { affixes(it).second })
        if (joined.length < MIN_MERGED_LENGTH) return null
        return index.exact[joined]
    }

    private fun matchSingle(index: Index, token: String): String? {
        val (prefix, core, suffix) = affixes(token)
        if (core.isEmpty()) return null
        val exactKey = key(core)
        if (exactKey.isEmpty()) return null
        index.exact[exactKey]?.let { return prefix + it + suffix }
        if (exactKey.length < MIN_FUZZY_LENGTH) return null
        val best = bestFuzzyMatch(index, foldedKey(core), SINGLE_THRESHOLD) ?: return null
        return prefix + best + suffix
    }

    private fun bestFuzzyMatch(index: Index, folded: String, threshold: Double): String? {
        if (folded.length < MIN_FUZZY_LENGTH) return null
        var bestScore = threshold
        var best: String? = null
        for ((candidate, canonical) in index.fuzzy) {
            if (candidate.length < MIN_FUZZY_LENGTH) continue
            if (kotlin.math.abs(candidate.length - folded.length) > MAX_LENGTH_DIFF) continue
            val score = similarity(folded, candidate)
            if (score > bestScore) {
                bestScore = score
                best = canonical
            }
        }
        return best
    }

    /** Setzt Satzzeichen des Fensterrandes wieder an den korrigierten Text. */
    private fun reattachAffixes(slice: List<String>, canonical: String): String =
        affixes(slice.first()).first + canonical + affixes(slice.last()).third

    /** Zerlegt ein Token in (führende Satzzeichen, Wortkern, folgende Satzzeichen). */
    private fun affixes(token: String): Triple<String, String, String> {
        val start = token.indexOfFirst { it.isLetterOrDigit() }
        if (start < 0) return Triple(token, "", "")
        val end = token.indexOfLast { it.isLetterOrDigit() }
        return Triple(
            token.substring(0, start),
            token.substring(start, end + 1),
            token.substring(end + 1),
        )
    }

    /**
     * Schlüssel für den **exakten** Treffer: Kleinschreibung, alles außer
     * Buchstaben und Ziffern entfernt - Umlaute bleiben aber erhalten.
     * Dadurch gelten "Kassen-Automat", "kassenautomat" und "Kassen Automat"
     * (zusammengezogen) als dieselbe Zeichenkette.
     *
     * Umlaute werden hier **bewusst nicht** gefaltet: Sonst fielen ganz
     * normale Wörter mit Fachbegriffen zusammen und würden ohne jede
     * Sicherheitsprüfung ersetzt. Konkreter, im Test aufgefallener Fall:
     * "ich fahre nach ..." wurde zu "ich Fähre nach ...", weil "fahre" und
     * "Fähre" gefaltet identisch sind.
     */
    private fun key(text: String): String = buildString {
        for (char in text.lowercase(Locale.GERMAN)) {
            if (char.isLetterOrDigit()) append(char)
        }
    }

    /**
     * Schlüssel für den **unscharfen** Vergleich: zusätzlich Umlaute und ß
     * gefaltet, damit lautgleiche Schreibfehler der Erkennung ("Verspatung",
     * "Strassenbahn") noch gefunden werden. Hier ist das ungefährlich, weil
     * zusätzlich Mindestlänge und Ähnlichkeitsschwelle greifen.
     */
    private fun foldedKey(text: String): String = buildString {
        for (char in text.lowercase(Locale.GERMAN)) {
            when (char) {
                'ä' -> append('a')
                'ö' -> append('o')
                'ü' -> append('u')
                'ß' -> append("ss")
                else -> if (char.isLetterOrDigit()) append(char)
            }
        }
    }

    /** Ähnlichkeit zweier normalisierter Zeichenketten: 1.0 = identisch. */
    private fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val longest = maxOf(a.length, b.length)
        if (longest == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / longest
    }

    private fun levenshtein(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(substitution, previous[j] + 1, current[j - 1] + 1)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
