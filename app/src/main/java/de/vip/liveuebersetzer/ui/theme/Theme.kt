package de.vip.liveuebersetzer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Farbrollen aus der offiziellen ViP-Palette (siehe [Color.kt]):
 * Waldgrün als Primärfarbe (Kopfleisten, Sprechtasten), Aquamarin als
 * heller Flächenhintergrund, Seegrün als heller Kontrast (Akzente,
 * Sekundärtexte), Dunkelgrün als dunkler Kontrast (Fließtext auf Karten).
 */
private val LightColors = lightColorScheme(
    primary = VipWaldgruen,
    onPrimary = VipWhite,
    secondary = VipSeegruen,
    tertiary = VipSeegruen,
    background = VipAquamarine,
    surface = VipWhite,
    onSurface = VipDunkelgruen,
)

private val DarkColors = darkColorScheme(
    primary = VipSeegruen,
    onPrimary = VipWhite,
    secondary = VipSeegruen,
    tertiary = VipNightMuted,
    background = VipNightBackground,
    surface = VipDunkelgruen,
    onSurface = VipAquamarine,
)

@Composable
fun VipLiveUebersetzerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = VipTypography,
        content = content,
    )
}
