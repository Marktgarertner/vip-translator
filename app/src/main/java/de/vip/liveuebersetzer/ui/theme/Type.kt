// Variable Schriften (FontVariation) sind in Compose noch als experimentell
// markiert; die API ist seit mehreren Versionen stabil in Gebrauch.
@file:OptIn(ExperimentalTextApi::class)

package de.vip.liveuebersetzer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import de.vip.liveuebersetzer.R

/**
 * Schrift: Inter (SIL Open Font License, siehe `app/fonts-license/OFL-Inter.txt`),
 * als variable Schrift gebündelt (`res/font/inter_variable.ttf`). Gewählt,
 * weil sie am Schalter mit Abstand gut lesbar ist (offene Formen, klare
 * Ziffern) und Latein, Kyrillisch, Griechisch und Vietnamesisch vollständig
 * abdeckt - für Arabisch, Persisch, Hindi und die ostasiatischen Schriften
 * fällt Android automatisch auf die Systemschrift zurück. Die Gewichte
 * werden über die `wght`-Achse der variablen Schrift gezogen (ab API 26,
 * unserem minSdk).
 */
val VipFontFamily: FontFamily = FontFamily(
    Font(R.font.inter_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.inter_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.inter_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.inter_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

/**
 * Typo-Skala der App. Etwas größer als die Material-Defaults: Die App läuft
 * am Kundenschalter und wird oft mit Abstand gelesen (Splitscreen,
 * Gegenüber). Rollen:
 *  - displaySmall: Begrüßung auf der Kundenseite
 *  - titleLarge / titleMedium: Bildschirmtitel, Kartentitel
 *  - bodyLarge: Gesprächsbeiträge (Hauptzeile), bodyMedium: Fließtext
 *  - labelLarge: Knöpfe, labelMedium: Statuszeilen, labelSmall: Abschnitts-
 *    überschriften in Versalien (mit Sperrung)
 */
val VipTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = VipFontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = VipFontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = VipFontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = VipFontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = VipFontFamily, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = VipFontFamily, fontWeight = FontWeight.Normal,
        fontSize = 19.sp, lineHeight = 27.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = VipFontFamily, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 23.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = VipFontFamily, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = VipFontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = VipFontFamily, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = VipFontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.1.sp,
    ),
)
