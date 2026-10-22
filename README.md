# ViP Live-Übersetzer

Natives Android-Projekt (Kotlin, Jetpack Compose) für den Einsatz an
Kundenschaltern eines Verkehrsunternehmens: Text- und Live-Sprachübersetzung
für 16 Sprachen, vollständig on-device.

## Warum on-device?

Harte Anforderung: Kundendaten dürfen das Gerät zu keinem Zeitpunkt in
Richtung eines Drittanbieters verlassen. Deshalb:

- **Übersetzung** läuft über [ML Kit Translate](https://developers.google.com/ml-kit/language/translation)
  (`com.google.mlkit:translate`, stabil/GA). Nach einmaligem Download des
  Sprachmodellpaars läuft die Übersetzung komplett offline.
- **Live-Spracherkennung** läuft über [ML Kit GenAI Speech Recognition](https://developers.google.com/ml-kit/genai/speech-recognition/android)
  (`com.google.mlkit:genai-speech-recognition:1.0.0-alpha1`, **Alpha-Status**,
  Basic-Modus) für 13 der 16 Sprachen, für Ukrainisch, Arabisch und Persisch
  über die gebündelte Offline-Erkennung [Vosk](https://alphacephei.com/vosk)
  (Apache-2.0, siehe Abschnitt "Vosk-Modelle"); Chinesisch, Japanisch,
  Koreanisch und Hindi können beides (siehe "Sprachcoverage"). In allen
  Fällen: einmaliger Modell-Download, danach reine On-Device-Inferenz.
- Es gibt **bewusst keinen Cloud-Fallback**, auch nicht für Sprachen ohne
  Live-Unterstützung. Die einzige Internetnutzung der App ist der einmalige
  Download der on-device-Modelle - von Googles ML-Kit-Servern bzw. von
  alphacephei.com für die Vosk-Modelle (`INTERNET`-/`ACCESS_NETWORK_STATE`-
  Berechtigung in `AndroidManifest.xml`) - nie die Übertragung von
  Kunden-Text oder -Audio.

## Architektur

Kernlogik in `app/src/main/java/de/vip/liveuebersetzer/`:

| Datei | Zweck |
|---|---|
| `LanguageCatalog.kt` | Die unterstützten Sprachen, Anzeigenamen, ML-Kit-Sprachkonstanten, Live-Speech-Unterstützung pro Sprache |
| `TranslationEngine.kt` | Wrapper um ML Kit Translate (Translator-Cache pro Sprachpaar, Modell-Download, `translate()`) |
| `SpeechEngine.kt` | Wrapper um ML Kit GenAI Speech Recognition (Recognizer-Erstellung, Modell-Status/-Download mit Fortschritt, `startRecognition()`-Flow) plus die Engine-Weiche `engineFor()`: entscheidet pro Sprache zwischen Vosk (wenn Modell geladen), ML Kit und "kein Live" |
| `VoskSpeechEngine.kt` | Zweite Live-Engine: [Vosk](https://alphacephei.com/vosk) (Apache-2.0), direkt in die App gebündelt statt über einen Android-Systemdienst - einziger Weg für Ukrainisch, Arabisch, Persisch; erprobte Alternative für Chinesisch, Japanisch, Koreanisch, Hindi. Modell-Download/-Entpacken, Recognizer-Erstellung, `SpeechService`-Listener |
| `ModelDownloads.kt` | App-weiter Zustand aller Sprachpaket-Downloads (Übersetzung, ML-Kit-Erkennung, Vosk) - läuft außerhalb der Compose-Hierarchie weiter, auch wenn das Menü verlassen wird |
| `SpeechOutput.kt` | Sprachausgabe über die systemeigene Android-TTS-Engine (on-device): wählt automatisch die beste installierte Offline-Stimme pro Sprache, leicht reduziertes Sprechtempo, Absprung in die TTS-Einstellungen bei fehlender Stimme |
| `TransitGlossary.kt` | ÖPNV-/ViP-Fachwortschatz mit Nachkorrektur des erkannten Textes (zerlegte Komposita, lautähnlich verschriebene Eigennamen) - siehe eigenen Abschnitt unten |
| `AppSettings.kt` | Gerätelokale Einstellungen (Ersteinrichtung erledigt, selbst gepflegte Fachbegriffe) - bewusst **keine** Gesprächsinhalte |
| `Readiness.kt` | Ermittelt pro Sprache, ob Übersetzung, Stimme und Spracheingabe bereit sind (Grundlage des Einrichtungs-Assistenten) |
| `SetupScreen.kt` | Einrichtungs-Assistent und Einstiegsmenü: Mikrofonfreigabe, Einsatzbereitschaft pro Sprache, Absprünge in die übrigen Menüs |
| `GlossaryScreen.kt` | Menü "Fachbegriffe": eigene Begriffe anlegen und löschen |
| `StaffPhrases.kt` / `PhrasesScreen.kt` | Schnellbausteine: häufige Schaltersätze auf Tastendruck übersetzen und vorlesen |
| `MainActivity.kt` | Zustand und Engines (Aufnahme, Übersetzung, Sprachausgabe, Navigation) - reicht an die Bildschirme nur Daten und Rückrufe weiter |
| `ConversationScreen.kt` | Der Splitscreen (Kundenseite 180° gedreht), zustandslos: `ConversationUiState` rein, `ConversationActions` raus. Kein getippter Modus (siehe unten) |
| `ModelsScreen.kt` | Menü "Sprachpakete", zustandslos über `PackageCardState` |
| `ui/components/VipComponents.kt` | Die Bausteine der Oberfläche: Kopfleisten, Sprachpille, Sprechtaste, Sprechblasen, Statusbanner, Statuszeilen, Karten, Knöpfe |
| `ui/theme/` | ViP-Palette und abgeleitete Farbrollen (`Color.kt`, `Theme.kt`), Typo-Skala mit Inter (`Type.kt`), Rundungsskala (`Shape.kt`) |

## Sprachcoverage

Übersetzung deckt alle 16 Sprachen ab. Die Live-Spracherkennung läuft
**zweigleisig** - beide Wege vollständig on-device:

| Sprache | Code | Übersetzung | Live-Spracherkennung über |
|---|---|:---:|:---:|
| Deutsch | `de` | ✅ | ML Kit |
| Englisch | `en` | ✅ | ML Kit |
| Russisch | `ru` | ✅ | ML Kit |
| Türkisch | `tr` | ✅ | ML Kit |
| Polnisch | `pl` | ✅ | ML Kit |
| Vietnamesisch | `vi` | ✅ | ML Kit |
| Französisch | `fr` | ✅ | ML Kit |
| Spanisch | `es` | ✅ | ML Kit |
| Italienisch | `it` | ✅ | ML Kit |
| Chinesisch | `zh` | ✅ | ML Kit (`cmn-Hans-CN`) **oder** Vosk (gebündelt)*† |
| Japanisch | `ja` | ✅ | ML Kit **oder** Vosk (gebündelt)*† |
| Koreanisch | `ko` | ✅ | ML Kit **oder** Vosk (gebündelt)*† |
| Hindi | `hi` | ✅ | ML Kit **oder** Vosk (gebündelt)*† |
| Ukrainisch | `uk` | ✅ | Vosk (gebündelt)* |
| Arabisch | `ar` | ✅ | Vosk (gebündelt)* |
| Persisch | `fa` | ✅ | Vosk (gebündelt)* |

\* Anders als bei ML Kit wird hier NICHT beim ersten Mikro-Tap spontan
nachgeladen - das Vosk-Modell (41-318 MB je nach Sprache) muss vorher im
Menü "Sprachpakete" heruntergeladen worden sein, siehe Abschnitt
"Vosk-Modelle" unten.

† **Zweigleisig:** Ein geladenes Vosk-Modell hat Vorrang (dieser Pfad ist am
Testgerät erprobt). Ohne Vosk-Modell versucht die App ML Kit - der erste
Mikro-Tap lädt dann wie bei den anderen ML-Kit-Sprachen das Erkennungsmodell
nach. Das Sprachpakete-Menü zeigt beide Wege und bietet das Vosk-Modell als
"eigenes Sprachmodell" an, falls die Android-Erkennung auf dem Gerät nicht
verfügbar ist oder schlecht versteht.

**Warum zwei Engines (bewusste Entscheidung, siehe Kommentare in
`LanguageCatalog.kt`/`SpeechEngine.kt`/`VoskSpeechEngine.kt`):** Die
aktuelle Google-Doku (Stand Oktober 2026, in dieser Session erstmals direkt
lesbar - frühere Sessions mussten die Sprachliste aus Suchmaschinen-Snippets
rekonstruieren, siehe "Build-Verifikation") nennt für den "Basic"-Modus die
Locales en-US, fr-FR, it-IT, de-DE, es-ES, hi-IN, ja-JP, pt-BR, tr-TR,
pl-PL, cmn-Hans-CN, ko-KR, cmn-Hant-TW, ru-RU, vi-VN. **Ukrainisch, Arabisch
und Persisch fehlen** (Arabisch gibt es nur im "Advanced"-Modus, der laut
Doku exklusiv auf Pixel 10/11 läuft). Naheliegend wäre für diese Sprachen
die **geräteinterne Android-Systemerkennung**
(`SpeechRecognizer.createOnDeviceSpeechRecognizer`, Android 12+) gewesen -
der einzige Weg dorthin, der eine geräteinterne Verarbeitung garantiert
(der ältere `createSpeechRecognizer` + `EXTRA_PREFER_OFFLINE` "bevorzugt"
offline nur und könnte Audio doch an einen Cloud-Dienst schicken, was das
Datenschutz-Requirement verletzen würde). Ein **Praxistest** hat aber
gezeigt, dass die Systemerkennung des Testgeräts für Ukrainisch/Arabisch per
`checkRecognitionSupport` explizit "nicht unterstützt" zurückliefert - dieser
Weg war also kein verlässlicher Ersatz. Die Lösung ist [Vosk](https://alphacephei.com/vosk)
(Apache-2.0, `com.alphacephei:vosk-android`): eine direkt in die App
gebündelte Offline-Erkennung, die unabhängig vom Gerätehersteller
funktioniert, solange das Modell einmal heruntergeladen wurde.

Zusätzlich gilt: ML Kit GenAI Speech Recognition Basic-Modus ist laut
Google-Doku "generally available on most Android devices with API level 31
and higher" - `SpeechEngine.isLiveSupported()` prüft das und deaktiviert die
Sprechtasten für die ML-Kit-Sprachen unterhalb davon. Vosk hat diese
Einschränkung nicht (reine Kotlin/JNA-Bibliothek ohne Android-Systemdienst)
und läuft bereits ab `minSdk 26`.

Fehlt für eine Seite die Sprechtaste (Vosk-Modell nicht geladen, Android zu
alt), steht der Grund seit Version 1.2 in Klartext auf der Mitarbeiterseite
("Für Ukrainisch ist das Sprachmodell noch nicht geladen … Logo antippen →
Sprachpakete") - vorher verschwand die Taste kommentarlos.

## Splitscreen & ViP-Branding

Im Kundencenter stehen sich Mitarbeiter:in und Kund:in frontal gegenüber -
das UI ist deshalb ein **Splitscreen**:

- **Beide Hälften sind identisch aufgebaut** (Optik-Feedback aus dem
  Praxistest eingearbeitet): oben eine **grüne Kopfleiste** mit dem ViP-Logo
  links (in einem weißen Kreis - ein Antippen öffnet den
  Einrichtungs-Assistenten) und der **Sprachauswahl als weiße Pille oben
  rechts**; unten links die **große runde Sprechtaste** in ViP-Grün. Wer eine
  Taste sucht, findet sie auf beiden Seiten an derselben Stelle. Die
  Kopfleiste ist seit 1.3 auf beiden Seiten einzeilig: Die
  Mitarbeiter-Aktionen (Lautsprecher, Schnellbausteine) sitzen als runde
  Icons neben dem Logo, "Nächster Kunde" ist nach unten rechts gewandert -
  als Gegenstück zur Sprechtaste, beide in Daumenreichweite. Vorher brauchte
  die Mitarbeiterseite zwei Zeilen, weil "Nächster Kunde" mit hineinmusste
  und die Sprachtaste sonst buchstabenweise umbrach (Praxistest-Foto); die
  Pille schneidet jetzt notfalls ab.
- **Obere Hälfte (Kundenseite):** um 180° gedreht, sodass das Gegenüber alles
  in seiner Leserichtung sieht ("unten links" heißt hier: aus Kundensicht).
  Unter der Kopfleiste eine Begrüßung in der Kundensprache
  (`Language.greeting`), live mitlaufende Spracherkennung (wenn der Kunde
  spricht) und der Konversationsverlauf mit der Kundensprache prominent.
  Jeder Eintrag hat einen Vorlesen-Button. Die Sprechtaste ist in der
  Kundensprache beschriftet (`Language.tapToSpeak`).
- **Sprachauswahl oben rechts, pro Seite:** Ein kompakter weißer Button in
  der Kopfleiste; Antippen blendet darunter eine horizontal scrollbare
  Chip-Reihe mit allen Sprachen ein (`PaneHeader`). Auf der Kundenseite in
  den jeweils **eigenen Sprachnamen** (`Language.nativeName`, z. B. "Türkçe",
  "Русский"), auf der Mitarbeiterseite in den **deutschen Bezeichnungen**
  (`Language.displayName`). Bewusst kein `DropdownMenu`/Popup: ein Popup
  würde die 180°-Drehung der Kundenhälfte nicht mitmachen und stünde dort
  verkehrt herum bzw. falsch positioniert.
- **Sprechtaste unten links auf beiden Seiten** (Praxis-Feedback: ein
  zentraler Richtungs-Umschalter führte zu Fehlbedienung): Wer seine Taste
  drückt, bestimmt die Übersetzungsrichtung - niemand muss umschalten.
  Während der Aufnahme färbt sich die Taste rot. Bei Sprachen ohne
  Live-Modus (Ukrainisch, Arabisch) wird die Kundenseiten-Sprechtaste
  ausgeblendet, die Mitarbeiter-Sprechtaste bleibt sichtbar, aber gedämpft
  dargestellt und deaktiviert. **Kein Textfeld und keine Übersetzen-Tasten
  für getippten Text** - die App konzentriert sich bewusst aufs Sprechen;
  Konsequenz für Ukrainisch/Arabisch siehe "Offene Punkte".
- **Verlauf als Sprechblasen:** Beiträge der eigenen Seite stehen rechts in
  Grün, die der Gegenseite links in Weiß - auf beiden Hälften aus der
  jeweils eigenen Perspektive. Hauptzeile in der Sprache der Hälfte, darunter
  klein das Gegenstück; die Mitarbeiterseite zeigt zusätzlich das Sprachpaar
  und, falls der Fachwortschatz eingegriffen hat, "wörtlich erkannt: …".
  Jede Blase hat einen Vorlesen-Knopf. Auf Tablets sind Blasen auf 560 dp
  begrenzt, damit die Chat-Anordnung erhalten bleibt.
- **Mitarbeiterseite zusätzlich:** Status- und Fehlermeldungen erscheinen
  als **Banner mit Symbol** direkt unter der Kopfleiste (Fehler rot, Hinweis
  orange, "Aufnahme läuft" mit pulsierendem roten Punkt und mitlaufendem
  Text), bewusst kompakt, damit mehrere Banner den Verlauf nicht verdrängen.
- **Farben:** Die App nutzt die **offizielle ViP-Palette "Verkehr (ViP)"**
  aus dem SWP-Markenportal (`ui/theme/Color.kt`, Namen wie dort vergeben):
  `$waldgruen` **#006A4D** (Leistungsbereich → Primärfarbe: Kopfleisten,
  Sprechtasten), `$seegruen` **#008963** (Hell Kontrast → Akzente,
  Abschnittsüberschriften), `$dunkelgruen` **#09503D** (Dunkel Kontrast →
  Fließtext auf Karten), `$aquamarine` **#DEF3F0** (Sonderfarbe →
  Bildschirmhintergrund). Karten in Weiß mit feinem Rahmen statt Schatten;
  das dunkle Theme leitet seine Flächen aus Dunkelgrün ab. Die **vier
  Farben des Windrads** (Rot #D5232A, Orange #F39200, Blau #1467A8, Grün
  #41A62A aus `vip_logo.xml`) dienen als Statusakzente - Rot für "Aufnahme
  läuft" und Fehler, Orange für Hinweise, Grün für "bereit" - statt des
  Material-Standardrots. Der Launcher-Icon-Hintergrund ist Waldgrün, das
  Launcher-Symbol ein weißes Windrad.
- **Schrift:** Inter (SIL Open Font License, Lizenztext in
  `app/fonts-license/OFL-Inter.txt`), als variable Schrift gebündelt
  (`res/font/inter_variable.ttf`, 0,9 MB). Gewählt, weil sie mit Abstand gut
  lesbar ist und Latein, Kyrillisch, Griechisch und Vietnamesisch abdeckt;
  für Arabisch, Persisch, Hindi und die ostasiatischen Schriften fällt
  Android automatisch auf die Systemschrift zurück. Die Typo-Skala
  (`ui/theme/Type.kt`) ist etwas größer als die Material-Defaults, weil am
  Schalter mit Abstand gelesen wird.
- **Logo:** `res/drawable/vip_logo.xml` ist eine **stilisierte
  Vektor-Annäherung** an das offizielle Windrad, nach der
  Original-Vorlage aufgebaut: vier gebogene Ringsegmente mit Lücken -
  Rot (oben links), Blau (oben rechts), Orange (unten rechts), Grün
  (unten links). Das Original hat zusätzlich geschwungene Lücken und
  Farbverläufe - für das finale Branding bitte das offizielle Logo aus dem
  SWP-Markenportal als Vector-Asset importieren (Android Studio: File >
  New > Vector Asset) und diese Datei ersetzen - dabei auch
  `ic_launcher_foreground.xml` (weiße Variante fürs App-Icon) angleichen.

## Gestaltung & Screenshots

Die Oberfläche wurde für Version 1.3 von Grund auf neu gezeichnet - weg von
den Material-Standardbauteilen (lavendelfarbene Karten auf Aquamarin, nackte
rote Textzeilen als Fehlermeldung) hin zu einem eigenen, kleinen
Designsystem in `ui/components/VipComponents.kt`: Alles, was auf mehr als
einem Bildschirm vorkommt, steht dort genau einmal (Kopfleisten, Sprachpille,
Sprechtaste mit Pulsring, Sprechblasen, Statusbanner, Statuszeilen mit
Fortschrittsbalken, Karten, Knöpfe, Abzeichen, Leerzustände).

**Sehen statt raten:** Die Bildschirme werden per
[Paparazzi](https://github.com/cashapp/paparazzi) auf der JVM gerendert -
ohne Gerät, ohne Emulator. `app/src/test/.../ScreenshotTest.kt` füllt sie
mit erfundenen Gesprächsdaten (keine Engine wird angefasst), und

```
gradle recordPaparazziDebug
```

legt die PNGs unter `app/src/test/snapshots/images/` ab: Gespräch (leer,
mit Beiträgen und laufender Aufnahme, mit Hinweisen, dunkel, Tablet),
Einrichtung, Sprachpakete (hell/dunkel), Schnellbausteine, Fachbegriffe.
Die Bilder sind eingecheckt, damit der aktuelle Stand auch ohne Build
sichtbar ist, und der CI-Workflow legt sie bei jedem Lauf zusätzlich als
Artefakt `vip-live-uebersetzer-screenshots` ab. Dafür mussten die
Bildschirme zustandslos werden (`ConversationUiState`, `PackageCardState`,
`SetupContent`, `GlossaryContent`) - eine Trennung, die `MainActivity.kt`
ohnehin gutgetan hat.

## Einrichtung, Nächster Kunde & Schnellbausteine

Diese drei Bausteine richten sich an das Personal am Schalter, nicht an
technisch versierte Nutzer:

- **Einrichtungs-Assistent** (`SetupScreen.kt`, per Klick auf das Logo; beim
  allerersten Start automatisch): sagt in Klartext, ob das Gerät einsatzbereit
  ist. Mikrofonfreigabe (mit Knopf zum Nachholen) und pro Sprache, ob
  Übersetzung, Offline-Stimme und Spracheingabe vorhanden sind - inklusive
  Zählerzeile "X von 16 Sprachen vollständig einsatzbereit". Hintergrund: Die
  App braucht inzwischen Berechtigungen, 16 Übersetzungsmodelle, TTS-Stimmen
  und für Ukrainisch/Arabisch mehrere hundert MB Vosk-Modelle - von außen ist
  nicht erkennbar, was davon fehlt. Genau daran blieb ein nicht geladenes
  Vosk-Modell lange unbemerkt. Der Bildschirm ist zugleich das Menü zu
  Sprachpaketen, Fachbegriffen und Schnellbausteinen.
- **"Nächster Kunde"** (Taste unten rechts auf der Mitarbeiterseite, ersetzt
  den früheren Papierkorb): löscht den Verlauf, stoppt eine laufende Aufnahme und
  setzt beide Sprachen auf Standard zurück. Zusätzlich räumt sich der Verlauf
  **nach fünf Minuten ohne neuen Beitrag von selbst ab** - ein
  Datenschutz-Sicherheitsnetz, falls das Zurücksetzen vergessen wird, denn
  kein Kunde soll das Gespräch des Vorgängers sehen. Solange ein Gespräch
  läuft (Verlauf nicht leer oder Mikrofon offen), bleibt das **Display an**;
  nach dem Abräumen darf das Gerät wieder normal abdunkeln.
- **Zurück-Taste und Drehen:** Die Android-Zurück-Taste führt in den Menüs
  eine Ebene zurück statt die App zu beenden. Drehen des Geräts, Umschalten
  des dunklen Modus oder Einklappen einer Tastatur erzeugen die Activity
  nicht mehr neu (`android:configChanges` im Manifest) - vorher war damit
  der komplette, bewusst nur im Arbeitsspeicher gehaltene Verlauf weg.
- **Schnellbausteine** (`StaffPhrases.kt` / `PhrasesScreen.kt`, Listen-Symbol
  in der Kopfleiste): häufige Schaltersätze, die auf Tastendruck übersetzt,
  vorgelesen und in den Verlauf eingetragen werden. **Die mitgelieferte
  Satzliste ist ausdrücklich eine Startauswahl und stammt nicht aus den echten
  Abläufen des ViP-Kundencenters** - welche Sätze dort wirklich gebraucht
  werden, wissen nur die Kolleg:innen am Schalter. Anpassen heißt: Einträge in
  `StaffPhrases.kt` ändern, mehr nicht. Die Sätze sind auf Deutsch formuliert
  und werden immer aus dem Deutschen übersetzt.

## Tap-to-Talk & Sprachpakete-Menü

- **Tap-to-Talk (Fix aus dem Praxistest):** Bei dauerhaft offenem Mikrofon
  wurde die eigene Sprachausgabe wieder als Eingabe erkannt
  (Rückkopplungsschleife). Deshalb stoppt die Aufnahme jetzt **automatisch
  nach dem ersten fertigen Satz**, bevor die Übersetzung vorgelesen wird.
  Zusätzlich: Beim Öffnen des Mikrofons wird eine laufende Sprachausgabe
  abgebrochen, und gesprochen wird grundsätzlich nie, solange das Mikrofon
  offen ist. Für den nächsten Satz das Mikrofon einfach erneut antippen.
- **Sprachpakete-Menü (über Logo → Einrichtung erreichbar):** Pro Sprache lassen sich
  Übersetzungsmodell und Live-Erkennungsmodell **vorab herunterladen** -
  einmalig mit Internet vorbereiten, danach entsteht am Schalter keine
  Wartezeit durch spontane Modell-Downloads. Der Status pro Sprache wird
  angezeigt: Übersetzung (über `RemoteModelManager`), ML-Kit-Erkennungsmodell
  (über `SpeechRecognizer.checkStatus()` - bisher galt die ML-Kit-Erkennung
  pauschal als bereit, und der erste Mikro-Tap am Schalter löste dann den
  Download aus) und Vosk-Modell. **Downloads laufen weiter, wenn das Menü
  verlassen wird** (`ModelDownloads.kt`): Vorher liefen sie im Scope des
  Menüs und wurden beim Tipp auf "Zurück" still abgebrochen - beim 318-MB-
  Modell für Arabisch hieß das: von vorn, ohne Hinweis. Fehler werden pro
  Teil gemeldet; ein gescheitertes Erkennungsmodell macht eine fertige
  Übersetzung nicht mehr rückgängig.
- **Auto-Scroll:** Beide Verlaufslisten (Kunden- und Mitarbeiterseite)
  scrollen bei jedem neuen Beitrag automatisch zum aktuellsten Eintrag.

## Vosk-Modelle (Ukrainisch, Arabisch, Persisch - und als Alternative für zh/ja/ko/hi)

Die Live-Erkennung für Ukrainisch, Arabisch und Persisch läuft über
[Vosk](https://alphacephei.com/vosk) (`com.alphacephei:vosk-android:0.3.75`,
Apache-2.0), direkt in die App gebündelt statt über einen
Android-Systemdienst - siehe "Sprachcoverage" oben für die Begründung. Für
Chinesisch, Japanisch, Koreanisch und Hindi ist Vosk der am Testgerät
erprobte Weg und hat Vorrang, sobald das Modell geladen ist; ohne Modell
greift ML Kit.

- **Modelle werden nicht in der APK mitgeliefert** (würde Debug-Builds
  unhandlich groß machen), sondern im Menü "Sprachpakete" heruntergeladen.
  Alle URLs und Größen sind per HTTP-HEAD gegen die echten, von
  [alphacephei.com](https://alphacephei.com/vosk/models) ausgelieferten
  Dateien verifiziert (der Host ist aus der Entwicklungs-Sandbox nicht
  erreichbar, siehe "Build-Verifikation" - die Prüfung lief über einen
  temporären Schritt im echten CI-Workflow):

  | Sprache | Modell | Größe |
  |---|---|---:|
  | Chinesisch | `vosk-model-small-cn-0.22` | ~41 MB |
  | Hindi | `vosk-model-small-hi-0.22` | ~42 MB |
  | Japanisch | `vosk-model-small-ja-0.22` | ~47 MB |
  | Persisch | `vosk-model-small-fa-0.5` | ~59 MB |
  | Koreanisch | `vosk-model-small-ko-0.22` | ~82 MB |
  | Ukrainisch | `vosk-model-small-uk-v3-small` | ~137 MB |
  | Arabisch | `vosk-model-ar-mgb2-0.4` | ~318 MB |

  Die Modelle werden **einzeln pro Sprache** geladen - es muss nie alles auf
  ein Gerät. Vosk nutzt für Chinesisch das Kürzel `cn`, die App bleibt beim
  ISO-Code `zh`.
- **Warum Arabisch kein "small"-Modell nutzt (Praxistest-Korrektur):**
  Ursprünglich war `vosk-model-small-ar-0.3` (~100 MB) vorgesehen - ein
  Praxistest zeigte, dass es zwar aufnimmt, aber praktisch nichts brauchbar
  erkennt (ein deutlich älteres/schwächeres Modell). Ukrainisch
  funktionierte mit seiner "small"-Variante dagegen einwandfrei. Für
  Arabisch kommt deshalb `vosk-model-ar-mgb2-0.4` zum Einsatz - Vosks
  etabliertes, auf dem MGB-2-Corpus arabischer Rundfunknachrichten
  trainiertes Modell - trotz des mehr als drei Mal so großen Downloads.
  Die Marker-Datei, die den Download-Status auf dem Gerät festhält,
  speichert dafür die jeweilige Modell-URL: Ändert sich diese Zuordnung
  (wie hier geschehen), gilt ein bereits heruntergeladenes altes Modell
  automatisch als veraltet und wird beim nächsten "Laden" ersetzt.
- **Größerer, einmaliger Download:** Die Statuszeile "Live-Erkennung: ..." im
  Sprachpakete-Menü zeigt den Fortschritt in Prozent (gezählt werden die
  heruntergeladenen, komprimierten Bytes - vorher die entpackten, weshalb
  die Anzeige über 100 % hinauslief); am besten über WLAN vorbereiten, bevor
  die Sprache am Schalter gebraucht wird. Anders als bei den ML-Kit-Sprachen
  wird hier **nicht** beim ersten Mikro-Tap spontan nachgeladen - dafür sind
  die Modelle zu groß, die Sprechtaste bleibt bis zum Abschluss des Downloads
  deaktiviert.
- **Stille am Anfang:** Vosk meldet nach wenigen Sekunden ohne Sprache ein
  leeres Ergebnis. Früher beendete das die Aufnahme mit "Nichts erkannt",
  oft bevor der Kunde überhaupt angefangen hatte. Leere Ergebnisse werden
  jetzt ignoriert, die Aufnahme läuft bis zum ersten Satz weiter - begrenzt
  durch Vosks eigenen Timeout von 30 s, danach kommt die Meldung zu Recht.
- **Bleibt vollständig on-device:** Nach dem einmaligen Download läuft die
  Erkennung komplett offline auf dem Gerät (Kaldi-Engine über JNA/native
  Bibliothek, in der AAR enthalten) - kein Unterschied zum
  Datenschutz-Anspruch der übrigen Sprachen.
- **Warum Thai, Indonesisch und Urdu (noch) fehlen:** ML Kit Translate
  könnte sie übersetzen, aber Vosk bietet für sie kein Modell an (per
  CI gegen den Modell-Katalog geprüft: 404). Ohne Spracherkennung wären
  sie eine Einbahnstraße - Mitarbeiter:in könnte etwas sagen, der Kunde
  aber nicht antworten. Da die App bewusst kein Texteingabefeld mehr hat,
  wurden sie deshalb nicht aufgenommen. Bei konkretem Bedarf wären die
  Optionen: ein kleines Texteingabefeld nur für solche Sprachen, oder eine
  andere Erkennungs-Bibliothek (z. B. Whisper-Ableger) zusätzlich einbinden.

## ÖPNV-Fachwortschatz (Erkennungs-Nachkorrektur)

Praxistest-Befund: Die Erkennung hat Probleme mit Eigennamen und typischem
ÖPNV-Vokabular ("Einzelfahrausweis", "Kassenautomat") - allgemeine
Sprachmodelle kennen weder Tarifbegriffe noch lokale Haltestellennamen.
Typische Fehlerbilder: deutsche Komposita werden zerlegt ("Kassen Automat"),
Eigennamen lautähnlich verschrieben ("Potstam").

`TransitGlossary.kt` schiebt deshalb zwischen Erkennung und Übersetzung eine
**Nachkorrektur**: Jeder fertig erkannte Satz wird gegen eine kuratierte
Wortliste abgeglichen (normalisierter Levenshtein-Vergleich), wobei auch
Fenster aus zwei und drei Wörtern geprüft werden - so werden zerlegte
Komposita und Mehrwort-Haltestellennamen ("Alter Markt", "Platz der Einheit")
wieder zusammengesetzt.

- **Verbessert zugleich die Übersetzung:** ML Kit Translate bekommt dann das
  korrekte Kompositum statt zweier Bruchstücke - "Einzelfahrausweis" wird
  brauchbar übersetzt, "Einzel Fahrausweis" nicht.
- **Nur Deutsch bekommt das volle Glossar.** Für alle anderen Sprachen wird
  ausschließlich die Eigennamen-Teilmenge angewendet (Orte, Haltestellen,
  Marken) - ein Kunde nennt die Haltestelle auch auf Englisch "Luisenplatz",
  aber deutsches Fachvokabular darf fremdsprachige Sätze nicht anfassen. Bei
  nicht-lateinischer Schrift (Ukrainisch, Arabisch) greift der Vergleich
  ohnehin nie.
- **Konservative Schwellen gegen Falschkorrekturen:** Einzelne Wörter ab
  sieben Zeichen werden unscharf verglichen (Ähnlichkeit ≥ 80 %), kürzere nur
  exakt. **Zusammengezogene Mehrwort-Treffer müssen exakt passen** - unscharf
  verschluckte die Korrektur sonst Nachbarwörter (siehe Tests unten). Wurde
  etwas geändert, zeigt die Mitarbeiterseite unter dem Beitrag den
  ursprünglichen Wortlaut ("wörtlich erkannt: ..."), damit eine Fehlkorrektur
  auffällt statt unbemerkt zu bleiben.
- **Eigene Begriffe direkt am Gerät:** Über Logo → "Fachbegriffe" lassen sich
  ViP-eigene Tarif-, Produkt- und Haltestellennamen ergänzen, ohne dass eine
  neue App-Version nötig ist. Sie gelten für alle Sprachen und wirken sofort.
- **Durch Tests abgesichert** (`app/src/test/.../TransitGlossaryTest.kt`, läuft
  in CI): Die Negativtests wiegen dabei schwerer als die Positivtests. Drei
  davon sind Regressionstests für echte, beim Entwickeln gefundene Fehler:
  "ich **fahre** nach Potsdam" wurde zu "ich **Fähre** nach Potsdam"
  (Umlaut-Faltung machte beide Wörter identisch), "wir **werden**" zu
  "wir **Werder**" (Ortsname, ein Buchstabe Unterschied) und
  "**Ein** Schwielowsee Ticket bitte" zu "Schwielowseeticket bitte" - dabei
  fiel das "Ein" weg, weil das Mehrwort-Fenster dem Begriff nur ähnlich genug
  sein musste. Verschluckte Wörter verfälschen die Aussage und wiegen
  schwerer als eine verpasste Korrektur; deshalb jetzt exakt-only.
- **Erweitern:** Begriffe einfach in `TransitGlossary.properNouns` bzw.
  `generalTerms` ergänzen - kein weiterer Code nötig. Die Listen sind eine
  **Startauswahl und kein amtlich geprüftes Haltestellen- oder
  Tarifverzeichnis**; vor dem Rollout sollte ViP sie gegen die eigenen Daten
  abgleichen und um die real gebräuchlichen Begriffe erweitern.

Was diese Nachkorrektur **nicht** kann: die *Übersetzung* eines Fachbegriffs
erzwingen. ML Kit Translate bietet keine Glossar-/Terminologie-Funktion, d. h.
wie "Einzelfahrausweis" auf Arabisch heißt, entscheidet weiterhin allein das
Übersetzungsmodell. Auch Eigennamen können dabei mitübersetzt werden
("Alter Markt" → "Old Market"). Falls das am Schalter stört, wäre der nächste
Ausbauschritt eine feste Begriffstabelle, die solche Terme nach der
Übersetzung wieder zurücksetzt.

## Konversationsverlauf, Sprachausgabe & Übersetzer-Lebenszyklus

- **Konversationsverlauf:** Jeder abgeschlossene Beitrag (Original,
  Übersetzung, Sprachrichtung) wandert in eine Chat-artige Liste und bleibt
  beim Sprachwechsel bzw. Richtungstausch vollständig erhalten - jeder
  Eintrag trägt sein eigenes Sprachpaar. Der Verlauf lebt bewusst **nur im
  Arbeitsspeicher** (nichts wird persistiert) und lässt sich über den
  Papierkorb-Button in der Titelleiste leeren - am Schalter vor dem nächsten
  Kunden zu empfehlen.
- **Sprachausgabe:** Übersetzungen werden über die **systemeigene
  Android-TTS-Engine** (`android.speech.tts`, siehe `SpeechOutput.kt`)
  vorgelesen - ein lokaler Systemdienst, keine Dritt-Cloud-API aus der App
  heraus. Der Lautsprecher-Button in der Kopfleiste schaltet die
  automatische Ausgabe um; jeder Verlaufseintrag hat zusätzlich einen
  eigenen Vorlesen-Button. Verbesserungen: Die App wählt pro Sprache
  automatisch die **beste installierte Offline-Stimme** (höchste
  Qualitätsstufe, Netz-Stimmen werden bewusst ignoriert), spricht mit leicht
  reduziertem Tempo (0.9× - am Schalter verständlicher) und nutzt den vollen
  Locale-Tag (z. B. `de-DE` statt nur `de`) für eine passendere Stimmwahl.
  Fehlt für eine Sprache jede Stimme, bietet die Mitarbeiterseite einen
  Button **"Sprachausgabe-Einstellungen öffnen"** an, der direkt zu den
  Android-TTS-Einstellungen führt (dort Offline-Stimmen nachinstallieren) -
  mit einer Meldung, die die Ursache nennt (Engine startet noch / nur
  Online-Stimme / keine Stimme). Bleibt eine Übersetzung im Gespräch stumm,
  erscheint dieselbe Meldung; vorher schwieg die App ohne Erklärung.
  **Datenschutz-Absicherung:** Sind für eine Sprache ausschließlich
  Netz-Stimmen installiert, bleibt die Ausgabe bewusst stumm - eine
  Netz-Stimme würde den zu sprechenden Text (also Gesprächsinhalte) an den
  TTS-Cloud-Dienst übertragen. Das Menü "Sprachpakete" zeigt diesen Fall
  explizit an. Stimmen, deren Sprachdaten die Engine zwar listet, aber noch
  nicht heruntergeladen hat (Google TTS kennzeichnet sie mit dem Feature
  `notInstalled`), zählen dabei nicht als Offline-Stimme.
- **Stimmen-Check im Sprachpakete-Menü:** Pro Sprache zeigt das Menü neben
  dem Übersetzungs-Status auch die **Stimmen-Lage der Sprachausgabe**
  (Offline-Stimme bereit / nur Online-Stimme / keine Stimme), einen
  **Probehören**-Button (spricht die Begrüßung in der Sprache - so lässt
  sich die Ausgabe vor dem Kundengespräch testen) und bei fehlender Stimme
  einen **"Stimme installieren"**-Button direkt in die
  Android-TTS-Einstellungen. Gedacht als Einrichtungs-Checkliste: einmal
  durchgehen, alle 16 Zeilen grün bekommen.
- **Übersetzer-Lebenszyklus (Fix "Translation closed"):** Ursprünglich wurde
  der ML-Kit-Translator bei jedem Sprachwechsel sofort geschlossen - lief
  dabei noch eine Übersetzung (oder der Live-Modus benutzte ihn noch),
  schlug sie mit "Translation closed" fehl. `TranslationEngine` hält
  Translator jetzt in einem kleinen `LruCache` pro Sprachpaar (das von der
  ML-Kit-Doku empfohlene Muster) und schließt sie erst bei Verdrängung bzw.
  beim Verlassen des Screens (`closeAll()`). Ein Quellsprachwechsel während
  einer laufenden Live-Aufnahme stoppt die Aufnahme jetzt außerdem sauber,
  da der Recognizer fest auf seine Startsprache gebunden ist.

## Der frühere Platzhalter `response.toString()`

`SpeechEngine.listen()` verarbeitet den `Flow<SpeechRecognizerResponse>` aus
`SpeechRecognizer.startRecognition()` jetzt korrekt über ein `when` auf die
vier tatsächlichen Antworttypen (Paket `com.google.mlkit.genai.speechrecognition`):

- `SpeechRecognizerResponse.PartialTextResponse` - vorläufiger Text, über `.text`
- `SpeechRecognizerResponse.FinalTextResponse` - finaler Text eines Segments, über `.text`
- `SpeechRecognizerResponse.CompletedResponse` - Stream-Ende, kein Text
- `SpeechRecognizerResponse.ErrorResponse` - Fehlerfall, über `.e` (eine `GenAiException`)

**Diese Namen sind gegen die echte Alpha-AAR verifiziert**, nicht nur aus
Doku-Snippets rekonstruiert: `developers.google.com` war für diese Session
durchgehend blockiert (HTTP 403, sowohl direkt als auch über WebFetch), daher
enthielt der CI-Workflow zwischenzeitlich einen Diagnose-Schritt
("ML-Kit-GenAI-Speech-API introspizieren"), der die von Gradle aufgelöste AAR
mit `javap -p` decompiliert und die echten Methodensignaturen ins Build-Log
geschrieben hat (mittlerweile aus dem Workflow entfernt, da sein Zweck erfüllt
ist - das Ergebnis ist hier dokumentiert). Dabei zeigte sich, dass die
anfängliche (aus Suchmaschinen-Snippets rekonstruierte) Annahme an mehreren
Stellen falsch war:

| Angenommen | Tatsächlich (per `javap`) |
|---|---|
| `checkFeatureStatus(): Task<Int>` | `checkStatus(): Int` (suspend, kein `Task`) |
| `downloadFeature(callback): Task<Void>` | `download(): Flow<DownloadStatus>` (kein Callback, keine Parameter) |
| `ErrorResponse` unbekannter Aufbau | `ErrorResponse(val e: GenAiException)` |
| `SpeechRecognizerOptions.Mode` als Enum | `Mode` ist ein `@IntDef`-Interface mit `Int`-Konstanten |

`DownloadStatus` (aus `genai-common 1.0.0-beta3`) ist seit Version 1.2
ebenfalls per `javap` gegen die echte AAR verifiziert - diesmal lokal, weil
die Sandbox dieser Session Google Maven erreichen konnte (siehe
"Build-Verifikation"): eine sealed class mit `DownloadStarted`,
`DownloadProgress(totalBytesDownloaded: Long)`, `DownloadFailed(e:
GenAiException)` und `DownloadCompleted`. `ensureModelDownloaded()` wertet
den Flow jetzt aus: Fortschritt in MB (Sprachpakete-Menü und Hinweis beim
ersten Mikro-Tap), ein `DownloadFailed` wird als Fehler gemeldet statt erst
beim Sprechen kryptisch aufzufallen. Eine Gesamtgröße nennt ML Kit nicht.

## Build-Verifikation

**Status: `gradle testDebugUnitTest`, `gradle recordPaparazziDebug` und
`gradle assembleDebug` sind grün.** Der CI-Workflow
[`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml) baut das
Projekt auf einem GitHub-Actions-Runner zu einer Debug-APK (Artifact
`vip-live-uebersetzer-debug`), rendert die Screenshots (Artifact
`vip-live-uebersetzer-screenshots`) und bleibt die verbindliche Prüfung.

**Version 1.2 wurde zusätzlich lokal gebaut:** Die Sandbox dieser Session
konnte - anders als alle früheren - `dl.google.com`, Google Maven, Maven
Central und `developers.google.com` erreichen. Android-SDK (Platform 34,
Build-Tools 34.0.0) per `sdkmanager` installiert, Gradle 8.14.3 (vorinstalliert;
CI nutzt weiterhin 8.7), JDK 21. Einzige Hürde: Maven Central weist Gradles
parallele Downloads mit HTTP 429 ab - gelöst über ein lokales Init-Script,
das Googles Maven-Central-Spiegel
(`maven-central.storage-download.googleapis.com`) voranstellt; das Script
liegt bewusst **nicht** im Repo, die Projekt-Repositories sind unverändert.
Damit liefen Unit-Tests und `assembleDebug` erstmals vor dem Push durch, und
die ML-Kit-AARs konnten direkt aus dem Gradle-Cache introspiziert werden
(`DownloadStatus`, siehe oben).

**CI-Workflow repariert (10/2026):** Der erste CI-Lauf von 1.2 brach vor
dem ersten Gradle-Aufruf ab - nicht am Code, sondern an
`android-actions/setup-android@v3`: Die Action installiert standardmäßig
das SDK-Paket `tools`, das Google inzwischen aus dem Repository entfernt
hat ("Failed to find package 'tools'"). Der letzte grüne Lauf war im Juli.
Da das `ubuntu-latest`-Runner-Image das Android-SDK samt Platform 34 und
Build-Tools 34.0.0 ohnehin vorinstalliert mitbringt (`ANDROID_HOME`
gesetzt), ist die Action ersatzlos gestrichen; ein idempotenter
`sdkmanager`-Aufruf stellt die beiden Pakete nur noch sicher.

**Warum ursprünglich CI statt eines lokalen Builds:** Die Entwicklungs-Sandbox,
in der dieses Projekt geschrieben wurde, hatte eine Egress-Policy, die u. a.
`dl.google.com`, `maven.google.com` und `developers.google.com` blockiert
(HTTP 403 auf allen drei Hosts, verifiziert). Damit war es dort nicht möglich,
- Android-SDK-Plattformen/Build-Tools per `sdkmanager` zu installieren,
- AndroidX/Jetpack-Compose-Artefakte aufzulösen (nur auf Google Maven
  gehostet, kein Mirror auf Maven Central),
- ML-Kit-Artefakte (Translate, GenAI Speech Recognition) aufzulösen,
- oder die offizielle Gradle-Distribution zu laden (der Download von
  `services.gradle.org` leitet auf einen GitHub-Release-Download um, der in
  dieser Sandbox ebenfalls blockiert war - deshalb liegt hier **kein
  `gradlew`/`gradle-wrapper.jar`** im Repo, siehe unten).

Ein echter `gradle assembleDebug` war in dieser Sandbox also grundsätzlich
nicht durchführbar - unabhängig vom Anwendungscode. Der Weg zu einem grünen
Build:

1. Kotlin-Kompilierprüfung des Anwendungscodes gegen lokal geschriebene Stubs,
   die die recherchierte API-Form von ML Kit Translate/GenAI Speech
   Recognition sowie Jetpack Compose nachbilden (Kotlin-2.0.21-Compiler aus
   der lokalen Gradle-Distribution, da kein `kotlinc` direkt verfügbar war).
   Fing einen echten Bug ab (`return@TypedModePanel`-Label in einer Lambda an
   eine nicht-`inline`-Funktion), aber prüft naturgemäß nicht, ob die
   recherchierte API-Form selbst stimmt.
2. Erster echter CI-Lauf: schlug fehl, weil `genai-speech-recognition` selbst
   transitiv ein deutlich neueres Kotlin verlangt als angenommen (siehe
   Versionsmatrix unten) - ein reiner Versionskonflikt, keine Code-Änderung.
3. Nach der Versionskorrektur schlug CI mit echten Kotlin-Compile-Fehlern
   fehl: `ExposedDropdownMenu` (Compose) existiert in der aufgelösten
   Material3-Version nicht, und `SpeechEngine.kt`s angenommene ML-Kit-API
   (`checkFeatureStatus`, `downloadFeature`) stimmte nicht mit der echten
   AAR überein. Ein temporärer Diagnose-Schritt im CI-Workflow hat die
   tatsächlichen Klassen mit `javap -p` decompiliert (siehe Abschnitt oben) -
   das lieferte die exakten echten Methodensignaturen.
4. Mit diesen echten Signaturen gefixt, erneut per CI verifiziert: **grün.**
   Der Diagnose-Schritt wurde danach wieder aus dem Workflow entfernt, da sein
   Zweck erfüllt ist.

Dieser Verlauf ist der Grund, warum der CI-Lauf die verbindliche
Verifikation für dieses Projekt ist (wie in der Aufgabenstellung als
Fallback vorgesehen) und keine rein lokale/Doku-basierte Prüfung ausreicht:
mehrere reale Fehler (Versionskonflikt, zwei falsch angenommene APIs) wurden
ausschließlich durch den echten Build mit echtem Netzzugriff sichtbar.

### Gradle-Wrapper nachträglich ergänzen

Sobald jemand mit normalem Internetzugriff (z. B. lokal oder in Android
Studio) am Projekt arbeitet, genügt einmalig:

```
gradle wrapper --gradle-version 8.7 --distribution-type bin
```

um `gradlew`, `gradlew.bat` und `gradle-wrapper.jar` zu erzeugen und
einzuchecken.

## Versionsmatrix

Ausgangspunkt war AGP 8.5.2 / Kotlin 1.9.24 / Compose BOM 2024.06.00 /
Compose-Compiler-Extension 1.5.14. Das war ein zweistufiger Prozess:

**1. Dokumentationsprüfung (vor dem ersten CI-Lauf):** Anhand der offiziellen
[AGP/Kotlin-Kompatibilitätstabelle](https://developer.android.com/build/kotlin-support)
(Stand 2026-07-06) schien Kotlin 1.9 auf AGP 7.4.2-8.2 begrenzt, während AGP
8.5.2 mindestens Kotlin 2.0 verlangt. Da die Compose-Compiler-Extension 1.5.14
exakt an Kotlin 1.9.24 gebunden ist, wurde in einer ersten Version AGP auf
8.2.2 heruntergezogen statt Kotlin/Compose-Compiler hochzuziehen.

**2. Korrektur durch den echten CI-Build:** Der erste `gradle assembleDebug`-
Lauf in CI schlug an `:app:compileDebugKotlin` fehl - nicht wegen der App-
Logik, sondern weil `com.google.mlkit:genai-speech-recognition:1.0.0-alpha1`
selbst transitiv **`kotlin-stdlib:2.2.20`** zieht:

```
e: Class 'kotlin.Unit' was compiled with an incompatible version of Kotlin.
   The actual metadata version is 2.2.0, but the compiler version 1.9.0
   can read versions up to 2.0.0.
```

Diese Alpha-API ist selbst bereits mit einem deutlich neueren Kotlin gebaut,
als die (per Doku-Tabelle plausible) 1.9.24-Wahl vorsah - genau die Art von
Erkenntnis, die nur ein echter Build mit echtem Netzzugriff liefern kann,
keine Versionstabelle. Reaktion: Kotlin auf **2.2.20** hochziehen (passend
zur bereits transitiv gezogenen Stdlib-Version), womit AGP 8.5.2 wieder
gültig wird (Kotlin 2.2 erlaubt laut Tabelle AGP 7.3.1-8.10) - **AGP konnte
also auf den ursprünglich vorgesehenen Wert zurückgesetzt werden**. Die alte
`composeOptions.kotlinCompilerExtensionVersion`-Mechanik (K1) existiert ab
Kotlin 2.0 nicht mehr; sie wurde durch das offizielle
`org.jetbrains.kotlin.plugin.compose`-Gradle-Plugin (K2, Version = Kotlin-
Version) ersetzt.

Finale Versionen:

| Komponente | Wert |
|---|---|
| Android Gradle Plugin | 8.5.2 (unverändert - siehe oben, zwischenzeitlich auf 8.2.2 herunter- und wieder hochgezogen) |
| Kotlin | **2.2.20** (↑ von 1.9.24, durch echten CI-Fehler erzwungen) |
| Compose-Compiler-Mechanik | **`org.jetbrains.kotlin.plugin.compose` 2.2.20** (↑ ersetzt die K1-Extension 1.5.14, die es ab Kotlin 2.0 nicht mehr gibt) |
| Compose BOM | 2024.06.00 (unverändert; ggf. bei weiteren CI-Fehlern nochmals prüfen) |
| Gradle | **8.7** (↑ von 8.2, Mindestversion für AGP 8.5.2) |
| compileSdk / targetSdk | 34 (unverändert) |
| minSdk | 26 (unverändert, hartes Requirement) |
| JDK (Gradle/AGP) | 17 |

Diese Versionsmatrix ist mit dieser Kombination gegen einen echten, grünen
`gradle assembleDebug`-Lauf in CI verifiziert (siehe "Build-Verifikation").

## Debug-Signierung

`app/debug.keystore` ist bewusst eingecheckt (kein Secret - Debug-Keystores
sind für genau diesen Zweck gedacht, geteilt zu werden) und in
`app/build.gradle.kts` als fester `signingConfigs.debug` verdrahtet. Ohne das
würde jede frische CI-Umgebung (jeder Workflow-Lauf startet in einer leeren
VM) einen eigenen zufälligen Debug-Key erzeugen - Tester könnten dann eine
neuere Debug-APK nicht über eine ältere installieren ("App nicht installiert"
wegen Signatur-Konflikt), ohne die alte Version vorher zu deinstallieren.

## Offene Punkte

- **Ukrainisch/Arabisch-Eingabe: am Testgerät bestätigt** (07/2026), auf
  weiteren Geräten vor dem Rollout gegenprüfen. Praxistest-Historie in
  Kurzform: Die Android-Systemerkennung meldete für beide Sprachen "nicht
  unterstützt" → Umstieg auf die gebündelte Offline-Erkennung Vosk (siehe
  Abschnitt "Vosk-Modelle"). Ukrainisch funktionierte mit der
  "small"-Modellvariante auf Anhieb; Arabisch erst nach dem Wechsel vom
  small- auf das MGB2-Modell (das small-Modell nahm auf, erkannte aber
  praktisch nichts). Beide Sprachen laufen seitdem am Testgerät.
- **Sprachausgabe für Ukrainisch/Arabisch hängt an den installierten
  TTS-Stimmen:** Die App kann nur Stimmen nutzen, die die TTS-Engine des
  Geräts anbietet (bei Google Speech Services lassen sich Offline-Stimmen
  pro Sprache nachinstallieren). Laut Praxistest funktioniert das für beide
  Sprachen auf dem Testgerät bereits. Das Sprachpakete-Menü macht die Lage
  pro Sprache sichtbar (Probehören/Installieren), falls es auf anderer
  Hardware doch fehlt.
- **ML Kit für Chinesisch/Japanisch/Koreanisch/Hindi ist am Gerät noch
  nicht getestet.** Die aktuelle Google-Doku listet die vier im Basic-Modus,
  deshalb bietet die App sie dort an - der Basic-Modus nutzt aber die
  Offline-Erkennung des Systems, und ob die ViP-Hardware diese Sprachpakete
  hat, weiß nur ein Praxistest. Deshalb bleibt Vosk Vorrang, sobald geladen,
  und im Sprachpakete-Menü steht es als "eigenes Sprachmodell" bereit. Für
  Chinesisch verlangt ML Kit die Locale `cmn-Hans-CN` (so in
  `LanguageCatalog.kt` hinterlegt), die Sprachausgabe nutzt weiter `zh-CN`.
- **Portugiesisch** wäre ein Kandidat ohne Aufwand: ML Kit Translate und der
  Basic-Modus (`pt-BR`) decken es ab - eine Zeile in `LanguageCatalog.kt`,
  falls das Kundencenter Bedarf meldet.
- **ViP-Schalter-Hardware:** Die Android-Version der im Einsatz befindlichen
  Schalter-Hardware ist nicht bekannt. Relevant, weil ML Kit (13 Sprachen)
  Android 12 (API 31) voraussetzt, Vosk dagegen bereits ab `minSdk 26` läuft
  (siehe "Sprachcoverage"). Auf einem Gerät unter API 31 hätten also -
  solange kein Textfeld mehr existiert - nur die Vosk-Sprachen eine
  Live-Eingabemöglichkeit, Deutsch und Englisch keine. Sollte vor dem
  Rollout geklärt werden; die Mitarbeiterseite sagt es seit 1.2 immerhin in
  Klartext ("braucht Android 12 oder neuer").
- **Package-Name:** `de.vip.liveuebersetzer` (unverändert, hartes Requirement).
