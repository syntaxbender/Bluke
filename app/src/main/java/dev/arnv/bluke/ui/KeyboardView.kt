package dev.arnv.bluke.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection

@Suppress("UnusedBoxWithConstraintsScope")
@Composable
fun KeyboardView(
    geometry: KeyboardGeometry,
    theme: KeyboardThemeDefinition,
    characterLayout: KeyboardCharacterLayout = KeyboardCharacterLayout.US_QWERTY,
    activePressedKeys: List<Int>,
    isConnected: Boolean = false,
    isCapsLockActive: Boolean,
    isNumLockActive: Boolean,
    isScrollLockActive: Boolean,
    isFnActive: Boolean = false,
    keySensitivity: Float = 6f,
    fillAvailableSpace: Boolean = false,
    keyLayoutOverride: List<TouchKeyboardKey>? = null,
    sharedInput: TouchKeyboardSession? = null,
    selectedStyleId: String? = null,
    onKeySelected: ((KeyLayoutInfo) -> Unit)? = null,
    onKeyPressChange: (Int, Boolean) -> Unit
) {
    val currentKeyPressChange by rememberUpdatedState(onKeyPressChange)
    val scope = rememberCoroutineScope()
    val swipeSlop = with(LocalDensity.current) { 10.dp.toPx() }
    val localInput = remember(swipeSlop) {
        TouchKeyboardSession(swipeSlop, scope) { code, pressed -> currentKeyPressChange(code, pressed) }
    }
    val inputSession = sharedInput ?: localInput
    val inputOwner = remember { Any() }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val touchKeys = remember(geometry, characterLayout, fillAvailableSpace, keyLayoutOverride) {
                if (keyLayoutOverride != null) keyLayoutOverride
                else if (fillAvailableSpace) touchKeyboardLayout(geometry, characterLayout)
                else KeyboardLayouts.getLayout(geometry, characterLayout).flatten().map { TouchKeyboardKey(it) }
            }
            val allKeys = touchKeys.map { it.layout }
            val touchFeedback = inputSession.feedback

        val totalLayoutWidthInUnits = allKeys.maxOfOrNull { it.x + it.widthRatio } ?: 15.0f
        val totalLayoutHeightInUnits = allKeys.maxOfOrNull { it.y + it.heightRatio } ?: 5.0f

        val platePadding = if (fillAvailableSpace) 2.dp else 6.dp
        val outerBoxPadding = if (fillAvailableSpace) 0.dp else 6.dp

        // Adjust constraints perfectly to take up absolutely maximum available space without dead zones
        val usableWidthDp = maxWidth - (outerBoxPadding * 2) - (platePadding * 2)
        val usableHeightDp = maxHeight - (outerBoxPadding * 2) - (platePadding * 2)

        val widthBasedUnit = usableWidthDp / totalLayoutWidthInUnits
        val heightBasedUnit = usableHeightDp / totalLayoutHeightInUnits
        val baseUnitWidth = minOf(widthBasedUnit, heightBasedUnit)
        val horizontalUnit = if (fillAvailableSpace) widthBasedUnit else baseUnitWidth
        val verticalUnit = if (fillAvailableSpace) heightBasedUnit else baseUnitWidth

        val plateWidth = horizontalUnit * totalLayoutWidthInUnits
        val plateHeight = verticalUnit * totalLayoutHeightInUnits

        val context = androidx.compose.ui.platform.LocalContext.current
        val sharedPrefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }

        // Keyboard Case plate well holding absolutely-positioned keycaps (guarantees perfect grid alignments)
        Box(
            modifier = Modifier
                .width(plateWidth + (platePadding * 2))
                .height(plateHeight + (platePadding * 2))
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(10.dp),
                    spotColor = Color.Black.copy(alpha = 0.5f),
                    ambientColor = Color.Black
                )
                .background(Color(theme.plateArgb), RoundedCornerShape(10.dp))
                .border(1.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                .padding(platePadding)
                .pointerInput(horizontalUnit, verticalUnit, touchKeys, onKeySelected, keySensitivity, fillAvailableSpace, inputSession) {
                    if (fillAvailableSpace && onKeySelected == null) {
                        inputSession.attach(inputOwner,
                            touchKeys.map { key ->
                                CornerGestureKey(
                                    id = key.layout.styleId,
                                    keyCode = key.layout.keyCode,
                                    left = horizontalUnit.toPx() * key.layout.x,
                                    top = verticalUnit.toPx() * key.layout.y,
                                    width = horizontalUnit.toPx() * key.layout.widthRatio,
                                    height = verticalUnit.toPx() * key.layout.heightRatio,
                                    corners = key.corners,
                                    holdPrimaryAfterDelay = key.holdPrimaryAfterDelay,
                                )
                            },
                        )
                        try {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    inputSession.update(inputOwner, event.changes.map {
                                        KeyContact(it.id.value, it.position.x, it.position.y, it.pressed)
                                    }, event.changes.maxOfOrNull { it.uptimeMillis } ?: 0L)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        } finally {
                            inputSession.detach(inputOwner)
                        }
                        return@pointerInput
                    }
                        awaitPointerEventScope {
                            if (onKeySelected != null) {
                                var pointerWasDown = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressedChange = event.changes.firstOrNull { it.pressed }
                                    if (pressedChange != null && !pointerWasDown) {
                                        val pos = pressedChange.position
                                        allKeys.lastOrNull { key ->
                                            val keyLeft = horizontalUnit.toPx() * key.x
                                            val keyRight = keyLeft + horizontalUnit.toPx() * key.widthRatio
                                            val keyTop = verticalUnit.toPx() * key.y
                                            val keyBottom = keyTop + verticalUnit.toPx() * key.heightRatio
                                            pos.x in keyLeft..keyRight && pos.y in keyTop..keyBottom
                                        }?.let(onKeySelected)
                                    }
                                    pointerWasDown = pressedChange != null
                                    event.changes.forEach { it.consume() }
                                }
                            }

                            // Map of PointerId to List of KeyCodes currently being pressed by that pointer
                            val currentPointers = mutableMapOf<Long, List<Int>>()

                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val eventChanges = event.changes

                                    val newPointerStates = mutableMapOf<Long, List<Int>>()

                                    eventChanges.forEach { change ->
                                        if (change.pressed) {
                                            val pos = change.position
                                            val touchRadius = keySensitivity.dp.toPx()

                                            fun distanceToKeySquared(key: KeyLayoutInfo): Float {
                                                val keyLeft = horizontalUnit.toPx() * key.x
                                                val keyRight = keyLeft + horizontalUnit.toPx() * key.widthRatio
                                                val keyTop = verticalUnit.toPx() * key.y
                                                val keyBottom = keyTop + verticalUnit.toPx() * key.heightRatio

                                                // Closest point on the rectangle to the touch center
                                                val closestX = pos.x.coerceIn(keyLeft, keyRight)
                                                val closestY = pos.y.coerceIn(keyTop, keyBottom)

                                                val dx = pos.x - closestX
                                                val dy = pos.y - closestY

                                                return dx * dx + dy * dy
                                            }
                                            val intersectedKeys = allKeys.filter { key ->
                                                distanceToKeySquared(key) <= touchRadius * touchRadius
                                            }
                                            // A finger near an edge should type one character, while
                                            // separate fingers can still hold modifiers and other keys.
                                            val pressedKeys = if (fillAvailableSpace) {
                                                listOfNotNull(intersectedKeys.minByOrNull(::distanceToKeySquared)?.keyCode)
                                            } else {
                                                intersectedKeys.map { it.keyCode }
                                            }

                                            newPointerStates[change.id.value] = pressedKeys
                                            change.consume()
                                        }
                                    }

                                    // Synthesize differences to send onKeyPressChange
                                    val oldAllPressed = currentPointers.values.flatten().toSet()
                                    val newAllPressed = newPointerStates.values.flatten().toSet()

                                    val newlyPressed = newAllPressed - oldAllPressed
                                    val newlyReleased = oldAllPressed - newAllPressed

                                    newlyReleased.forEach { keyCode ->
                                        currentKeyPressChange(keyCode, false)
                                    }
                                    newlyPressed.forEach { keyCode ->
                                        currentKeyPressChange(keyCode, true)
                                    }

                                    currentPointers.clear()
                                    currentPointers.putAll(newPointerStates)
                                }
                            } finally {
                                // Resizing, changing geometry, or closing the panel must release held modifiers.
                                currentPointers.values.flatten().toSet().forEach { keyCode ->
                                    currentKeyPressChange(keyCode, false)
                                }
                            }
                        }
                    }
            ) {
                touchKeys.forEach { touchKey ->
                    val key = touchKey.layout
                    val keyX = key.x
                    val keyY = key.y

                    val keyWidth = horizontalUnit * key.widthRatio
                    val keyHeight = verticalUnit * key.heightRatio

                    val keyStyle = theme.styleFor(key)
                    val isLatched = key.keyCode in inputSession.latchedModifiers
                    val displayStyle = if (isLatched) theme.accentStyle else keyStyle
                    val keyColor = Color(displayStyle.backgroundArgb)
                    val legendColor = Color(displayStyle.legendArgb)

                    val isPressed = activePressedKeys.contains(key.keyCode) || key.styleId in inputSession.visualPressedKeys

                    val isShiftActive = (activePressedKeys + inputSession.latchedModifiers).any { it == 0xE1 || it == 0xE5 }
                    val isUppercase = isCapsLockActive xor isShiftActive
                    val isAlphabetic = key.legend.length == 1 && key.legend[0].isLetter()
                    val baseDisplayLegend = if (isAlphabetic) {
                        characterLayout.displayLetter(key.legend, isUppercase)
                    } else {
                        key.legend
                    }
                    val fnLegend = if (isFnActive) fnLegendForKey(key.keyCode) else null
                    val displayLegend = fnLegend ?: baseDisplayLegend
                    val displayShiftedLegend = if (fnLegend == null) key.shiftedLegend else ""

                    val isIndicatorActive = when (key.keyCode) {
                        0x39 -> isCapsLockActive
                        0x53 -> isNumLockActive
                        0x47 -> isScrollLockActive
                        else -> false
                    }

                    val finalGlowColor = if (isIndicatorActive) {
                        Color(theme.accentStyle.backgroundArgb).copy(alpha = 0.25f)
                    } else {
                        null
                    }

                    KeyCap(
                        legend = displayLegend,
                        shiftedLegend = displayShiftedLegend,
                        altGrLegend = if (fnLegend == null) key.altGrLegend else "",
                        shiftedAltGrLegend = if (fnLegend == null) key.shiftedAltGrLegend else "",
                        width = keyWidth,
                        height = keyHeight,
                        isPressed = isPressed,
                        isLatched = isLatched,
                        keyBgColor = keyColor,
                        legendColor = legendColor,
                        legendScale = keyStyle.legendScale,
                        isSelected = selectedStyleId == key.styleId,
                        baseUnitWidth = baseUnitWidth,
                        touchOptimized = fillAvailableSpace,
                        cornerActions = touchKey.corners,
                        selectedCorner = touchFeedback[key.styleId],
                        modifier = Modifier
                            .offset(
                                x = horizontalUnit * keyX,
                                y = verticalUnit * keyY
                            ),
                        glowColor = finalGlowColor
                    )
                }
            }
        }
        }
    }
