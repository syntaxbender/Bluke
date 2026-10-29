package dev.arnv.bluke.ui

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.arnv.bluke.bluetooth.BluetoothKeyboardManager
import dev.arnv.bluke.bluetooth.BluetoothState
import dev.arnv.bluke.bluetooth.CURRENT_HID_DESCRIPTOR_REVISION
import dev.arnv.bluke.bluetooth.HID_DESCRIPTOR_REVISION_PREFERENCE
import dev.arnv.bluke.BuildConfig
import dev.arnv.bluke.sound.KeyboardSoundSynthesizer
import dev.arnv.bluke.ui.theme.MyApplicationTheme
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w800dp-h360dp-land-xhdpi", sdk = [36], shadows = [KeyboardTouchpadViewTest.HidShadow::class])
class KeyboardTouchpadViewTest {
    @get:Rule val compose = createComposeRule()

    @Implements(BluetoothHidDevice::class)
    class HidShadow {
        companion object {
            val reports = CopyOnWriteArrayList<Pair<Int, List<Byte>>>()
        }
        @Implementation
        fun sendReport(device: BluetoothDevice, id: Int, data: ByteArray): Boolean {
            reports.add(id to data.toList())
            return true
        }
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = BluetoothKeyboardManager(context)
    private val visible = mutableStateOf(true)
    private val geometry = mutableStateOf(KeyboardGeometry.COMPACT_75)
    private val connected = mutableStateOf(true)

    @After fun closeManager() = manager.close()

    private fun attachHost() {
        HidShadow.reports.clear()
        val device = context.getSystemService(BluetoothManager::class.java).adapter
            .getRemoteDevice("00:11:22:33:44:55")
        ReflectionHelpers.setField(manager, "hidDeviceProfile", Shadow.newInstanceOf(BluetoothHidDevice::class.java))
        ReflectionHelpers.getField<MutableStateFlow<BluetoothDevice?>>(manager, "_connectedDevice").value = device
    }

    private fun show(direction: LayoutDirection = LayoutDirection.Ltr, keySensitivity: Float = 0f,
                     typingLayout: KeyboardCharacterLayout = KeyboardCharacterLayout.US_QWERTY,
                     theme: KeyboardThemeDefinition = KeyboardThemeCatalog.defaultTheme) {
        attachHost()
        compose.setContent {
            MyApplicationTheme {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    Box(Modifier.fillMaxSize().background(Color(theme.caseArgb))) {
                        if (visible.value) {
                            KeyboardTouchpadView(
                                btManager = manager,
                                sharedPrefs = context.getSharedPreferences("split-test", Context.MODE_PRIVATE),
                                geometry = geometry.value,
                                theme = theme,
                                characterLayout = typingLayout,
                                activePressedKeys = emptyList(),
                                isConnected = connected.value,
                                isCapsLockActive = false,
                                isNumLockActive = false,
                                isScrollLockActive = false,
                                isFnActive = false,
                                keySensitivity = keySensitivity,
                                modifier = Modifier.fillMaxSize(),
                                onKeyPressChange = manager::sendKey,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun awaitReports() {
        ReflectionHelpers.getField<ExecutorService>(manager, "reportExecutor")
            .submit {}.get(5, TimeUnit.SECONDS)
    }

    private fun modifierCenter(legend: String) = compose.onAllNodesWithTag("key_$legend", useUnmergedTree = true)
        .fetchSemanticsNodes().minBy { it.boundsInRoot.left }.boundsInRoot.center

    private fun tapModifier(legend: String) {
        val point = modifierCenter(legend)
        compose.onRoot().performTouchInput { click(point) }
    }

    private fun assertPanelsFit() {
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val keyboard = compose.onNodeWithTag("split_keyboard_panel").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val touchpad = compose.onNodeWithTag("split_touch_surface").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(keyboard.right < touchpad.left)
        assertTrue(touchpad.right <= root.right)
        assertTrue(touchpad.bottom <= root.bottom)
        assertTrue(touchpad.width >= root.width * 0.17f)
        compose.onNodeWithTag("key_F1", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("key_q", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("key_→", useUnmergedTree = true).assertIsDisplayed()

        val escape = compose.onNodeWithTag("key_Esc", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val letter = compose.onNodeWithTag("key_q", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val lastKey = compose.onAllNodesWithTag("key_Ctrl", useUnmergedTree = true).fetchSemanticsNodes()
            .maxBy { it.boundsInRoot.right }.boundsInRoot
        val rightArrow = compose.onNodeWithTag("key_→", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val navigation = compose.onNodeWithTag("navigation_cluster").fetchSemanticsNode().boundsInRoot
        val up = compose.onNodeWithTag("key_↑", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val down = compose.onNodeWithTag("key_↓", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(up.center.y, down.center.y, 1f)
        assertTrue(down.right <= up.left + 1f)
        assertEquals(up.width, rightArrow.width, 1f)
        assertTrue("Navigation should sit below the touchpad", navigation.top >= touchpad.bottom)
        assertTrue("Navigation must not narrow typing rows", navigation.left >= keyboard.right)
        assertEquals("The keyboard and arrows should share a baseline", lastKey.bottom, rightArrow.bottom, 1f)
        assertEquals("Navigation and typing rows should have matching height", letter.height, up.height, 1f)
        listOf("a", "z", "m").forEach { legend ->
            val bounds = compose.onNodeWithTag("key_$legend", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals("Typing key widths should match", letter.width, bounds.width, 1f)
            assertEquals("Typing key heights should match", letter.height, bounds.height, 1f)
        }
        listOf("1", "0").forEach { legend ->
            val bounds = compose.onNodeWithTag("key_$legend", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("Digit and letter widths should stay close", letter.width / bounds.width in 0.9f..1.1f)
        }
        listOf("Tab", "Caps Lock", "Shift").forEach { legend ->
            val bounds = compose.onAllNodesWithTag("key_$legend", useUnmergedTree = true).fetchSemanticsNodes()
                .minBy { it.boundsInRoot.left }.boundsInRoot
            assertEquals("Modifier keys should use normal key width", letter.width, bounds.width, 1f)
        }
        compose.onNodeWithTag("key_Fn", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("key_Home", useUnmergedTree = true).assertDoesNotExist()
        val delete = compose.onNodeWithTag("key_Delete", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals("Delete should stay on the top row", escape.top, delete.top, 1f)
        assertTrue("Keys should fill the panel from the top", escape.top - keyboard.top < keyboard.height * 0.02f)
        assertTrue("Keys should fill the panel to the bottom", keyboard.bottom - lastKey.bottom < keyboard.height * 0.02f)
        assertTrue("Each letter should use a full row's height", letter.height >= keyboard.height * 0.15f)
        assertTrue("The first row should start at the panel edge", escape.left - keyboard.left < keyboard.width * 0.02f)
        assertTrue("The last row should reach the panel edge", keyboard.right - lastKey.right < keyboard.width * 0.02f)
        val f4 = compose.onNodeWithTag("key_F4", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val f5 = compose.onNodeWithTag("key_F5", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Function keys should have no unused cluster gap", kotlin.math.abs(f5.left - f4.right) <= 2f)
        val textLayouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("corner_←_TOP_LEFT", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(textLayouts) }
        val textLayout = textLayouts.single()
        assertFalse("Small key labels should remain fully readable", textLayout.isLineEllipsized(0))
        assertEquals("Home".length, textLayout.getLineEnd(0, visibleEnd = true))
    }

    @Test fun landscapeShowsBothPanelsAndThe75PercentKeyboard() {
        show()
        compose.onRoot().captureRoboImage(filePath = "build/outputs/screenshots/keyboard-touchpad.png")
        assertPanelsFit()
    }

    @Test fun defaultKeyboardStillOpensTheOriginalMechanicalLayout() {
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
            .putInt("last_run_version_code", BuildConfig.VERSION_CODE)
            .putInt(HID_DESCRIPTOR_REVISION_PREFERENCE, CURRENT_HID_DESCRIPTOR_REVISION)
            .putBoolean("key_sound_enabled", false)
            .putStringSet("cycle_connection_modes", defaultInputModeKeys)
            .putInt("launch_mode", InputMode.KEYBOARD.id)
            .apply()
        ReflectionHelpers.getField<MutableStateFlow<BluetoothState>>(manager, "_serviceState").value =
            BluetoothState.ReadyDisconnected
        val sound = KeyboardSoundSynthesizer(context)
        try {
            compose.setContent { MyApplicationTheme { HomeScreen(manager, sound) } }
            compose.onNodeWithText("Launch Keyboard").assertIsDisplayed()
            compose.onNodeWithTag("start_keyboard_btn").performClick()
            compose.onNodeWithTag("mechanical_keyboard_view").assertIsDisplayed()
            compose.onNodeWithTag("keyboard_touchpad_view").assertDoesNotExist()
            compose.onNodeWithTag("key_q", useUnmergedTree = true).assertIsDisplayed()
        } finally {
            sound.release()
        }
    }

    @Test fun launchButtonOpensBothPanelsAndCloseReturnsToSetup() {
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
            .putInt("last_run_version_code", BuildConfig.VERSION_CODE)
            .putInt(HID_DESCRIPTOR_REVISION_PREFERENCE, CURRENT_HID_DESCRIPTOR_REVISION)
            .putBoolean("key_sound_enabled", false)
            .putStringSet("cycle_connection_modes", defaultInputModeKeys + InputMode.SPLIT_KEYBOARD.preferenceKey)
            .putInt("launch_mode", InputMode.SPLIT_KEYBOARD.id)
            .apply()
        ReflectionHelpers.getField<MutableStateFlow<BluetoothState>>(manager, "_serviceState").value =
            BluetoothState.ReadyDisconnected
        val sound = KeyboardSoundSynthesizer(context)
        try {
            compose.setContent { MyApplicationTheme { HomeScreen(manager, sound) } }
            compose.onNodeWithTag("start_keyboard_btn").performClick()
            assertPanelsFit()
            compose.onRoot().captureRoboImage(filePath = "build/outputs/screenshots/keyboard-touchpad-screen.png")
            compose.onNodeWithTag("exit_keyboard_btn").performClick()
            compose.onNodeWithTag("start_keyboard_btn").assertIsDisplayed()
            compose.onNodeWithTag("keyboard_touchpad_view").assertDoesNotExist()
        } finally {
            sound.release()
        }
    }

    @Test
    @Config(qualifiers = "w640dp-h320dp-land-xhdpi")
    fun smallLandscapePhoneKeepsBothPanelsVisible() {
        show()
        assertPanelsFit()
        compose.onRoot().captureRoboImage(filePath = "build/outputs/screenshots/keyboard-touchpad-small.png")
    }

    @Test fun rtlLocaleStillKeepsKeyboardOnTheLeft() {
        show(LayoutDirection.Rtl)
        assertPanelsFit()
    }

    @Test fun paneControlsResizeWithinLimitsAndReturnToTheDefaultWidth() {
        show()
        val original = compose.onNodeWithTag("split_keyboard_panel").fetchSemanticsNode().boundsInRoot.width
        repeat(2) { compose.onNodeWithTag("widen_touchpad").performClick() }
        compose.onNodeWithTag("widen_touchpad").assertIsNotEnabled()
        assertPanelsFit()
        assertTrue(compose.onNodeWithTag("split_keyboard_panel").fetchSemanticsNode().boundsInRoot.width < original)
        compose.mainClock.advanceTimeBy(700L)
        compose.onRoot().captureRoboImage(filePath = "build/outputs/screenshots/keyboard-touchpad-touch-wide.png")
        repeat(4) { compose.onNodeWithTag("widen_keyboard").performClick() }
        compose.onNodeWithTag("widen_keyboard").assertIsNotEnabled()
        assertPanelsFit()
        assertTrue(compose.onNodeWithTag("split_keyboard_panel").fetchSemanticsNode().boundsInRoot.width > original)
        compose.mainClock.advanceTimeBy(700L)
        compose.onRoot().captureRoboImage(filePath = "build/outputs/screenshots/keyboard-touchpad-keyboard-wide.png")
        repeat(2) { compose.onNodeWithTag("widen_touchpad").performClick() }
        assertEquals(original, compose.onNodeWithTag("split_keyboard_panel").fetchSemanticsNode().boundsInRoot.width, 1f)
        assertEquals(0, context.getSharedPreferences("split-test", Context.MODE_PRIVATE).getInt(SPLIT_WIDTH_STEP_PREFERENCE, 99))
    }

    @Test fun turkishQKeepsItsLettersLegibleAndUsesPhysicalBluetoothCodes() {
        show(typingLayout = KeyboardCharacterLayout.TURKISH_Q,
            theme = KeyboardThemeCatalog.builtIn(BuiltinKeyboardTheme.DRACULA.id)!!)
        assertPanelsFit()
        mapOf("shift_." to ":", "shift_," to ";", "altgr_q" to "@", "altgr_e" to "€",
            "altgr_t" to "₺", "altgr_7" to "{", "altgr_8" to "[", "altgr_*" to "\\",
            "altgr_-" to "|", "shift_altgr_a" to "Æ").forEach { (tag, symbol) ->
            val node = compose.onNodeWithTag(tag, useUnmergedTree = true)
                .assertIsDisplayed().assertTextEquals(symbol)
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            // A no-wrap paragraph keeps the available key width even when Text wraps its own bounds to the glyph.
            // Check the actual line instead of hasVisualOverflow, which compares those two different widths.
            assertFalse("Alternate symbol $symbol must not be ellipsized", layout.isLineEllipsized(0))
            assertTrue("Alternate symbol $symbol must fit horizontally",
                layout.getLineRight(0) - layout.getLineLeft(0) <= layout.size.width + 1f)
            assertTrue("Alternate symbol $symbol must fit vertically", layout.multiParagraph.height <= layout.size.height + 1f)
        }
        mapOf("ı" to KeyboardLayouts.KEY_I, "i" to KeyboardLayouts.KEY_APOSTROPHE,
            "ğ" to KeyboardLayouts.KEY_LBRACKET, "ü" to KeyboardLayouts.KEY_RBRACKET,
            "ş" to KeyboardLayouts.KEY_SEMICOLON, "ö" to KeyboardLayouts.KEY_COMMA,
            "ç" to KeyboardLayouts.KEY_PERIOD).forEach { (legend, code) ->
            compose.onNodeWithTag("key_$legend", useUnmergedTree = true).assertIsDisplayed().performTouchInput { click() }
            awaitReports()
            assertTrue(HidShadow.reports.any { (id, bytes) -> id == 1 && bytes[2] == code.toByte() })
        }
        val caps = compose.onNodeWithTag("key_Caps Lock", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        val shift = modifierCenter("Shift")
        compose.onRoot().performTouchInput {
            down(0, shift)
            down(1, caps)
            moveTo(1, caps + Offset(-24f, -24f))
            up(1)
            up(0)
        }
        awaitReports()
        assertTrue("Turkish ISO > should be available through Shift and the Caps corner",
            HidShadow.reports.any { (id, bytes) -> id == 1 && bytes[0] == 2.toByte() && bytes[2] == KeyboardLayouts.KEY_NONUS_BACKSLASH.toByte() })
        tapModifier("Shift")
        val altGr = modifierCenter("AltGr")
        listOf(
            Triple(listOf(shift), ".", 0x02 to KeyboardLayouts.KEY_SLASH),
            Triple(listOf(altGr), "q", 0x40 to KeyboardLayouts.KEY_Q),
            Triple(listOf(altGr), "7", 0x40 to KeyboardLayouts.KEY_7),
            Triple(listOf(shift, altGr), "a", 0x42 to KeyboardLayouts.KEY_A),
        ).forEach { (modifiers, legend, expected) ->
            HidShadow.reports.clear()
            val key = compose.onNodeWithTag("key_$legend", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
            compose.onRoot().performTouchInput {
                modifiers.forEachIndexed { index, point -> down(index, point) }
                down(modifiers.size, key)
                up(modifiers.size)
                modifiers.indices.reversed().forEach { up(it) }
            }
            awaitReports()
            assertTrue("$legend must retain its HID usage with the displayed modifiers",
                HidShadow.reports.any { (id, bytes) -> id == 1 && bytes[0] == expected.first.toByte() && bytes[2] == expected.second.toByte() })
            assertEquals(expected.first, ReflectionHelpers.getField<Int>(manager, "activeModifiers"))
            modifiers.forEach { point -> compose.onRoot().performTouchInput { click(point) } }
            awaitReports()
            assertTrue("A second tap must release the latched modifiers", HidShadow.reports.last().second.all { it == 0.toByte() })
        }
        compose.mainClock.advanceTimeBy(KEY_RELEASE_FEEDBACK_MILLIS + 32L)
        compose.onRoot().captureRoboImage(filePath = "build/outputs/screenshots/keyboard-touchpad-turkish-q.png")
    }

    @Test fun movingTouchpadWithLatchedCtrlKeepsModifierDownUntilTappedAgain() {
        show()
        val ctrl = modifierCenter("Ctrl")
        val pad = compose.onNodeWithTag("split_touch_surface").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { click(ctrl) }
        compose.onRoot().performTouchInput {
            down(1, pad)
            moveTo(1, pad + Offset(40f, 0f))
            up(1)
        }
        awaitReports()
        assertEquals(1, ReflectionHelpers.getField<Int>(manager, "activeModifiers"))
        assertTrue(HidShadow.reports.any { (id, bytes) -> id == 1 && bytes[0] == 1.toByte() })
        assertTrue(HidShadow.reports.any { (id, bytes) -> id == 2 && bytes[1] != 0.toByte() })

        compose.onRoot().performTouchInput { click(ctrl) }
        awaitReports()
        assertEquals(0, ReflectionHelpers.getField<Int>(manager, "activeModifiers"))
        assertTrue(HidShadow.reports.last { it.first == 1 }.second.all { it == 0.toByte() })
    }

    @Test fun tappingNearEitherEdgeOfALetterTypesOnlyThatLetter() {
        show(keySensitivity = 6f)
        val letter = compose.onNodeWithTag("key_q", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        listOf(Offset(letter.left + 1f, letter.center.y), Offset(letter.right - 1f, letter.center.y)).forEach { position ->
            compose.onRoot().performTouchInput { down(0, position) }
            awaitReports()
            val bytes = HidShadow.reports.last { it.first == 1 }.second
            assertEquals(KeyboardLayouts.KEY_Q.toByte(), bytes[2])
            assertTrue("One finger must not press adjacent letters", bytes.drop(3).all { it == 0.toByte() })
            compose.onRoot().performTouchInput { up(0) }
        }
    }

    @Test fun closingOrChangingKeyboardGeometryReleasesHeldCtrl() {
        show()
        fun holdCtrl() {
            val ctrl = modifierCenter("Ctrl")
            compose.onRoot().performTouchInput { click(ctrl) }
            assertEquals(1, ReflectionHelpers.getField<Int>(manager, "activeModifiers"))
        }
        holdCtrl()
        compose.runOnIdle { geometry.value = KeyboardGeometry.HHKB_60 }
        compose.waitForIdle()
        assertEquals(0, ReflectionHelpers.getField<Int>(manager, "activeModifiers"))

        holdCtrl()
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        awaitReports()
        assertEquals(0, ReflectionHelpers.getField<Int>(manager, "activeModifiers"))
        assertTrue(HidShadow.reports.last { it.first == 1 }.second.all { it == 0.toByte() })
    }

    @Test fun draggingLeftWithCtrlAltOrWinSendsTheCornerCombinationWithoutAnArrow() {
        show()
        val left = compose.onNodeWithTag("key_←", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        listOf("Ctrl" to 1, "Alt" to 4, "Win" to 8).forEach { (legend, mask) ->
            HidShadow.reports.clear()
            val modifier = modifierCenter(legend)
            compose.onRoot().performTouchInput {
                down(0, modifier)
                down(1, left)
                moveTo(1, left + Offset(-24f, -24f))
                up(1)
            }
            awaitReports()
            assertTrue(HidShadow.reports.any { (id, bytes) ->
                id == 1 && bytes[0].toInt() == mask && bytes[2] == KeyboardLayouts.KEY_HOME.toByte()
            })
            assertFalse(HidShadow.reports.any { (id, bytes) -> id == 1 && KeyboardLayouts.KEY_LEFT.toByte() in bytes.drop(2) })
            compose.onRoot().performTouchInput { up(0) }
            assertEquals(mask, ReflectionHelpers.getField<Int>(manager, "activeModifiers"))
            tapModifier(legend)
            awaitReports()
            assertEquals(0, ReflectionHelpers.getField<Int>(manager, "activeModifiers"))
        }
    }

    @Test fun ctrlShiftEndStillWorksWhenModifiersAreReleasedBeforeTheCorner() {
        show()
        val right = compose.onNodeWithTag("key_→", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        val ctrl = modifierCenter("Ctrl")
        val shift = modifierCenter("Shift")
        compose.onRoot().performTouchInput {
            down(0, ctrl)
            down(1, shift)
            down(2, right)
            up(0)
            up(1)
            moveTo(2, right + Offset(24f, -24f))
            up(2)
        }
        awaitReports()
        assertTrue(HidShadow.reports.any { (id, bytes) ->
            id == 1 && bytes[0].toInt() == 3 && bytes[2] == KeyboardLayouts.KEY_END.toByte()
        })
        assertEquals(3, ReflectionHelpers.getField<Int>(manager, "activeModifiers"))
        tapModifier("Ctrl")
        tapModifier("Shift")
        awaitReports()
        assertTrue(HidShadow.reports.last { it.first == 1 }.second.all { it == 0.toByte() })
    }

    @Test fun anArbitraryLetterACornerUsesRealBluetoothModifierReports() {
        show()
        val gesture = CornerKeyGesture(
            keys = listOf(
                CornerGestureKey("secondary", KeyboardLayouts.KEY_TAB, 0f, 0f, 100f, 100f,
                    mapOf(KeyCorner.TOP_RIGHT to CornerAction("A", KeyboardLayouts.KEY_A))),
                CornerGestureKey("ctrl", KeyboardLayouts.MOD_LCTRL, 100f, 0f, 100f, 100f),
            ), swipeSlop = 10f, sendKey = manager::sendKey,
        )
        gesture.process(listOf(KeyContact(0, 150f, 50f, true), KeyContact(1, 50f, 50f, true)))
        gesture.process(listOf(KeyContact(0, 150f, 50f, true), KeyContact(1, 80f, 20f, true)))
        gesture.process(listOf(KeyContact(0, 150f, 50f, false), KeyContact(1, 80f, 20f, false)))
        awaitReports()
        assertTrue(HidShadow.reports.any { (id, bytes) ->
            id == 1 && bytes[0] == 1.toByte() && bytes[2] == KeyboardLayouts.KEY_A.toByte()
        })
        assertFalse(HidShadow.reports.any { (id, bytes) -> id == 1 && KeyboardLayouts.KEY_TAB.toByte() in bytes.drop(2) })
        assertTrue(HidShadow.reports.last { it.first == 1 }.second.all { it == 0.toByte() })
    }

    @Test fun winFnCornerControlsMediaWithoutSendingWinOrF1() {
        attachHost()
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
            .putInt("last_run_version_code", BuildConfig.VERSION_CODE)
            .putInt(HID_DESCRIPTOR_REVISION_PREFERENCE, CURRENT_HID_DESCRIPTOR_REVISION)
            .putBoolean("key_sound_enabled", false)
            .putStringSet("cycle_connection_modes", defaultInputModeKeys + InputMode.SPLIT_KEYBOARD.preferenceKey)
            .putInt("launch_mode", InputMode.SPLIT_KEYBOARD.id)
            .apply()
        ReflectionHelpers.getField<MutableStateFlow<BluetoothState>>(manager, "_serviceState").value =
            BluetoothState.ReadyDisconnected
        val sound = KeyboardSoundSynthesizer(context)
        try {
            compose.setContent { MyApplicationTheme { HomeScreen(manager, sound) } }
            compose.onNodeWithTag("start_keyboard_btn").performClick()
            val win = modifierCenter("Win")
            val f1 = compose.onNodeWithTag("key_F1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
            compose.onRoot().performTouchInput {
                down(0, win)
                moveTo(0, win + Offset(-24f, -24f))
                down(1, f1)
                up(1)
                up(0)
            }
            awaitReports()
            assertTrue(HidShadow.reports.any { (id, bytes) -> id == 4 && bytes[0] == 0xE2.toByte() })
            assertTrue(HidShadow.reports.last { it.first == 4 }.second.all { it == 0.toByte() })
            assertFalse(HidShadow.reports.any { (id, bytes) -> id == 1 &&
                (bytes[0].toInt() and 8 != 0 || KeyboardLayouts.KEY_F1.toByte() in bytes.drop(2))
            })
        } finally {
            sound.release()
        }
    }

    @Test fun holdingAnArrowUnderCtrlWorksAcrossTheKeyboardAndNavigationPanels() {
        show()
        val ctrl = modifierCenter("Ctrl")
        val up = compose.onNodeWithTag("key_↑", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        val pad = compose.onNodeWithTag("split_touch_surface").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput {
            down(0, ctrl)
            down(1, up)
            down(2, pad)
            moveTo(2, pad + Offset(20f, 0f))
            up(2)
        }
        compose.mainClock.advanceTimeBy(CornerKeyGesture.HOLD_DELAY_MILLIS + 16L)
        awaitReports()
        assertTrue(HidShadow.reports.any { (id, bytes) ->
            id == 1 && bytes[0] == 1.toByte() && bytes[2] == KeyboardLayouts.KEY_UP.toByte()
        })
        assertEquals(KeyboardLayouts.KEY_UP.toByte(), HidShadow.reports.last { it.first == 1 }.second[2])
        compose.onRoot().performTouchInput { up(0); up(1) }
        assertEquals(1, ReflectionHelpers.getField<Int>(manager, "activeModifiers"))
        tapModifier("Ctrl")
        awaitReports()
        assertTrue(HidShadow.reports.last { it.first == 1 }.second.all { it == 0.toByte() })
    }

    @Test fun tappingWinLatchesUntilTheNextTapAndStillCombinesWithA() {
        show()
        val win = modifierCenter("Win")
        val a = compose.onNodeWithTag("key_a", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { down(0, win); up(0) }
        awaitReports()
        assertTrue(HidShadow.reports.any { (id, bytes) -> id == 1 && bytes[0] == 8.toByte() })
        assertEquals(8, ReflectionHelpers.getField<Int>(manager, "activeModifiers"))
        HidShadow.reports.clear()
        compose.onRoot().performTouchInput { click(a) }
        awaitReports()
        assertTrue(HidShadow.reports.any { (id, bytes) ->
            id == 1 && bytes[0] == 8.toByte() && bytes[2] == KeyboardLayouts.KEY_A.toByte()
        })
        assertEquals(8, ReflectionHelpers.getField<Int>(manager, "activeModifiers"))
        tapModifier("Win")
        awaitReports()
        assertTrue(HidShadow.reports.last { it.first == 1 }.second.all { it == 0.toByte() })
    }

    @Test fun deleteIsADirectTopRowKeyAndArrowsStillTapNormally() {
        show()
        listOf("Delete" to KeyboardLayouts.KEY_DELETE, "↑" to KeyboardLayouts.KEY_UP,
            "↓" to KeyboardLayouts.KEY_DOWN, "←" to KeyboardLayouts.KEY_LEFT, "→" to KeyboardLayouts.KEY_RIGHT).forEach { (legend, code) ->
            HidShadow.reports.clear()
            compose.onNodeWithTag("key_$legend", useUnmergedTree = true).performTouchInput { click() }
            awaitReports()
            assertTrue(HidShadow.reports.any { (id, bytes) -> id == 1 && bytes[2] == code.toByte() })
            assertTrue(HidShadow.reports.last { it.first == 1 }.second.all { it == 0.toByte() })
        }
    }

    @Test fun sequentialCtrlShiftTapsStayHighlightedAcrossLettersAndCornerNavigation() {
        show(theme = KeyboardThemeCatalog.builtIn(BuiltinKeyboardTheme.DRACULA.id)!!)
        tapModifier("Ctrl")
        tapModifier("Shift")
        compose.mainClock.advanceTimeBy(KEY_RELEASE_FEEDBACK_MILLIS + 32L)
        compose.onAllNodesWithTag("key_Ctrl", useUnmergedTree = true)[0].assertIsSelected()
        compose.onAllNodesWithTag("key_Shift", useUnmergedTree = true)[0].assertIsSelected()
        compose.onNodeWithTag("key_A", useUnmergedTree = true).assertIsDisplayed()
        HidShadow.reports.clear()
        listOf("A" to KeyboardLayouts.KEY_A, "C" to KeyboardLayouts.KEY_C).forEach { (legend, code) ->
            compose.onNodeWithTag("key_$legend", useUnmergedTree = true).performTouchInput { click() }
            awaitReports()
            assertTrue(HidShadow.reports.any { (id, bytes) -> id == 1 && bytes[0] == 3.toByte() && bytes[2] == code.toByte() })
            assertEquals(3, ReflectionHelpers.getField<Int>(manager, "activeModifiers"))
        }
        val left = compose.onNodeWithTag("key_←", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput {
            down(0, left)
            moveTo(0, left + Offset(-24f, -24f))
            up(0)
        }
        awaitReports()
        assertTrue(HidShadow.reports.any { (id, bytes) -> id == 1 && bytes[0] == 3.toByte() && bytes[2] == KeyboardLayouts.KEY_HOME.toByte() })
        assertTrue(HidShadow.reports.filter { it.first == 1 }.all { it.second[0] == 3.toByte() })
        compose.mainClock.advanceTimeBy(KEY_RELEASE_FEEDBACK_MILLIS + 32L)
        compose.onRoot().captureRoboImage(filePath = "build/outputs/screenshots/keyboard-touchpad-sticky-modifiers.png")
        tapModifier("Shift")
        tapModifier("Ctrl")
        compose.onAllNodesWithTag("key_Ctrl", useUnmergedTree = true)[0].assertIsNotSelected()
        compose.onAllNodesWithTag("key_Shift", useUnmergedTree = true)[0].assertIsNotSelected()
        HidShadow.reports.clear()
        compose.onNodeWithTag("key_a", useUnmergedTree = true).performTouchInput { click() }
        awaitReports()
        assertTrue(HidShadow.reports.any { (id, bytes) -> id == 1 && bytes[0] == 0.toByte() && bytes[2] == KeyboardLayouts.KEY_A.toByte() })
        assertTrue(HidShadow.reports.last { it.first == 1 }.second.all { it == 0.toByte() })
    }

    @Test fun connectionChangesClearLatchedModifiersAndTheirHighlights() {
        show()
        tapModifier("Ctrl")
        tapModifier("Alt")
        assertEquals(5, ReflectionHelpers.getField<Int>(manager, "activeModifiers"))
        compose.runOnIdle { connected.value = false }
        compose.waitForIdle()
        assertEquals(0, ReflectionHelpers.getField<Int>(manager, "activeModifiers"))
        compose.onAllNodesWithTag("key_Ctrl", useUnmergedTree = true)[0].assertIsNotSelected()
        compose.onAllNodesWithTag("key_Alt", useUnmergedTree = true)[0].assertIsNotSelected()
        compose.runOnIdle { connected.value = true }
        compose.waitForIdle()
        assertEquals(0, ReflectionHelpers.getField<Int>(manager, "activeModifiers"))
    }
}
