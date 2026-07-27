package de.vip.liveuebersetzer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = VipGreen,
    onPrimary = VipSurface,
    secondary = VipOrange,
    tertiary = VipTextMuted,
    background = VipMist,
    surface = VipSurface,
    onSurface = VipInk,
)

private val DarkColors = darkColorScheme(
    primary = VipGreenLight,
    onPrimary = VipOnPrimaryDark,
    secondary = VipOrange,
    tertiary = VipNightMuted,
    background = VipNight,
    surface = VipNightSurface,
    onSurface = VipNightInk,
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
