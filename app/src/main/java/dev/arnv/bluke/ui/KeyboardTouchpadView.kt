package dev.arnv.bluke.ui

import android.content.SharedPreferences
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.arnv.bluke.bluetooth.BluetoothKeyboardManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.roundToInt

/** Both panels use the same HID session; modifiers remain held while moving the pointer. */
@Composable
fun KeyboardTouchpadView(
    btManager: BluetoothKeyboardManager,
    sharedPrefs: SharedPreferences,
    geometry: KeyboardGeometry,
    theme: KeyboardThemeDefinition,
    characterLayout: KeyboardCharacterLayout,
    activePressedKeys: List<Int>,
    isConnected: Boolean,
    isCapsLockActive: Boolean,
    isNumLockActive: Boolean,
    isScrollLockActive: Boolean,
    isFnActive: Boolean,
    keySensitivity: Float,
    modifier: Modifier = Modifier,
    keyboardOverlay: @Composable BoxScope.() -> Unit = {},
    onKeyPressChange: (Int, Boolean) -> Unit,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val currentKeyPressChange by rememberUpdatedState(onKeyPressChange)
    val swipeSlop = with(LocalDensity.current) { 10.dp.toPx() }
    val inputSession = remember(swipeSlop) {
        TouchKeyboardSession(swipeSlop, scope) { code, pressed -> currentKeyPressChange(code, pressed) }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputSession) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) inputSession.reset()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            inputSession.reset()
        }
    }
    LaunchedEffect(isConnected) { inputSession.reset() }
    val navigationKeys = remember(geometry) { touchNavigationLayout(geometry) }
    val keyboardRows = remember(geometry, characterLayout) {
        touchKeyboardLayout(geometry, characterLayout).maxOf { it.layout.y + it.layout.heightRatio }
    }
    var splitStep by rememberSaveable {
        mutableIntStateOf(sharedPrefs.getInt(SPLIT_WIDTH_STEP_PREFERENCE, 0).coerceIn(SplitPanePolicy.MIN_STEP, SplitPanePolicy.MAX_STEP))
    }
    // Keep the keyboard physically on the left even when Android uses an RTL locale.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(modifier = modifier) {
            val navigationHeight = maxOf(44.dp, (maxHeight - 4.dp) / keyboardRows + 4.dp)
            val availableWidth = (maxWidth.value - SplitPanePolicy.DIVIDER_WIDTH_DP).coerceAtLeast(1f)
            val targetKeyboardFraction = SplitPanePolicy.keyboardFraction(splitStep, availableWidth)
            val keyboardFraction by animateFloatAsState(
                targetValue = targetKeyboardFraction,
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                label = "keyboardTouchpadSplit",
            )
            val keyboardPercent = (targetKeyboardFraction * 100).roundToInt()
            val leftStep = SplitPanePolicy.nextStep(splitStep, -1, availableWidth)
            val rightStep = SplitPanePolicy.nextStep(splitStep, 1, availableWidth)
            fun resize(step: Int?) {
                if (step == null) return
                splitStep = step
                sharedPrefs.edit().putInt(SPLIT_WIDTH_STEP_PREFERENCE, step).apply()
            }
            Row(
                modifier = Modifier.fillMaxSize().testTag("keyboard_touchpad_view"),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(
                    modifier = Modifier.weight(keyboardFraction).fillMaxHeight().testTag("split_keyboard_panel"),
                    contentAlignment = Alignment.Center,
                ) {
                    KeyboardView(
                        geometry = geometry,
                        theme = theme,
                        characterLayout = characterLayout,
                        activePressedKeys = activePressedKeys,
                        isConnected = isConnected,
                        isCapsLockActive = isCapsLockActive,
                        isNumLockActive = isNumLockActive,
                        isScrollLockActive = isScrollLockActive,
                        isFnActive = isFnActive,
                        keySensitivity = keySensitivity,
                        fillAvailableSpace = true,
                        sharedInput = inputSession,
                        onKeyPressChange = onKeyPressChange,
                    )
                    keyboardOverlay()
                }

                Column(
                    modifier = Modifier.width(SplitPanePolicy.CONTROL_WIDTH_DP.dp).fillMaxHeight().testTag("split_width_controls"),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SplitWidthButton(Icons.AutoMirrored.Filled.ArrowBack, "Touchpad'i genişlet",
                        "widen_touchpad", leftStep != null) { resize(leftStep) }
                    Spacer(Modifier.height(6.dp))
                    SplitWidthButton(Icons.AutoMirrored.Filled.ArrowForward, "Klavyeyi genişlet",
                        "widen_keyboard", rightStep != null) { resize(rightStep) }
                }

                Column(
                    modifier = Modifier.weight(1f - keyboardFraction).fillMaxHeight()
                        .padding(top = 2.dp)
                        .testTag("split_touchpad_panel"),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Touchpad · ${100 - keyboardPercent}%",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E1E20))
                            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                            .testTag("split_touch_surface"),
                    ) {
                        TouchGestureLayer(
                            btManager = btManager,
                            sensitivity = sharedPrefs.getFloat("touchpad_sensitivity", 1.5f),
                            scrollSensitivity = sharedPrefs.getFloat("touchpad_scroll_sensitivity", 1f),
                            buttonMode = TrackpadButtonMode.TWO_BUTTONS,
                            triggerVibration = {
                                if (sharedPrefs.getBoolean("touchpad_vibration_enabled", true)) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            },
                            showNumpadLed = false,
                            onTouchStart = inputSession::activatePendingModifiers,
                        )
                        // Labels do not intercept gestures, including a held click followed by dragging.
                        Row(
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                .fillMaxHeight(0.18f)
                                .border(0.5.dp, Color.White.copy(alpha = 0.12f)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            listOf("L", "R").forEach { label ->
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    Text(
                        text = "Scroll: 2 fingers / right edge",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 9.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    Box(
                        modifier = Modifier.height(navigationHeight).fillMaxWidth().testTag("navigation_cluster"),
                    ) {
                        KeyboardView(
                            geometry = geometry,
                            theme = theme,
                            activePressedKeys = activePressedKeys,
                            isConnected = isConnected,
                            isCapsLockActive = isCapsLockActive,
                            isNumLockActive = isNumLockActive,
                            isScrollLockActive = isScrollLockActive,
                            fillAvailableSpace = true,
                            sharedInput = inputSession,
                            keyLayoutOverride = navigationKeys,
                            onKeyPressChange = onKeyPressChange,
                        )
                    }
                }
            }
            KeyPressPreviewOverlay(inputSession.keyPreview, Modifier.matchParentSize())
        }
    }
}

@Composable
private fun SplitWidthButton(icon: ImageVector, label: String, tag: String, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = Modifier.width(SplitPanePolicy.CONTROL_WIDTH_DP.dp).height(40.dp).clip(RoundedCornerShape(5.dp))
            .background(Color.White.copy(alpha = when { !enabled -> 0.01f; pressed -> 0.14f; else -> 0.035f }))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Color.White.copy(alpha = if (enabled) 0.85f else 0.2f), modifier = Modifier.size(14.dp))
    }
}
