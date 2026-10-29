package dev.arnv.bluke.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit

@Composable
fun KeyCap(
    legend: String,
    shiftedLegend: String,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    isPressed: Boolean,
    keyBgColor: Color,
    legendColor: Color,
    legendScale: Float = 1f,
    isSelected: Boolean = false,
    baseUnitWidth: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    glowColor: Color? = null,
    touchOptimized: Boolean = false,
    cornerActions: Map<KeyCorner, CornerAction> = emptyMap(),
    selectedCorner: KeyCorner? = null,
    altGrLegend: String = "",
    shiftedAltGrLegend: String = "",
    isLatched: Boolean = false,
) {
    val keyBorderColor = keyBgColor.darker()

    if (touchOptimized) {
        val label = cornerActions[selectedCorner]?.legend ?: when (legend) {
            "Backspace" -> "⌫"
            "Delete" -> "Del"
            "Caps Lock" -> "⇪"
            "Shift" -> "⇧"
            "Tab" -> "⇥"
            "PgUp" -> "Pg↑"
            "PgDn" -> "Pg↓"
            "" -> "Space"
            else -> legend
        }
        val symbol = label in setOf("⇪", "⇧", "⇥")
        val requestedFontSize = (minOf(width.value, height.value) * if (symbol) 0.55f else 0.4f)
            .coerceIn(if (symbol) 16f else 9f, if (symbol) 24f else 18f) * legendScale
        val textStyle = LocalTextStyle.current.copy(
            fontSize = requestedFontSize.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp,
        )
        val measuredWidth = rememberTextMeasurer().measure(
            text = label,
            style = textStyle,
            maxLines = 1,
            softWrap = false,
        ).size.width.coerceAtLeast(1)
        val availableWidth = with(LocalDensity.current) { (width - 9.dp).toPx() }.coerceAtLeast(1f)
        val fontSize = requestedFontSize * minOf(1f, availableWidth / measuredWidth)
        val shape = RoundedCornerShape(5.dp)
        Box(
            modifier = modifier.width(width).height(height).testTag("key_$legend")
                .semantics { selected = isLatched }
                .padding(1.dp)
                .background(if (isPressed && !isLatched) keyBorderColor else keyBgColor, shape)
                .border(1.dp, if (selectedCorner != null) legendColor else glowColor ?: Color.Black.copy(alpha = 0.16f), shape),
            contentAlignment = Alignment.Center,
        ) {
            val arrow = when (legend) {
                "←" -> Icons.AutoMirrored.Filled.ArrowBack
                "→" -> Icons.AutoMirrored.Filled.ArrowForward
                "↑" -> Icons.Default.ArrowUpward
                "↓" -> Icons.Default.ArrowDownward
                else -> null
            }
            if (arrow != null) {
                Icon(
                    imageVector = arrow,
                    contentDescription = legend,
                    tint = legendColor,
                    modifier = Modifier.size(minOf(width, height) * 0.6f),
                )
            } else Text(
                text = label,
                color = legendColor,
                style = textStyle.copy(fontSize = fontSize.sp),
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 3.dp),
            )
            val displayedCorners = if (arrow != null) cornerActions.entries.distinctBy { it.value.keyCode } else cornerActions.entries.toList()
            displayedCorners.filter { (corner, action) ->
                action.characterModifiers == null || action.legend != when (corner) {
                    KeyCorner.TOP_RIGHT -> shiftedLegend
                    KeyCorner.BOTTOM_RIGHT -> altGrLegend
                    KeyCorner.BOTTOM_LEFT -> shiftedAltGrLegend
                    KeyCorner.TOP_LEFT -> ""
                }
            }.forEach { (corner, action) ->
                val alignment = when (corner) {
                    KeyCorner.TOP_LEFT -> Alignment.TopStart
                    KeyCorner.TOP_RIGHT -> Alignment.TopEnd
                    KeyCorner.BOTTOM_LEFT -> Alignment.BottomStart
                    KeyCorner.BOTTOM_RIGHT -> Alignment.BottomEnd
                }
                val cornerFont = (minOf(width.value, height.value) * 0.17f).coerceIn(7f, 9f)
                Text(
                    text = action.legend,
                    color = legendColor.copy(alpha = if (cornerActions[selectedCorner] == action) 1f else 0.65f),
                    style = textStyle.copy(fontSize = cornerFont.sp, lineHeight = cornerFont.sp),
                    fontWeight = if (cornerActions[selectedCorner] == action) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    modifier = Modifier.align(alignment).padding(horizontal = 4.dp, vertical = 3.dp)
                        .testTag("corner_${legend}_${corner.name}"),
                )
            }
            AlternateKeyLegends(
                legend = legend,
                shiftedLegend = shiftedLegend,
                altGrLegend = altGrLegend,
                shiftedAltGrLegend = shiftedAltGrLegend,
                color = legendColor,
                fontSize = (fontSize * 0.65f).coerceIn(8f, 11f).sp,
                selectedCorner = selectedCorner,
            )
            if (isLatched) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = legendColor.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(9.dp),
                )
            }
        }
        return
    }
    
    // Proportional radii mapped directly from KBSim's 5px/3px on a 54px keysize
    val outerRadius = baseUnitWidth * 0.092f
    val innerRadius = baseUnitWidth * 0.055f
    
    // Inset padding mapped directly from KBSim's keysize/9 and keysize/18
    val insetX = baseUnitWidth / 9f
    val insetTop = baseUnitWidth / 18f
    val insetBottom = baseUnitWidth / 6f // keysize * 3 / 18

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .testTag("key_$legend")
            .padding(1.dp)
            .then(
                if (isSelected) Modifier.border(2.dp, Color(0xFF00BCD4), RoundedCornerShape(outerRadius))
                else Modifier
            )
    ) {
        // Underglow if active
        if (glowColor != null && glowColor != Color.Transparent) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp)
                    .background(glowColor.copy(alpha = glowColor.alpha * 0.45f), RoundedCornerShape(outerRadius))
                    .shadow(elevation = 3.dp, shape = RoundedCornerShape(outerRadius), spotColor = glowColor)
            )
        }

        // 1. Key Border Base Bezel (KBSim .keyborder)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(keyBorderColor, RoundedCornerShape(outerRadius))
                .border(1.dp, Color.Black.copy(alpha = 0.85f), RoundedCornerShape(outerRadius))
        )

        // 2. Key Top Bevel (KBSim .keytop, inset and styled)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = insetX,
                    end = insetX,
                    top = insetTop,
                    bottom = insetBottom
                )
                .background(
                    color = if (isPressed) keyBorderColor else keyBgColor,
                    shape = RoundedCornerShape(innerRadius)
                )
                .border(1.dp, Color.Black.copy(alpha = 0.15f), RoundedCornerShape(innerRadius)),
            contentAlignment = Alignment.Center
        ) {
            val keyWidthRatio = (width.value / baseUnitWidth.value).coerceAtLeast(1f)
            val normalizedLegendLength = legend.length / keyWidthRatio
            val legendLengthScale = when {
                normalizedLegendLength >= 8f -> 0.62f
                normalizedLegendLength >= 6f -> 0.74f
                normalizedLegendLength >= 4.5f -> 0.88f
                else -> 1f
            }
            val shiftedLengthScale = when {
                shiftedLegend.length >= 4 -> 0.72f
                else -> 1f
            }
            val mainFontSize = (
                (baseUnitWidth.value * 0.24f).coerceIn(3f, 13f) *
                    legendScale * legendLengthScale
                ).sp
            val shiftFontSize = (
                (baseUnitWidth.value * 0.17f).coerceIn(2.5f, 9f) *
                    legendScale * shiftedLengthScale
                ).sp

            if (shiftedLegend.isNotEmpty()) {
                // Stack legends inside a column so the secondary text sits exactly slightly above the primary with perfect alignment
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(y = (baseUnitWidth.value * 0.055f).coerceAtMost(3f).dp)
                        .padding(
                            start = (baseUnitWidth.value * 0.08f).dp,
                            end = (baseUnitWidth.value * 0.08f).dp,
                            top = 0.dp,
                            bottom = 0.dp
                        ),
                    verticalArrangement = Arrangement.spacedBy(
                        (baseUnitWidth.value * 0.03f).coerceAtMost(2f).dp,
                    ),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = shiftedLegend,
                        fontSize = shiftFontSize,
                        color = legendColor.copy(alpha = 0.82f),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        style = androidx.compose.ui.text.TextStyle(
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                        )
                    )
                    Text(
                        text = legend,
                        fontSize = mainFontSize,
                        color = legendColor,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        style = androidx.compose.ui.text.TextStyle(
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                        )
                    )
                }
            } else {
                // Alphabetical or single legend, centered
                Text(
                    text = legend,
                    fontSize = mainFontSize,
                    color = legendColor,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.padding(
                        (baseUnitWidth.value * 0.04f).coerceIn(0.5f, 2f).dp,
                    ),
                )
            }
            AlternateKeyLegends(
                legend = legend,
                shiftedLegend = "", // The mechanical key already stacks Shift above its primary label.
                altGrLegend = altGrLegend,
                shiftedAltGrLegend = shiftedAltGrLegend,
                color = legendColor,
                fontSize = shiftFontSize,
            )
        }
    }
}

/** Symbol hints share the same corners as the touch keyboard's character gestures. */
@Composable
private fun BoxScope.AlternateKeyLegends(
    legend: String,
    shiftedLegend: String,
    altGrLegend: String,
    shiftedAltGrLegend: String,
    color: Color,
    fontSize: TextUnit,
    selectedCorner: KeyCorner? = null,
) {
    listOf(
        Triple("shift", shiftedLegend, Alignment.TopEnd),
        Triple("altgr", altGrLegend, Alignment.BottomEnd),
        Triple("shift_altgr", shiftedAltGrLegend, Alignment.BottomStart),
    ).filter { it.second.isNotEmpty() }.forEach { (level, symbol, alignment) ->
        val highlighted = selectedCorner == when (level) {
            "shift" -> KeyCorner.TOP_RIGHT
            "altgr" -> KeyCorner.BOTTOM_RIGHT
            else -> KeyCorner.BOTTOM_LEFT
        }
        Text(
            text = symbol,
            color = color.copy(alpha = if (highlighted) 1f else 0.75f),
            fontSize = fontSize,
            lineHeight = fontSize * 1.25f,
            letterSpacing = 0.sp,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
            style = androidx.compose.ui.text.TextStyle(
                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
            ),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.align(alignment).padding(horizontal = 4.dp, vertical = 2.dp)
                .testTag("${level}_$legend"),
        )
    }
}



// Extension function to darken mechanical key highlights on pressing

fun Color.darker(): Color {
    return Color(
        red = (this.red * 0.82f).coerceIn(0f, 1f),
        green = (this.green * 0.82f).coerceIn(0f, 1f),
        blue = (this.blue * 0.82f).coerceIn(0f, 1f),
        alpha = this.alpha
    )
}


