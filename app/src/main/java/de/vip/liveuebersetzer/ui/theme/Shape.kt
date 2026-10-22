package de.vip.liveuebersetzer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Eine Rundungsskala für die ganze App, damit Karten, Blasen, Banner und
 * Knöpfe zusammengehörig wirken: Kleines (Chips, Badges) 10 dp, Karten und
 * Banner 18 dp, große Flächen 24 dp. Pillen (Sprachwahl, Sprechtaste) sind
 * voll gerundet und stehen in den Komponenten direkt.
 */
val VipShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** Sprechblase: rundum weich, die "Sprecher-Ecke" etwas enger. */
val BubbleShapeMine = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp)
val BubbleShapeOther = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 18.dp)
