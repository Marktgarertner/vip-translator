package de.vip.liveuebersetzer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sicherheitsnetz für [TransitGlossary].
 *
 * Die Nachkorrektur ist der einzige Teil der App, der ohne Gerät und ohne
 * Google-/Vosk-Bibliotheken vollständig prüfbar ist - und gleichzeitig der
 * riskanteste: Eine zu großzügige Schwelle würde Alltagssprache still zu
 * Fachbegriffen "korrigieren" und damit Gespräche verfälschen.
 *
 * Die Negativtests unten sind deshalb genauso wichtig wie die Positivtests.
 * Zwei davon haben beim Entwickeln echte Fehler aufgedeckt:
 *  - "ich fahre nach ..." wurde zu "ich Fähre nach ...", weil Umlaut-Faltung
 *    "fahre" und "Fähre" zu demselben Schlüssel machte (exakter Treffer, der
 *    alle Sicherheitsprüfungen übersprang).
 *  - "wir werden ..." wurde zu "wir Werder ...", weil sich das häufige Verb
 *    und der Ortsname nur um einen Buchstaben unterscheiden.
 * Beide Fälle stehen unten und müssen grün bleiben.
 */
class TransitGlossaryTest {

    private fun language(code: String) = Language(
        code = code,
        displayName = code,
        nativeName = code,
        greeting = "",
        tapToSpeak = "",
        mlKitLanguage = code,
        speechLocaleTag = code,
        mlKitLiveSpeech = true,
    )

    private val german = language("de")
    private val english = language("en")
    private val ukrainian = language("uk")

    private fun assertCorrected(expected: String, input: String, language: Language = german) {
        assertEquals(expected, TransitGlossary.correct(language, input))
    }

    private fun assertUnchanged(input: String, language: Language = german) {
        assertEquals(input, TransitGlossary.correct(language, input))
    }

    // --- Zerlegte Komposita wieder zusammensetzen ---------------------------

    @Test
    fun `setzt von der Erkennung zerlegte Komposita zusammen`() {
        assertCorrected("Ich brauche einen Kassenautomat", "Ich brauche einen Kassen Automat")
        assertCorrected("Wo ist der Einzelfahrausweis", "Wo ist der Einzel Fahrausweis")
        assertCorrected("Fahrkartenautomat defekt", "Fahrkarten Automat defekt")
    }

    @Test
    fun `setzt auch dreiteilige Komposita zusammen`() {
        assertCorrected("Schienenersatzverkehr heute", "Schienen Ersatz Verkehr heute")
    }

    // --- Eigennamen --------------------------------------------------------

    @Test
    fun `korrigiert lautaehnlich verschriebene Eigennamen`() {
        assertCorrected("Ich fahre nach Potsdam", "Ich fahre nach Potstam")
        assertCorrected("Haltestelle Babelsberg", "Haltestelle Babelsberch")
        assertCorrected("bis Griebnitzsee bitte", "bis Griebnitzee bitte")
    }

    @Test
    fun `erkennt Mehrwort-Haltestellennamen`() {
        assertCorrected("zum Alter Markt", "zum alter markt")
        assertCorrected("am Platz der Einheit", "am Platz der einheit")
    }

    // --- Schreibweise ------------------------------------------------------

    @Test
    fun `stellt kanonische Schreibweise und Umlaute her`() {
        assertCorrected("ein Einzelfahrausweis bitte", "ein einzelfahrausweis bitte")
        assertCorrected("die Verspätung ist lang", "die Verspatung ist lang")
    }

    @Test
    fun `erhaelt Satzzeichen und respektiert Satzgrenzen`() {
        assertCorrected("Wo ist der Kassenautomat?", "Wo ist der Kassen Automat?")
        // Ueber einen Punkt hinweg darf nicht zusammengezogen werden.
        assertUnchanged("Danke, Kassen. Automat kaputt")
    }

    // --- Negativtests: Alltagssprache darf NICHT angefasst werden -----------

    @Test
    fun `laesst normale Alltagssaetze unveraendert`() {
        assertUnchanged("Guten Tag ich habe eine Frage")
        assertUnchanged("Können Sie mir bitte helfen")
        assertUnchanged("Das kostet zwei Euro fünfzig")
        assertUnchanged("Vielen Dank für Ihre Hilfe")
        assertUnchanged("Meine Tochter ist erst sechs Jahre alt")
        assertUnchanged("Der Automat hat mein Geld behalten")
    }

    @Test
    fun `verwechselt haeufige Woerter nicht mit kurzen Glossareintraegen`() {
        // Regressionstests zu zwei echten, im Test gefundenen Fehlern:
        assertUnchanged("Wir werden das klären") // "werden" vs. Ortsname "Werder"
        assertUnchanged("Wir fahren gleich los") // "fahren" vs. "Fähre"
        assertUnchanged("Der Fahrer war sehr nett")
        // Weitere nahe Kandidaten:
        assertUnchanged("Ich bin Berliner")
        assertUnchanged("Haben Sie noch Karten")
        assertUnchanged("Die Wartezeit war lang")
        assertUnchanged("Zum Bahnhof bitte")
        assertUnchanged("Ich habe meinen Ausweis dabei")
        assertUnchanged("Ich bin in Eile")
        assertUnchanged("Der Bus war voll")
    }

    // --- Andere Sprachen ---------------------------------------------------

    @Test
    fun `wendet auf Fremdsprachen nur Eigennamen an`() {
        assertCorrected("I want to go to Potsdam", "I want to go to Potstam", english)
        // Deutsches Fachvokabular darf englische Saetze nicht anfassen.
        assertUnchanged("where can I buy a ticket for the tram", english)
        assertUnchanged("how much does a single ticket cost", english)
        assertUnchanged("is there a connection to the airport", english)
    }

    @Test
    fun `laesst nicht-lateinische Schrift unveraendert`() {
        assertUnchanged("Доброго дня, де зупинка", ukrainian)
    }
}
