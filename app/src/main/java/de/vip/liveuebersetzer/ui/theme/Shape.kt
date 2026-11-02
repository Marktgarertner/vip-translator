package de.vip.liveuebersetzer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Bewusst knappe Rundungen: Bedienelemente sind eckig mit kleinem Radius
 * wie Beschilderung an der Haltestelle, nicht voll gerundete "Pillen"
 * (Rückmeldung aus dem Praxistest: wirkte nach Baukasten). Einzige runde
 * Form bleibt die Sprechtaste - sie ist das eine Element, das man auf
 * beiden Seiten sofort finden soll.
 */
val VipShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

/** Knöpfe, Sprachauswahl, Sprach-Chips, Kopfleisten-Icons. */
val ControlShape = RoundedCornerShape(6.dp)

/** Sprechblase: knapp gerundet, die "Sprecher-Ecke" spitz. */
val BubbleShapeMine = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 10.dp, bottomEnd = 2.dp)
val BubbleShapeOther = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 2.dp, bottomEnd = 10.dp)
