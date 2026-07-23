# ViP Live-Übersetzer

Natives Android-Projekt (Kotlin, Jetpack Compose) für den Einsatz an
Kundenschaltern eines Verkehrsunternehmens: Text- und Live-Sprachübersetzung
für 11 Sprachen, vollständig on-device.

## Warum on-device?

Harte Anforderung: Kundendaten dürfen das Gerät zu keinem Zeitpunkt in
Richtung eines Drittanbieters verlassen. Deshalb:

- **Übersetzung** läuft über [ML Kit Translate](https://developers.google.com/ml-kit/language/translation)
  (`com.google.mlkit:translate`, stabil/GA). Nach einmaligem Download des
  Sprachmodellpaars läuft die Übersetzung komplett offline.
- **Live-Spracherkennung** läuft über [ML Kit GenAI Speech Recognition](https://developers.google.com/ml-kit/genai/speech-recognition/android)
  (`com.google.mlkit:genai-speech-recognition:1.0.0-alpha1`, **Alpha-Status**).
  Auch hier: einmaliger Modell-Download, danach reine On-Device-Inferenz.
- Es gibt **bewusst keinen Cloud-Fallback**, auch nicht für Sprachen ohne
  Live-Unterstützung. Die einzige Internetnutzung der App ist der einmalige
  Download der on-device-Modelle (`INTERNET`-/`ACCESS_NETWORK_STATE`-
  Berechtigung in `AndroidManifest.xml`) - nie die Übertragung von
  Kunden-Text oder -Audio.

## Architektur

Kernlogik in `app/src/main/java/de/vip/liveuebersetzer/`:

| Datei | Zweck |
|---|---|
| `LanguageCatalog.kt` | Die 11 unterstützten Sprachen, Anzeigenamen, ML-Kit-Sprachkonstanten, Live-Speech-Unterstützung pro Sprache |
| `TranslationEngine.kt` | Wrapper um ML Kit Translate (Translator-Erstellung, Modell-Download, `translate()`) |
| `SpeechEngine.kt` | Wrapper um ML Kit GenAI Speech Recognition (Recognizer-Erstellung, Modell-Download, `startRecognition()`-Flow, `isLiveSupported()`) |
| `MainActivity.kt` | Jetpack-Compose-UI: Sprachauswahl, getippter Modus, Live-Modus |

## Sprachcoverage

Übersetzung deckt alle 11 Sprachen ab. Live-Spracherkennung nur 9 davon:

| Sprache | Code | Übersetzung | Live-Spracherkennung |
|---|---|:---:|:---:|
| Deutsch | `de` | ✅ | ✅ |
| Englisch | `en` | ✅ | ✅ |
| Russisch | `ru` | ✅ | ✅ |
| Türkisch | `tr` | ✅ | ✅ |
| Polnisch | `pl` | ✅ | ✅ |
| Vietnamesisch | `vi` | ✅ | ✅ |
| Französisch | `fr` | ✅ | ✅ |
| Spanisch | `es` | ✅ | ✅ |
| Italienisch | `it` | ✅ | ✅ |
| Ukrainisch | `uk` | ✅ | ❌ (nur getippt) |
| Arabisch | `ar` | ✅ | ❌ (nur getippt) |

**Warum kein Live für Ukrainisch/Arabisch (bewusste Entscheidung, siehe
Kommentare in `LanguageCatalog.kt`/`SpeechEngine.kt`):** ML Kit GenAI Speech
Recognition listet im "Basic"-Modus kein Ukrainisch; Arabisch ist dort nur im
"Advanced"-Modus verfügbar, der laut Google-Doku (Stand Juli 2026) exklusiv
auf Pixel-10-Geräten läuft. Da die App auf der gesamten ViP-Gerätefotte
laufen soll und kein Cloud-Fallback infrage kommt, bleiben beide Sprachen
bewusst auf den getippten Modus beschränkt - unabhängig vom Gerät.

Zusätzlich gilt: ML Kit GenAI Speech Recognition Basic-Modus ist laut
Google-Doku "generally available on most Android devices with API level 31
and higher". `SpeechEngine.isLiveSupported()` prüft deshalb neben der
Sprachliste auch `Build.VERSION.SDK_INT >= 31` und deaktiviert den
Live-Button entsprechend - `minSdk 26` bleibt für den getippten Modus davon
unberührt.

## Der frühere Platzhalter `response.toString()`

`SpeechEngine.listen()` verarbeitet den `Flow<SpeechRecognizerResponse>` aus
`SpeechRecognizer.startRecognition()` jetzt korrekt über ein `when` auf die
drei tatsächlichen Antworttypen (Paket `com.google.mlkit.genai.speechrecognition`):

- `SpeechRecognizerResponse.PartialTextResponse` - vorläufiger Text, über `.text`
- `SpeechRecognizerResponse.FinalTextResponse` - finaler Text eines Segments, über `.text`
- `SpeechRecognizerResponse.CompletedResponse` - Stream-Ende, kein Text

Diese Namen stammen aus der öffentlichen ML-Kit-Referenzdokumentation
(`developers.google.com/android/reference/.../SpeechRecognizerResponse.*`).
Ein direktes Decompilieren der Alpha-AAR war in der Entwicklungs-Sandbox
dieser Session nicht möglich (siehe nächster Abschnitt) - die Recherche
erfolgte stattdessen über Suchmaschinen-Snippets der offiziellen Referenzseiten,
da ein direkter Seitenabruf dort mit HTTP 403 blockiert wurde. **Da die API
Alpha-Status hat, sollte dieser Teil beim ersten echten Build auf einem
Gerät mit Zugriff auf `dl.google.com` gegen die tatsächliche AAR
gegengeprüft werden.**

## Build-Verifikation

**Wichtiger Hinweis zu dieser Session:** Die Entwicklungs-Sandbox, in der
dieses Projekt geschrieben wurde, hat eine Egress-Policy, die u. a.
`dl.google.com`, `maven.google.com` und `developers.google.com` blockiert
(HTTP 403 auf allen drei Hosts, verifiziert). Damit war es nicht möglich,
- Android-SDK-Plattformen/Build-Tools per `sdkmanager` zu installieren,
- AndroidX/Jetpack-Compose-Artefakte aufzulösen (nur auf Google Maven
  gehostet, kein Mirror auf Maven Central),
- ML-Kit-Artefakte (Translate, GenAI Speech Recognition) aufzulösen,
- oder die offizielle Gradle-Distribution zu laden (der Download von
  `services.gradle.org` leitet auf einen GitHub-Release-Download um, der in
  dieser Sandbox ebenfalls blockiert war - deshalb liegt hier **kein
  `gradlew`/`gradle-wrapper.jar`** im Repo).

Ein echter `gradle assembleDebug` war in dieser Sandbox also grundsätzlich
nicht durchführbar - unabhängig vom Anwendungscode. Validiert wurde
stattdessen:

1. Eine Kotlin-Kompilierprüfung des Anwendungscodes gegen lokal geschriebene
   Stubs, die exakt die recherchierte API-Form von ML Kit Translate/GenAI
   Speech Recognition sowie Jetpack Compose nachbilden (Kotlin-2.0.21-Compiler
   aus der lokalen Gradle-Distribution, da kein `kotlinc` direkt verfügbar war).
   **Ergebnis: alle sieben Quelldateien (`LanguageCatalog.kt`, `TranslationEngine.kt`,
   `SpeechEngine.kt`, `MainActivity.kt`, `Color.kt`, `Type.kt`, `Theme.kt`) kompilieren
   fehlerfrei** (Exit-Code 0, 117 erzeugte `.class`-Dateien). Dabei wurde ein echter Bug
   gefunden und gefixt: `MainActivity.kt` enthielt ein `return@TypedModePanel` in einer
   Lambda, die an eine nicht-`inline`-Funktion übergeben wird - dieses implizite Label
   existiert dort nicht (nur bei `inline`-Funktionen). Behoben durch Entfernen der
   (ohnehin durch `Button.enabled` bereits redundanten) Guard-Klausel. Diese
   Stub-Kompilierung prüft nur Syntax/Typen/Referenzen der App-eigenen Logik gegen
   die recherchierte API-Form, **nicht** die Korrektheit dieser Recherche selbst
   gegen die echte ML-Kit-Alpha-AAR - das leistet erst der CI-Lauf.
2. Der tatsächliche `gradle assembleDebug`-Lauf über
   [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml) auf
   einem GitHub-Actions-Runner mit vollem Internetzugriff - das ist die
   verbindliche Build-Verifikation für dieses Projekt, wie in der
   Aufgabenstellung als Fallback vorgesehen.

### Gradle-Wrapper nachträglich ergänzen

Sobald jemand mit normalem Internetzugriff (z. B. lokal oder in Android
Studio) am Projekt arbeitet, genügt einmalig:

```
gradle wrapper --gradle-version 8.2 --distribution-type bin
```

um `gradlew`, `gradlew.bat` und `gradle-wrapper.jar` zu erzeugen und
einzuchecken.

## Versionsmatrix

Ausgangspunkt war AGP 8.5.2 / Kotlin 1.9.24 / Compose BOM 2024.06.00 /
Compose-Compiler-Extension 1.5.14. Geprüft anhand der offiziellen
[AGP/Kotlin-Kompatibilitätstabelle](https://developer.android.com/build/kotlin-support)
(Stand 2026-07-06):

| Kotlin-Version | Erforderliche AGP-Version |
|---|---|
| 1.9 | 7.4.2 - 8.2 |
| 2.0 | 7.4.2 - 8.3 |

**Ergebnis: AGP 8.5.2 und Kotlin 1.9.24 sind laut dieser Tabelle nicht
kompatibel** (AGP 8.5.2 verlangt mindestens Kotlin 2.0). Da die
Compose-Compiler-Extension 1.5.14 wiederum exakt an Kotlin 1.9.24 gebunden
ist (letzte 1.9.x-kompatible Compiler-Version laut
[Compose-Kotlin-Kompatibilitätstabelle](https://developer.android.com/jetpack/androidx/releases/compose-kotlin)),
wurde **AGP auf 8.2.2 heruntergezogen** (die höchste mit Kotlin 1.9.x
kompatible Version) statt Kotlin/Compose-Compiler hochzuziehen - das ist die
kleinere, konsistente Änderung und vermeidet den Umstieg auf das neue
Compose-Compiler-Gradle-Plugin (K2), das erst ab Kotlin 2.0 greift.

Finale Versionen:

| Komponente | Wert |
|---|---|
| Android Gradle Plugin | **8.2.2** (↓ von 8.5.2) |
| Kotlin | 1.9.24 (unverändert) |
| Compose BOM | 2024.06.00 (unverändert) |
| Compose-Compiler-Extension | 1.5.14 (unverändert) |
| Gradle | 8.2 (passend zu AGP 8.2.2) |
| compileSdk / targetSdk | 34 (Maximum für AGP 8.2) |
| minSdk | 26 (unverändert, hartes Requirement) |
| JDK (Gradle/AGP) | 17 |

## Offene Punkte

- **ViP-Schalter-Hardware:** Die Android-Version der im Einsatz befindlichen
  Schalter-Hardware ist nicht bekannt. Kein Blocker für diesen Build, aber
  relevant für den Live-Modus: Läuft die Hardware unter API < 31, bleibt der
  Live-Button dort automatisch deaktiviert (`SpeechEngine.isLiveSupported()`
  liefert `false`), die App bleibt aber voll funktionsfähig im getippten
  Modus (`minSdk 26` deckt das ab). Sollte vor dem Rollout geklärt werden,
  falls Live-Spracherkennung an den Schaltern erwartet wird.
- **SpeechRecognizerResponse-Feldnamen (Alpha-API):** siehe Abschnitt oben -
  aus Suchmaschinen-Snippets der Google-Referenzdoku rekonstruiert, nicht
  gegen die reale AAR geprüft. Sollte beim ersten Build mit Netzzugriff auf
  `dl.google.com` verifiziert werden.
- **Package-Name:** `de.vip.liveuebersetzer` (unverändert, hartes Requirement).
