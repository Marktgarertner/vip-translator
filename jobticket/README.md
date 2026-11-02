# ViP Jobticket-Rechner – „Was kostet das Jobticket für Ihr Team?“

Eine HTML-Datei für Firmenkunden (`jobticket/index.html`), ohne Server,
Cookies oder externe Abhängigkeiten. Arbeitgeber geben Anzahl der
Beschäftigten, Zuschuss und Steuerweg ein und sehen sofort:

- was Beschäftigte für das Deutschlandticket Job zahlen (und sparen),
- wie sich der Ticketpreis aufteilt (Rabatt / Zuschuss / Eigenanteil),
- was das Unternehmen im Monat und im Jahr kostet – optional gegen
  wegfallende Stellplätze gerechnet,
- als Faustwert, was derselbe Vorteil als Gehaltserhöhung kosten würde.

Am Ende bereitet ein Knopf eine E-Mail an firmenticket@vip-potsdam.de vor
(mit allen Zahlen und einem Link auf die Rechnung). Die Seite selbst sendet
und speichert nichts.

## Regeln, auf denen die Rechnung beruht (Stand 2. 11. 2026)

| | 2026 | ab 2027 |
|---|---|---|
| Deutschlandticket | 63,00 € | 66,80 € |
| Mindestzuschuss Arbeitgeber (25 %) | 15,75 € | 16,70 € |
| Jobticket-Rabatt (5 %) | 3,15 € | 3,34 € |
| Eigenanteil höchstens | 44,10 € | 46,76 € |

- Unter dem Mindestzuschuss gibt es kein Deutschlandticket Job und keinen
  Rabatt – die Seite warnt dann.
- Steuer: Zuschuss zusätzlich zum Gehalt steuer- und sozialabgabenfrei
  (§ 3 Nr. 15 EStG, Anrechnung auf die Entfernungspauschale) oder 25 %
  Pauschalsteuer plus Soli (§ 40 Abs. 2 EStG), Kirchensteuer nicht
  eingerechnet.
- Faustwert Gehaltserhöhung: 40 % Abzüge bei Beschäftigten, 21 %
  Sozialabgaben beim Arbeitgeber – bewusst als grobe Orientierung
  gekennzeichnet.

**Vor dem Start vom Firmenkunden-Team prüfen lassen:** die 2027er
Rabattregel (5 % bei mindestens 25 % Zuschuss) im VBB-Tarif und ob ViP
Firmenkunden zusätzlich das VBB-Firmenticket anbieten möchte (bisher nicht
im Rechner).

## Aufrufe

| Aufruf | Wirkung |
|---|---|
| `index.html` | Vollansicht |
| `index.html?embed=1` | ohne Kopfleiste, für iframes/WebViews |
| `index.html?jahr=2027` | Preise 2027 vorab |
| `index.html#n=35&z=30&s=pauschal&pk=80&pa=6` | fertige Rechnung als Link: `n` Beschäftigte, `z` Zuschuss (`min`, `voll`, `30` oder Betrag), `s=pauschal`, `pk`/`pa` Stellplatzkosten/-anzahl – gut für Vertriebs-Mails |

## Pflege und Test

Zahlen stehen im Block `<script id="jobticket-data">` (Preise als Paar
`[2026, 2027]`). Nach jeder Änderung:

```
node jobticket/test/rechner-test.mjs
```
