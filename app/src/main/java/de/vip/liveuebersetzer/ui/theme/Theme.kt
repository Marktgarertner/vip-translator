package de.vip.liveuebersetzer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Farbrollen, die Material 3 nicht kennt, die App aber überall braucht:
 * Sprechblasen, Statusbanner, der Aufnahme-Rotton. Zugriff über
 * `VipTheme.colors` (siehe unten), damit kein Composable Hex-Werte oder
 * "ist dunkel?"-Abfragen enthält.
 */
@Immutable
data class VipColors(
    /** Blase für Beiträge der eigenen Seite (grün getönt). */
    val bubbleMine: Color,
    /** Blase für Beiträge der Gegenseite (neutral). */
    val bubbleOther: Color,
    /** Rahmen der neutralen Blase und feine Trennlinien. */
    val outline: Color,
    /** Sekundärer Text (Übersetzungszeile, Statusangaben). */
    val textMuted: Color,
    /** Sekundäre Fläche auf Karten (z. B. Hinweiskästen, Fortschrittsleisten-Hintergrund). */
    val surfaceSoft: Color,
    /** Aufnahme läuft (Sprechtaste, Live-Punkt) - Windrad-Rot. */
    val recording: Color,
    /** "Bereit"-Akzent (Haken, Punkte) - Windrad-Grün. */
    val ok: Color,
    /** Informationsakzent (Icons in neutralen Bannern) - Windrad-Blau. */
    val info: Color,
    val warnBackground: Color,
    val warnText: Color,
    val warnAccent: Color,
    val errorBackground: Color,
    val errorText: Color,
    val errorAccent: Color,
)

private val LightVipColors = VipColors(
    bubbleMine = VipMint,
    bubbleOther = VipWhite,
    outline = VipOutline,
    textMuted = VipDunkelgruen.copy(alpha = 0.72f),
    surfaceSoft = VipMintSoft,
    recording = VipLogoRot,
    ok = VipLogoGruen,
    info = VipLogoBlau,
    warnBackground = VipWarnFlaeche,
    warnText = VipWarnText,
    warnAccent = VipLogoOrange,
    errorBackground = VipFehlerFlaeche,
    errorText = VipFehlerText,
    errorAccent = VipLogoRot,
)

private val DarkVipColors = VipColors(
    bubbleMine = VipNightMine,
    bubbleOther = VipNightSurfaceRaised,
    outline = VipNightOutline,
    textMuted = VipNightMuted,
    surfaceSoft = VipNightSurfaceRaised,
    recording = VipNightRot,
    ok = VipLogoGruen,
    info = Color(0xFF6FB1E8),
    warnBackground = VipNightWarnFlaeche,
    warnText = VipNightWarnText,
    warnAccent = VipLogoOrange,
    errorBackground = VipNightFehlerFlaeche,
    errorText = VipNightFehlerText,
    errorAccent = VipNightRot,
)

/**
 * Material-Farbrollen aus der offiziellen ViP-Palette (siehe [Color.kt]):
 * Waldgrün als Primärfarbe (Kopfleisten, Sprechtasten), Aquamarin als
 * heller Flächenhintergrund, Seegrün als Akzent, Dunkelgrün als Fließtext
 * auf Karten. `error` ist bewusst das Windrad-Rot, nicht das Material-Rot.
 */
private val LightColors = lightColorScheme(
    primary = VipWaldgruen,
    onPrimary = VipWhite,
    primaryContainer = VipMint,
    onPrimaryContainer = VipDunkelgruen,
    secondary = VipSeegruen,
    onSecondary = VipWhite,
    secondaryContainer = VipMintSoft,
    onSecondaryContainer = VipDunkelgruen,
    tertiary = VipSeegruen,
    background = VipAquamarine,
    onBackground = VipDunkelgruen,
    surface = VipWhite,
    onSurface = VipDunkelgruen,
    surfaceVariant = VipMintSoft,
    onSurfaceVariant = VipDunkelgruen,
    outline = VipOutline,
    outlineVariant = VipOutline,
    error = VipLogoRot,
    onError = VipWhite,
    errorContainer = VipFehlerFlaeche,
    onErrorContainer = VipFehlerText,
)

private val DarkColors = darkColorScheme(
    primary = VipSeegruen,
    onPrimary = VipWhite,
    primaryContainer = VipNightMine,
    onPrimaryContainer = VipAquamarine,
    secondary = VipSeegruen,
    onSecondary = VipWhite,
    secondaryContainer = VipNightSurfaceRaised,
    onSecondaryContainer = VipAquamarine,
    tertiary = VipNightMuted,
    background = VipNightBackground,
    onBackground = VipAquamarine,
    surface = VipNightSurface,
    onSurface = VipAquamarine,
    surfaceVariant = VipNightSurfaceRaised,
    onSurfaceVariant = VipAquamarine,
    outline = VipNightOutline,
    outlineVariant = VipNightOutline,
    error = VipNightRot,
    onError = VipWhite,
    errorContainer = VipNightFehlerFlaeche,
    onErrorContainer = VipNightFehlerText,
)

private val LocalVipColors = staticCompositionLocalOf { LightVipColors }

/** Zugriff auf die App-eigenen Farbrollen: `VipTheme.colors.bubbleMine` usw. */
object VipTheme {
    val colors: VipColors
        @Composable get() = LocalVipColors.current
}

@Composable
fun VipLiveUebersetzerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val vipColors = if (darkTheme) DarkVipColors else LightVipColors
    CompositionLocalProvider(LocalVipColors provides vipColors) {
        MaterialTheme(
            colorScheme = colors,
            typography = VipTypography,
            shapes = VipShapes,
            content = content,
        )
    }
}
