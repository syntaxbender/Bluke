package dev.arnv.bluke.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

internal data class KeyPreviewInfo(
    val id: String,
    val bounds: Rect,
    val label: String,
    val corners: Map<KeyCorner, CornerAction> = emptyMap(),
    val background: Color,
    val foreground: Color,
    val shiftedLabel: String = label,
    val altGrLabel: String = label,
    val shiftedAltGrLabel: String = shiftedLabel,
    val fnLabel: String? = null,
)

internal data class PressedKeyPreview(
    val keyId: String,
    val bounds: Rect,
    val label: String,
    val background: Color,
    val foreground: Color,
)

internal fun keyPreviewLabel(
    key: KeyLayoutInfo,
    characterLayout: KeyboardCharacterLayout,
    activeModifiers: Set<Int>,
    capsLock: Boolean,
): String {
    val shift = activeModifiers.any { it == KeyboardLayouts.MOD_LSHIFT || it == KeyboardLayouts.MOD_RSHIFT }
    val altGr = KeyboardLayouts.MOD_RALT in activeModifiers
    if (altGr && shift && key.shiftedAltGrLegend.isNotEmpty()) return key.shiftedAltGrLegend
    if (altGr && !shift && key.altGrLegend.isNotEmpty()) return key.altGrLegend
    if (shift && !altGr && key.shiftedLegend.isNotEmpty()) return key.shiftedLegend
    if (key.legend.length == 1 && key.legend[0].isLetter()) {
        return characterLayout.displayLetter(key.legend, capsLock xor shift)
    }
    return when (key.legend) {
        "" -> "Space"
        "Backspace" -> "⌫"
        "Delete" -> "Del"
        "Caps Lock" -> "Caps"
        else -> key.legend
    }
}

/** Keep the label above the finger, or to its side when the top edge leaves no room. */
internal fun keyPreviewPosition(
    key: Rect,
    size: IntSize,
    window: IntSize,
    gap: Float,
    margin: Float,
): IntOffset {
    fun x(value: Float) = value.coerceIn(margin, (window.width - size.width - margin).coerceAtLeast(margin))
    fun y(value: Float) = value.coerceIn(margin, (window.height - size.height - margin).coerceAtLeast(margin))
    val above = y(key.top - gap - size.height)
    val centeredX = x(key.center.x - size.width / 2f)
    val position = when {
        above + size.height <= key.center.y - gap -> Offset(centeredX, above)
        key.right + gap + size.width <= window.width - margin -> Offset(x(key.right + gap), y(key.center.y - size.height / 2f))
        key.left - gap - size.width >= margin -> Offset(x(key.left - gap - size.width), y(key.center.y - size.height / 2f))
        else -> Offset(centeredX, y(key.bottom + gap))
    }
    return IntOffset(position.x.roundToInt(), position.y.roundToInt())
}

/** A passive overlay in the same window: no focus or pointer handler can intercept typing. */
@Composable
internal fun KeyPressPreviewOverlay(preview: PressedKeyPreview?, modifier: Modifier = Modifier) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var window by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    Box(modifier.fillMaxSize().onGloballyPositioned {
        origin = it.positionInRoot()
        window = it.findRootCoordinates().size
    }) {
        if (preview != null && window.width > 0 && window.height > 0) {
            val font = if (preview.label.length <= 2) 26.sp else 18.sp
            val style = LocalTextStyle.current.copy(
                fontSize = font,
                lineHeight = font * 1.25f,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp,
            )
            val textWidth = measurer.measure(preview.label, style, maxLines = 1, softWrap = false).size.width
            val width = with(density) { textWidth.toDp() + 24.dp }.coerceIn(52.dp, 104.dp)
            val height = 48.dp
            val size = with(density) { IntSize(width.roundToPx(), height.roundToPx()) }
            val position = with(density) { keyPreviewPosition(preview.bounds, size, window, 8.dp.toPx(), 6.dp.toPx()) }
            val shape = RoundedCornerShape(10.dp)
            Box(
                modifier = Modifier.align(Alignment.TopStart)
                    .offset { IntOffset(position.x - origin.x.roundToInt(), position.y - origin.y.roundToInt()) }
                    .size(width, height)
                    .shadow(6.dp, shape)
                    .background(preview.background, shape)
                    .border(1.dp, preview.foreground.copy(alpha = 0.3f), shape)
                    .testTag("key_press_preview"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = preview.label,
                    color = preview.foreground,
                    style = style,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.testTag("key_press_preview_label"),
                )
            }
        }
    }
}
