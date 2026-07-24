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

`DownloadStatus`s genauer Aufbau (für eine Fortschrittsanzeige beim Modell-
Download) ist noch nicht verifiziert - `ensureModelDownloaded()` durchläuft
den Flow aktuell bis zum Abschluss, ohne den Fortschritt auszuwerten.

## Build-Verifikation

**Status: `gradle assembleDebug` ist grün.** Der CI-Workflow
[`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml) baut das
Projekt auf einem GitHub-Actions-Runner erfolgreich zu einer Debug-APK
(Artifact `vip-live-uebersetzer-debug`).

**Warum überhaupt CI statt eines lokalen Builds:** Die Entwicklungs-Sandbox,
in der dieses Projekt geschrieben wurde, hat eine Egress-Policy, die u. a.
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

- **ViP-Schalter-Hardware:** Die Android-Version der im Einsatz befindlichen
  Schalter-Hardware ist nicht bekannt. Kein Blocker für diesen Build, aber
  relevant für den Live-Modus: Läuft die Hardware unter API < 31, bleibt der
  Live-Button dort automatisch deaktiviert (`SpeechEngine.isLiveSupported()`
  liefert `false`), die App bleibt aber voll funktionsfähig im getippten
  Modus (`minSdk 26` deckt das ab). Sollte vor dem Rollout geklärt werden,
  falls Live-Spracherkennung an den Schaltern erwartet wird.
- **`DownloadStatus`-Aufbau:** `SpeechRecognizer.download(): Flow<DownloadStatus>`
  ist gegen die echte AAR verifiziert (siehe Abschnitt oben), der genaue
  Aufbau von `DownloadStatus` selbst (für eine Fortschrittsanzeige) aber
  nicht - `ensureModelDownloaded()` durchläuft den Flow aktuell nur bis zum
  Abschluss. Kein Blocker (der Download funktioniert), aber offen für eine
  spätere Fortschrittsanzeige beim Modell-Download.
- **Package-Name:** `de.vip.liveuebersetzer` (unverändert, hartes Requirement).
