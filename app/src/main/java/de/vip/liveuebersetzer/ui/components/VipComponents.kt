package de.vip.liveuebersetzer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.vip.liveuebersetzer.Language
import de.vip.liveuebersetzer.LanguageCatalog
import de.vip.liveuebersetzer.R
import de.vip.liveuebersetzer.ui.theme.VipDisabled
import de.vip.liveuebersetzer.ui.theme.VipTheme

/*
 * Die Bausteine der Oberfläche. Alles, was auf mehr als einem Bildschirm
 * vorkommt, steht hier - damit Kopfleisten, Karten, Banner und Knöpfe
 * überall gleich aussehen und eine Designänderung an einer Stelle reicht.
 */

/** Kopfleiste der Untermenüs: Waldgrün, Zurück-Pfeil, Titel, optionale Aktionen. */
@Composable
fun VipTopBar(
    title: String,
    onBack: () -> Unit,
    backDescription: String = "Zurück",
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = backDescription,
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        actions?.invoke(this)
    }
}

/** Das ViP-Windrad in einem weißen Kreis - auf beiden Bildschirmhälften links oben. */
@Composable
fun LogoBadge(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .background(Color.White, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.vip_logo),
            contentDescription = "Einrichtung öffnen",
            modifier = Modifier.size(28.dp),
        )
    }
}

/**
 * Weiße Pille mit dem Namen der gewählten Sprache und einem Pfeil nach
 * unten/oben; ein Antippen klappt die Sprachauswahl auf.
 */
@Composable
fun LanguagePill(label: String, expanded: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // maxLines als Sicherheitsnetz: lieber abgeschnitten als
            // buchstabenweise umgebrochen (Praxistest-Foto "D e ut sc h").
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 2.dp)
                    .size(22.dp),
            )
        }
    }
}

/**
 * Grüne Kopfleiste einer Bildschirmhälfte: links das ViP-Logo (Klick öffnet
 * die Einrichtung), rechts die Sprachauswahl - darunter optionale Aktionen
 * ([actions], z. B. Lautsprecher/"Nächster Kunde" der Mitarbeiterseite).
 * Ein Antippen der Sprachauswahl klappt darunter eine horizontal scrollbare
 * Chip-Reihe aller Sprachen auf. Bewusst ohne Popup-Menü (das würde in der
 * um 180° gedrehten Kundenhälfte falsch positioniert erscheinen) -
 * stattdessen normales Layout, das mit der Elternhälfte mitrotiert.
 */
@Composable
fun PaneHeader(
    selected: Language,
    labelFor: (Language) -> String,
    onSelected: (Language) -> Unit,
    onLogoClick: () -> Unit,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        // Eine Zeile: Logo, Aktionen, Sprachauswahl. Früher brauchte die
        // Mitarbeiterseite zwei Zeilen, weil "Nächster Kunde" mit hinein
        // musste und die Sprachtaste sonst buchstabenweise umbrach - die
        // Taste sitzt jetzt unten rechts, gegenüber der Sprechtaste, und die
        // Sprachpille schneidet notfalls ab statt umzubrechen.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LogoBadge(onClick = onLogoClick)
            actions?.let { it() }
            Spacer(modifier = Modifier.weight(1f))
            LanguagePill(label = labelFor(selected), expanded = expanded, onClick = { expanded = !expanded })
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .padding(vertical = 10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(LanguageCatalog.all, key = { it.code }) { language ->
                    LanguageChip(
                        label = labelFor(language),
                        selected = language.code == selected.code,
                        onClick = {
                            onSelected(language)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
        border = if (selected) null else BorderStroke(1.dp, VipTheme.colors.outline),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

/** Icon-Knopf in der grünen Kopfleiste (weiß, mit leicht aufgehellter Fläche). */
@Composable
fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(Color.White.copy(alpha = if (active) 0.16f else 0.06f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (active) 1f else 0.6f),
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Schwebende Pille über dem Verlauf ("Nächster Kunde" unten rechts, als
 * Gegenstück zur Sprechtaste unten links): weiß mit grünem Rahmen, damit
 * die grüne Sprechtaste die dominante Taste bleibt.
 */
@Composable
fun FloatingPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(20.dp)) }
            Text(text = text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/** Art eines Statusbanners - bestimmt Farbe und Symbol. */
enum class BannerKind { INFO, WARN, ERROR, LIVE }

/**
 * Hinweiskasten mit Symbol statt einer nackten roten Textzeile: Information
 * (blau), Hinweis (orange), Fehler (rot) - die Windrad-Farben - und LIVE
 * (pulsierender roter Punkt, solange das Mikrofon offen ist). Optional mit
 * einer Aktion darunter ("Einstellungen öffnen").
 */
@Composable
fun StatusBanner(
    kind: BannerKind,
    text: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = VipTheme.colors
    val background = when (kind) {
        BannerKind.INFO -> MaterialTheme.colorScheme.surface
        BannerKind.WARN -> colors.warnBackground
        BannerKind.ERROR -> colors.errorBackground
        BannerKind.LIVE -> MaterialTheme.colorScheme.surface
    }
    val textColor = when (kind) {
        BannerKind.INFO, BannerKind.LIVE -> MaterialTheme.colorScheme.onSurface
        BannerKind.WARN -> colors.warnText
        BannerKind.ERROR -> colors.errorText
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = background,
        contentColor = textColor,
        border = if (kind == BannerKind.INFO || kind == BannerKind.LIVE) BorderStroke(1.dp, colors.outline) else null,
    ) {
        // Kompakt gehalten: Mehrere Banner übereinander (Fehler + Hinweis)
        // dürfen auf einem Telefon den Verlauf nicht komplett verdrängen.
        Column(modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when (kind) {
                        BannerKind.INFO -> Icon(Icons.Rounded.Info, null, tint = colors.info, modifier = Modifier.size(20.dp))
                        BannerKind.WARN -> Icon(Icons.Rounded.WarningAmber, null, tint = colors.warnAccent, modifier = Modifier.size(20.dp))
                        BannerKind.ERROR -> Icon(Icons.Rounded.ErrorOutline, null, tint = colors.errorAccent, modifier = Modifier.size(20.dp))
                        BannerKind.LIVE -> LiveDot()
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp, end = 4.dp),
                ) {
                    Text(text = text, style = MaterialTheme.typography.bodySmall)
                    detail?.let {
                        Text(
                            text = it,
                            modifier = Modifier.padding(top = 3.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            action?.let {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) { it() }
            }
        }
    }
}

/** Pulsierender roter Punkt: "Aufnahme läuft". */
@Composable
fun LiveDot(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "live")
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(
        modifier = modifier
            .size(12.dp)
            .alpha(pulse)
            .background(VipTheme.colors.recording, CircleShape),
    )
}

/**
 * Große runde Sprechtaste - auf beiden Bildschirmhälften unten links.
 * Waldgrün im Ruhezustand, Windrad-Rot mit pulsierendem Ring während der
 * Aufnahme, Fortschrittsring beim Vorbereiten des Modells, gedämpft wenn
 * deaktiviert. Beschriftung darunter in der Sprache der jeweiligen Seite.
 */
@Composable
fun MicButton(
    modifier: Modifier = Modifier,
    isRecording: Boolean,
    isPreparing: Boolean,
    enabled: Boolean,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val colors = VipTheme.colors
    val transition = rememberInfiniteTransition(label = "mic")
    val ringScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "ringScale",
    )
    val ringAlpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "ringAlpha",
    )
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.size(84.dp), contentAlignment = Alignment.Center) {
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .scale(ringScale)
                        .alpha(ringAlpha)
                        .background(colors.recording, CircleShape),
                )
            }
            if (isPreparing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(84.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = colors.outline,
                    strokeWidth = 3.dp,
                    strokeCap = StrokeCap.Round,
                )
            }
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        color = when {
                            isRecording -> colors.recording
                            enabled -> MaterialTheme.colorScheme.primary
                            else -> VipDisabled
                        },
                        shape = CircleShape,
                    )
                    .clickable { if (enabled || isRecording) onClick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(34.dp),
                    tint = Color.White,
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled || isRecording) MaterialTheme.colorScheme.primary else VipDisabled,
            maxLines = 1,
        )
    }
}

/**
 * Sprechblase eines Gesprächsbeitrags. [primary] ist der Text in der Sprache
 * der Bildschirmhälfte, [secondary] das Gegenstück; "eigene" Beiträge
 * (von dieser Seite gesprochen) stehen rechts in Grün, die der Gegenseite
 * links in Weiß. [caption] steht klein darüber (Mitarbeiterseite:
 * Sprachpaar), [note] klein darunter (Mitarbeiterseite: "wörtlich erkannt").
 */
@Composable
fun MessageBubble(
    primary: String,
    secondary: String,
    mine: Boolean,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    note: String? = null,
    primaryStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val colors = VipTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            // Auf Tablets nicht über die ganze Breite laufen lassen - lange
            // Zeilen lesen sich schlecht, und die Chat-Anordnung geht verloren.
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .widthIn(max = 560.dp),
            shape = if (mine) de.vip.liveuebersetzer.ui.theme.BubbleShapeMine else de.vip.liveuebersetzer.ui.theme.BubbleShapeOther,
            color = if (mine) colors.bubbleMine else colors.bubbleOther,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = if (mine) null else BorderStroke(1.dp, colors.outline),
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    caption?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                    Text(text = primary, style = primaryStyle)
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                    note?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.warnText,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                IconButton(onClick = onSpeak, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = "Vorlesen",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/**
 * Weicher Verlauf am oberen Rand einer Liste: Ältere Beiträge, die nach
 * oben aus dem Bild scrollen, werden ausgeblendet statt hart unter Banner
 * oder Kopfleiste abgeschnitten.
 */
@Composable
fun ListTopFade(modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(20.dp)
            .background(Brush.verticalGradient(listOf(background, background.copy(alpha = 0f)))),
    )
}

/** Weiße Karte ohne Schatten, mit feinem Rahmen im dunklen Theme. */
@Composable
fun VipCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, VipTheme.colors.outline),
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/** Abschnittsüberschrift in Versalien (Seegrün, gesperrt). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.secondary,
    )
}

/** Zustand einer [StatusRow]. */
enum class RowState { OK, PENDING, WARN, ERROR, NEUTRAL }

/**
 * Eine Statuszeile: farbiger Punkt bzw. Symbol links, Text rechts, optional
 * ein Fortschrittsbalken darunter (0..1 oder `null` für "unbestimmt").
 */
@Composable
fun StatusRow(
    state: RowState,
    text: String,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    showProgress: Boolean = false,
) {
    val colors = VipTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .padding(top = 3.dp)
                    .size(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (state) {
                    RowState.OK -> Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(colors.ok, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                    RowState.PENDING -> CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    RowState.WARN -> Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(colors.warnAccent, CircleShape),
                    )
                    RowState.ERROR -> Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(colors.errorAccent, CircleShape),
                    )
                    RowState.NEUTRAL -> Box(
                        modifier = Modifier
                            .size(12.dp)
                            .border(2.dp, colors.outline, CircleShape),
                    )
                }
            }
            Text(
                text = text,
                modifier = Modifier.padding(start = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = when (state) {
                    RowState.WARN -> colors.warnText
                    RowState.ERROR -> colors.errorText
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
        if (showProgress) {
            val barModifier = Modifier
                .fillMaxWidth()
                .padding(start = 26.dp, top = 8.dp, end = 4.dp)
                .height(6.dp)
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = barModifier,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = colors.surfaceSoft,
                    strokeCap = StrokeCap.Round,
                )
            } else {
                LinearProgressIndicator(
                    modifier = barModifier,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = colors.surfaceSoft,
                    strokeCap = StrokeCap.Round,
                )
            }
        }
    }
}

/** Primärer Knopf (Waldgrün, voll gerundet). */
@Composable
fun VipButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = VipDisabled.copy(alpha = 0.5f),
            disabledContentColor = Color.White,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        icon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Sekundärer Knopf (Umriss in Waldgrün). */
@Composable
fun VipOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = CircleShape,
        border = BorderStroke(1.5.dp, if (enabled) MaterialTheme.colorScheme.primary else VipDisabled),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    ) {
        icon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Textknopf ohne Fläche (für Nebenaktionen). */
@Composable
fun VipTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Kleines Abzeichen (z. B. "ML Kit", "Vosk") in einer Pille. */
@Composable
fun Badge(text: String, modifier: Modifier = Modifier, tone: RowState = RowState.NEUTRAL) {
    val colors = VipTheme.colors
    val (bg, fg) = when (tone) {
        RowState.OK -> colors.bubbleMine to MaterialTheme.colorScheme.onPrimaryContainer
        RowState.WARN -> colors.warnBackground to colors.warnText
        RowState.ERROR -> colors.errorBackground to colors.errorText
        else -> colors.surfaceSoft to colors.textMuted
    }
    Text(
        text = text,
        modifier = modifier
            .background(bg, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        maxLines = 1,
    )
}

/** Leerzustand: Symbol im Kreis, Titel, erklärender Text - zentriert. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(VipTheme.colors.bubbleMine, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
        }
        Text(
            text = title,
            modifier = Modifier.padding(top = 14.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = text,
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = VipTheme.colors.textMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
