package de.vip.liveuebersetzer.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Offizielle ViP-Farbpalette "Verkehr (ViP)" aus dem SWP-Markenportal
 * (Namen wie dort vergeben - bitte nicht "verschönern"):
 */
/** `$waldgruen` - Leistungsbereich, Primärfarbe. */
val VipWaldgruen = Color(0xFF006A4D)

/** `$dunkelgruen` - Dunkel Kontrast. */
val VipDunkelgruen = Color(0xFF09503D)

/** `$seegruen` - Hell Kontrast. */
val VipSeegruen = Color(0xFF008963)

/** `$aquamarine` - Sonderfarbe, helle Fläche. */
val VipAquamarine = Color(0xFFDEF3F0)

/**
 * Die vier Farben des ViP-Windrads (`res/drawable/vip_logo.xml`). Werden
 * als **semantische Akzente** genutzt, damit Zustände markenkonform bleiben
 * statt Material-Standardrot/-gelb zu zeigen: Rot = Aufnahme läuft / Fehler,
 * Orange = Hinweis/Warnung, Blau = Information, Grün = bereit.
 */
val VipLogoRot = Color(0xFFD5232A)
val VipLogoOrange = Color(0xFFF39200)
val VipLogoBlau = Color(0xFF1467A8)
val VipLogoGruen = Color(0xFF41A62A)

/**
 * Abgeleitete Farben (NICHT Teil der offiziellen Palette, nur Ergänzungen
 * für Zustände und Flächen, die die Palette nicht abdeckt):
 */
val VipWhite = Color(0xFFFFFFFF)

/** Heller Grünton für "eigene" Sprechblasen und Primär-Container. */
val VipMint = Color(0xFFCDEAE0)

/** Noch hellere Fläche für sekundäre Bereiche auf weißen Karten. */
val VipMintSoft = Color(0xFFEEF8F5)

/** Linien und Rahmen auf hellem Grund. */
val VipOutline = Color(0xFFB9DDD3)

/** Gedämpftes Grün für deaktivierte Bedienelemente. */
val VipDisabled = Color(0xFF9DBFB4)

/** Warnhinweise auf hellem Grund (Fläche / Text). */
val VipWarnFlaeche = Color(0xFFFFF1DB)
val VipWarnText = Color(0xFF6E4300)

/** Fehler auf hellem Grund (Fläche / Text). */
val VipFehlerFlaeche = Color(0xFFFCE7E7)
val VipFehlerText = Color(0xFF8C1B1F)

/** Dunkles Theme: Flächen, abgeleitet aus Dunkelgrün. */
val VipNightBackground = Color(0xFF071F18)
val VipNightSurface = Color(0xFF0F2E25)
val VipNightSurfaceRaised = Color(0xFF153B30)
val VipNightMine = Color(0xFF1C5A47)
val VipNightOutline = Color(0xFF24584A)

/** Dunkles Theme: gedämpfte helle Textfarbe. */
val VipNightMuted = Color(0xFF9CC8BA)

/** Dunkles Theme: Warn-/Fehlerflächen und -texte. */
val VipNightWarnFlaeche = Color(0xFF3B2D0E)
val VipNightWarnText = Color(0xFFFFD58A)
val VipNightFehlerFlaeche = Color(0xFF461A1C)
val VipNightFehlerText = Color(0xFFFFB4B0)
val VipNightRot = Color(0xFFE8534B)
