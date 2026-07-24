package de.vip.liveuebersetzer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = VipGreen,
    secondary = VipOrange,
    tertiary = VipGreenLight,
)

private val DarkColors = darkColorScheme(
    primary = VipGreenLight,
    secondary = VipOrange,
    tertiary = VipGreenDark,
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
