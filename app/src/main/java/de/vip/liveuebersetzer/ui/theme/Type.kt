package de.vip.liveuebersetzer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Etwas größer als die Material-Defaults: Die App läuft am Kundenschalter und
// wird oft mit Abstand gelesen (Splitscreen, Gegenüber).
val VipTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 18.sp),
)
