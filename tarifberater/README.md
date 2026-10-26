# ViP Tarifberater – „Welches Ticket passt zu Ihnen?“

Eine einzelne HTML-Datei ohne Backend, ohne Cookies, ohne externe
Abhängigkeiten: `tarifberater/index.html`. Vier Fragen (wer, wie oft, wohin,
was ist dabei), dann eine begründete Empfehlung mit Preis, Alternativen,
Hinweisen und der Liste der Unterlagen. Gedacht für drei Orte:

1. **In der ViP-App (Geomobile)** – als eingebettete Seite oder Link. Die App
   kommt von einem Dienstleister, an ihren Code kommen wir nicht heran; eine
   HTML-Seite ist der Weg, der ohne Vertragsänderung funktioniert.
2. **Auf der Website** (swp-potsdam.de / vip-potsdam.de).
3. **Am Schalter** – auf dem Tablet neben dem Live-Übersetzer, damit neue
   Kolleg:innen dieselbe Antwort geben wie erfahrene.

## Einbetten und aufrufen

Die Datei kann auf jedem statischen Webspace liegen. Es gibt keine
Server-Logik; alles läuft im Browser des Nutzers.

| Aufruf | Wirkung |
|---|---|
| `index.html` | Vollansicht mit grüner Kopfleiste |
| `index.html?embed=1` | ohne Kopfleiste – für WebViews und iframes, in denen die App schon einen Kopf hat |
| `index.html?jahr=2027` | Vorschau mit den Preisen des Folgejahres (für die Pflege, nicht verlinken) |
| `index.html#w=erwachsen&h=oft&z=berlin&x=hund` | Direktlink auf ein Ergebnis – die Antworten stehen im Hash, Zurück-Taste und Neuladen behalten den Stand |

Hash-Schlüssel: `w` = wer (`erwachsen`, `kind`, `u6`, `schueler`, `azubi`,
`student`, `leistung`, `sb`), `h` = wie oft (`einmal`, `tag`, `gelegentlich`,
`wenig`, `oft`), `z` = wohin (`ab`, `abc`, `berlin`, `weit`), `x` = Extras
(kommagetrennt: `kinder`, `gruppe`, `fahrrad`, `hund`).

**Geomobile:** Die üblichen Wege sind ein Menüpunkt, der eine URL in einem
In-App-Browser öffnet, oder ein WebView-Modul mit konfigurierbarer URL. Beides
braucht nur die Adresse, unter der die Datei liegt – am besten
`...?embed=1`. Wer das bei Geomobile beauftragt, sollte nach „externer Link“
oder „Web-Inhalt“ im Produktkatalog fragen; das ist in solchen Apps
Standard. Die Seite setzt keine Cookies und lädt nichts nach, es gibt also
auch nichts für eine Datenschutzerklärung nachzutragen.

**GitHub Pages** (falls kein eigener Webspace zur Hand ist): In den
Repository-Einstellungen unter „Pages“ den Branch wählen, Ordner `/` –
danach ist die Seite unter `https://<nutzer>.github.io/vip-translator/tarifberater/`
erreichbar. Für den Produktivbetrieb gehört sie aber auf eine ViP-Domain.

## Die Entscheidungslogik in einem Absatz

Die Logik steht in `index.html` im Block `<script id="tarif-engine">` und
ist bewusst als lesbare Fallunterscheidung geschrieben, nicht als
Punktesystem – damit jemand aus dem Kundenzentrum sie nachvollziehen und
korrigieren kann. Grundlinien:

- **Einmalige Fahrt:** Einzelfahrausweis des passenden Bereichs; in Potsdam AB
  zusätzlich die Kurzstrecke als Alternative; nach Berlin der Hinweis auf den
  Anschlussfahrausweis für Zeitkarten-Inhaber.
- **Mehrere Fahrten an einem Tag:** 24-Stunden-Karte (ab der dritten Fahrt
  günstiger als Einzelfahrausweise; bis zu drei Kinder 6–14 fahren mit); bei
  3–5 Personen die Kleingruppenkarte; für das Gesamtnetz die Tageskarte mit
  dem Hinweis, dass sie werktags erst ab 9 Uhr gilt.
- **Ein paar Mal im Monat (≈ 6 Fahrten):** 4-Fahrten-Karte, mit der Rechnung,
  ab wie vielen Fahrten sich ein Abo lohnt (Potsdam AB: ab 19 Fahrten).
- **Regelmäßig (≈ 15 bzw. ≈ 40 Fahrten):** Alle passenden Angebote werden
  nach Monatspreis sortiert – 4-Fahrten-Karten hochgerechnet, Umweltkarte als
  Abo und Monatskarte, Deutschlandticket. Für Berlin gewinnt das
  Deutschlandticket immer gegen die Berlin-ABC-Zeitkarten; die Kombination
  „Umweltkarte Potsdam AB + Anschluss nach Berlin“ wird mit der Schwelle
  angezeigt, bis zu der sie günstiger ist (vier Berlin-Fahrten im Monat).
- **Zielgruppen:** Schülerticket Potsdam (15 € Eigenanteil, nur AB), VBB-Abo
  Schüler/Azubi, Deutschlandticket Zuschuss Ausbildung, Mobilitätsticket
  Brandenburg (nur Brandenburg, nicht Berlin – das sagt die Seite
  ausdrücklich), Deutschlandsemesterticket (nur über die Hochschule),
  Freifahrt mit Schwerbehindertenausweis und Wertmarke, Kinder unter 6.
- **Extras:** Fahrrad (immer extra, auch zum Deutschlandticket), Hund
  (Einzelfahrausweis: Ermäßigungstarif; Tages-, Zeitkarte und Deutschlandticket:
  ein Hund frei), Kinder (24-Stunden-Karte und Umweltkarte nehmen Kinder
  mit, das Deutschlandticket nicht), Gruppe.

## Tarifdaten pflegen

Alle Preise stehen in **einem** JSON-Block am Ende von `index.html`
(`<script id="tarif-data">`), jeder Preis als Paar `[2026, 2027]`. Die Seite
wählt das Jahr automatisch nach dem Datum und zeigt den Folgepreis in
Klammern („ab 1.1.2027: …“), solange er abweicht. Der Jahreswechsel braucht
also **keinen Eingriff**.

Wenn der VBB die Preise für 2028 veröffentlicht (üblicherweise im September):

1. In `jahre` den Eintrag 2026 entfernen und 2028 anhängen.
2. In jedem Preispaar den ersten Wert streichen und den 2028-Wert anhängen –
   aus `[63.00, 66.80]` wird `[66.80, 70.00]`.
3. `stand` auf das Pflegedatum setzen.
4. `node tarifberater/test/engine-test.mjs` laufen lassen – der Test prüft
   unter anderem, dass kein Preis um mehr als 10 % springt (Tippfehler-Schutz)
   und dass alle Kombinationen ein Ergebnis liefern.
5. Mit `?jahr=2028` die Vorschau ansehen.

Texte, die Zahlen enthalten (etwa „ab 19 Fahrten lohnt sich ein Abo“),
werden aus den Daten berechnet – sie müssen nicht von Hand angepasst werden.

## Quellen und Prüfstand (26.10.2026)

| Was | Quelle | Stand |
|---|---|---|
| Alle Preise 2026 und 2027 für Potsdam und Berlin | [VBB-Preisliste 2027 (PDF)](https://www.vbb.de/fileadmin/user_upload/VBB/Dokumente/Tickets-Abonnements/preise-2027.pdf) – Spalten 01/2026 und 01/2027 | VBB-Stand 24.9.2026, 2027 „vorbehaltlich Tarifgenehmigung“ |
| Preise 2026 (Gegenprobe), eingestellte Angebote (7-Tage-Karte, BC, 65plus, 9-Uhr-Karte) | [VBB: Neue Fahrpreise ab 1.1.2026](https://www.vbb.de/news/neue-fahrpreise-im-vbb-ab-1-januar-2026/) | 17.9.2025 |
| Geltungsregeln Einzelfahrausweis, Kurzstrecke, 24-Stunden-Karte, Kleingruppe | [ViP Einzelfahrausweise](https://www.swp-potsdam.de/de/verkehr/tickets-und-preise/preise-einzelfahrausweise/), [ViP Tageskarten](https://www.swp-potsdam.de/de/verkehr/tickets-und-preise/preise-tageskarten/) | „Preise ab 01.01.2026“ |
| Umweltkarte: Mitnahme, Übertragbarkeit | [ViP Monatskarten](https://www.swp-potsdam.de/de/verkehr/tickets-und-preise/preise-monatskarten-(ohne-abo)/), [VBB-Umweltkarte](https://www.vbb.de/tickets/monatskarten/vbb-umweltkarte/) | 2026 |
| Deutschlandticket: Mitnahme, Hund im VBB, Fahrrad, Fernverkehr | [VBB: Wissenswertes zum Deutschlandticket](https://www.vbb.de/abonnements/deutschlandticket/wissenswertes-rund-um-mitnahme-1-klasse-und-mehr/) | 2026 |
| Deutschlandticket 66,80 € ab 2027 | Pressemeldungen zur Entscheidung des Koordinierungsrats (Sept. 2026); in der VBB-Liste enthalten | Sept. 2026 |
| Deutschlandticket Job / Zuschuss Ausbildung, Firmenticket | [ViP Jobticket](https://www.swp-potsdam.de/de/verkehr/firmenticket/) | 2026 |
| Schülerticket Potsdam 15 € Eigenanteil, 26,30 € gesamt | VBB-Preisliste; Stadt Potsdam: Haushaltsbeschluss 25.3.2026 (Erhöhung auf 18 € abgelehnt) | 2026 |
| Mobilitätsticket Brandenburg: Berechtigte, Geltung, Handyticket in Potsdam | [VBB Mobilitätsticket](https://www.vbb.de/tickets/monatskarten/mobilitaetsticket-brandenburg/) | 2026 |
| Deutschlandsemesterticket 60 % des Deutschlandtickets | Amtliche Mitteilung TH Brandenburg (SoSe 2026 / WiSe 2026/27) | 2026 |
| Hund: Zeitkarten und 24-Stunden-Karten ein Hund frei, sonst Ermäßigungstarif | [VBB Mitnahmemöglichkeiten](https://www.vbb.de/tickets/tarifinformationen-services/mitnahmemoeglichkeiten/) | 2026 |
| Abo-Bestellung bis zum 10. des Vormonats | [ViP Abo-FAQ](https://www.swp-potsdam.de/abonnement/faq/) | 2026 |

**Zwei Preise sind abgeleitet, nicht veröffentlicht** (im JSON unter
`abgeleitet`): Deutschlandticket Job 2027 (46,76 € = 66,80 € abzüglich 25 %
Arbeitgeberzuschuss und 5 % Rabatt) und Deutschlandticket Zuschuss
Ausbildung 2027 (40,08 € = 60 %). Die Seite sagt das beim Azubi-Ticket
ausdrücklich dazu.

## Vor dem Einsatz bitte prüfen

Das sind die Stellen, an denen ich Annahmen treffen musste. Eine Viertelstunde
mit jemandem aus dem Kundenzentrum klärt alle:

1. **Beispielorte für Potsdam C.** Die Seite nennt Werder (Havel), Teltow,
   Michendorf und Caputh. Das stammt aus Beschriftungen der VBB-Tarifkarte und
   Bahnhofsdaten (Teltow Stadt: Berlin C / Potsdam C), nicht aus einer
   offiziellen Ortsliste. Bitte gegen die aktuelle Tarifbereichskarte
   abgleichen und ggf. in `index.html` bei „Potsdam und Umland“ anpassen.
2. **Schülerticket Potsdam – wer genau?** Der VBB schreibt „Schüler:innen
   innerhalb Potsdams“ mit Schulbescheinigung. Ob Wohnort oder Schulort in
   Potsdam zählt, steht nirgends eindeutig. Die Seite sagt „Für Potsdamer
   Schüler:innen“ und verlangt die Schulbescheinigung.
3. **Abo-Laufzeit und Kündigungsfrist der VBB-Umweltkarte** bei der ViP – die
   Seite verweist auf die Abo-Bedingungen, ohne Frist zu nennen.
4. **Fahrrad in ViP-Fahrzeugen.** Die Preisliste kennt für Potsdam AB keinen
   Einzelfahrausweis Fahrrad (nur ABC für 2,70 € und die Monatskarte AB). Die
   Seite empfiehlt deshalb den ABC-Fahrausweis. Falls die Mitnahme in Tram und
   Bus der ViP innerhalb AB tatsächlich kostenlos ist, bitte den Text in
   `fahrradHinweis()` entsprechend ändern – das wäre eine gute Nachricht für
   die Kundschaft.
5. **Mobilitätsticket in Potsdam.** Laut VBB für SGB-II-Beziehende in Potsdam
   nur als Handyticket über das Jobcenter, für andere Berechtigte mit
   VBB-Kundenkarte. Die Formulierung „Handyticket über das Jobcenter“ bitte
   mit dem tatsächlichen Ablauf abgleichen.
6. **Deutschlandticket Zuschuss Ausbildung in Brandenburg:** Verkaufsstart
   war laut VBB für Juli/August 2026 angekündigt. Die ViP-Seite listet es
   bereits mit 37,80 €. Bitte bestätigen, dass es am Schalter tatsächlich
   beantragt werden kann (duale Ausbildung läuft über den Betrieb).

## Testen

```
node tarifberater/test/engine-test.mjs
```

zieht Tarifdaten und Logik direkt aus `index.html` (kein Kopieren, kein
Auseinanderlaufen) und prüft rund 50 Erwartungen – vom „Kind unter 6 braucht
kein Ticket“ bis zur Sortierung der Monatsangebote – sowie alle 2.160
Kombinationen aus Frageantworten, Extras und Tarifjahr auf Fehler und leere
Ergebnisse.

Optisch geprüft im headless Chromium bei 390 px (Telefon), 1024 px und im
dunklen Modus; die Screenshots liegen nicht im Repo, weil sie bei jeder
Änderung neu entstehen – der Befehl steht im Commit, der diesen Ordner
angelegt hat.

## Was die Seite bewusst nicht tut

- **Keine verbindliche Auskunft.** Sie steht unten auf jeder Seite:
  Verbindlich sind die VBB-Tarifbestimmungen. Der Berater ordnet ein, er
  entscheidet nicht – dieselbe Linie wie bei allen anderen Bausteinen.
- **Keine Preisberechnung für Entfernungstarife** (Einzelfahrausweis nach
  Kilometern im Land Brandenburg). Das kann ein Wabenplan, keine
  Vier-Fragen-Seite; die Seite verweist auf VBB-App und Automat.
- **Keine Daten über die Nutzer.** Nichts wird gespeichert oder gesendet.
  Der URL-Hash enthält nur die vier Antworten.

## Mögliche nächste Schritte

- **Mehrsprachig:** Die Oberflächentexte in ein Wörterbuch ziehen und von
  Muttersprachler:innen übersetzen lassen (Englisch, Ukrainisch, Arabisch,
  Türkisch – die Sprachen aus dem Übersetzer). Maschinell übersetzen würde
  ich Tariftexte nicht: Ein falsches Wort bei „gilt nicht in Berlin“ kostet
  jemanden 60 Euro Bußgeld.
- **Tarifbereich per Haltestelle:** Statt „Umland – z. B. Werder“ eine
  Haltestellensuche aus den offenen VBB-GTFS-Daten, die den Bereich liefert.
  Das wäre derselbe Datensatz wie für die Baustellen-Idee.
- **Zählen, was gefragt wird:** Wenn die ViP wissen möchte, welche
  Kombinationen am häufigsten vorkommen, ginge das datensparsam über die
  eigene Webserver-Statistik der Hash-Werte – ohne Cookies, ohne Tracking.
