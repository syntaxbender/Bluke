package dev.arnv.bluke.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TouchKeyboardSessionTest {
    private val control = CornerGestureKey("ctrl", KeyboardLayouts.MOD_LCTRL, 0f, 0f, 100f, 100f)
    private val left = CornerGestureKey("left", KeyboardLayouts.KEY_LEFT, 0f, 0f, 100f, 100f,
        mapOf(KeyCorner.TOP_LEFT to CornerAction("Home", KeyboardLayouts.KEY_HOME)), holdPrimaryAfterDelay = true)

    @Test fun cornerPreviewLingersWithoutDelayingTheBluetoothRelease() = runTest {
        val events = mutableListOf<Pair<Int, Boolean>>()
        val session = TouchKeyboardSession(10f, this) { code, pressed -> events += code to pressed }
        val owner = Any()
        session.attach(owner, listOf(left))
        session.updatePreviewKeys(owner, listOf(KeyPreviewInfo("left", Rect(0f, 0f, 100f, 100f), "←",
            left.corners, Color.Red, Color.Black)))
        session.update(owner, listOf(KeyContact(0, 50f, 50f, true)), 0)
        assertEquals("←", session.keyPreview?.label)
        session.update(owner, listOf(KeyContact(0, 20f, 20f, true)), 10)
        assertEquals("Home", session.keyPreview?.label)
        session.update(owner, listOf(KeyContact(0, 20f, 20f, false)), 20)
        assertEquals(listOf(KeyboardLayouts.KEY_HOME to true, KeyboardLayouts.KEY_HOME to false), events)
        assertEquals("Home", session.keyPreview?.label)
        advanceTimeBy(KEY_PREVIEW_RELEASE_MILLIS - 1)
        runCurrent()
        assertNotNull(session.keyPreview)
        advanceTimeBy(1)
        runCurrent()
        assertNull(session.keyPreview)
    }

    @Test fun anOlderReleaseTimerCannotHideTheNextHeldKeyPreview() = runTest {
        val session = TouchKeyboardSession(10f, this) { _, _ -> }
        val owner = Any()
        val a = CornerGestureKey("a", KeyboardLayouts.KEY_A, 100f, 0f, 100f, 100f)
        session.attach(owner, listOf(control, a))
        session.updatePreviewKeys(owner, listOf(
            KeyPreviewInfo("ctrl", Rect(0f, 0f, 100f, 100f), "Ctrl", background = Color.Red, foreground = Color.Black),
            KeyPreviewInfo("a", Rect(100f, 0f, 200f, 100f), "a", background = Color.Red, foreground = Color.Black),
        ))
        session.update(owner, listOf(KeyContact(0, 50f, 50f, true)), 0)
        session.update(owner, listOf(KeyContact(0, 50f, 50f, false)), 1)
        advanceTimeBy(100)
        session.update(owner, listOf(KeyContact(0, 150f, 50f, true)), 101)
        advanceTimeBy(KEY_PREVIEW_RELEASE_MILLIS + 1)
        runCurrent()
        assertEquals("a", session.keyPreview?.label)
        session.reset()
        assertNull(session.keyPreview)
    }

    @Test fun regionsShareModifiersEvenWhenReleasedBeforeTheSwipeFinishes() = runTest {
        val events = mutableListOf<Pair<Int, Boolean>>()
        val session = TouchKeyboardSession(10f, this) { code, pressed -> events += code to pressed }
        val keyboard = Any()
        val navigation = Any()
        session.attach(keyboard, listOf(control))
        session.attach(navigation, listOf(left))
        session.update(keyboard, listOf(KeyContact(0, 50f, 50f, true)), 0)
        session.update(navigation, listOf(KeyContact(1, 50f, 50f, true)), 50)
        session.update(keyboard, listOf(KeyContact(0, 50f, 50f, false)), 100)
        assertEquals(listOf(KeyboardLayouts.MOD_LCTRL to true), events)
        session.update(navigation, listOf(KeyContact(1, 20f, 20f, true)), 150)
        session.update(navigation, listOf(KeyContact(1, 20f, 20f, false)), 200)
        advanceUntilIdle()
        assertEquals(listOf(KeyboardLayouts.MOD_LCTRL to true, KeyboardLayouts.KEY_HOME to true,
            KeyboardLayouts.KEY_HOME to false), events)
        assertEquals(setOf(KeyboardLayouts.MOD_LCTRL), session.latchedModifiers)
        session.update(keyboard, listOf(KeyContact(0, 50f, 50f, true)), 250)
        session.update(keyboard, listOf(KeyContact(0, 50f, 50f, false)), 260)
        assertEquals(KeyboardLayouts.MOD_LCTRL to false, events.last())
        assertTrue(session.latchedModifiers.isEmpty())
    }

    @Test fun theHoldTimerStartsARealArrowPressWithoutAnotherTouchEvent() = runTest {
        val events = mutableListOf<Pair<Int, Boolean>>()
        val session = TouchKeyboardSession(10f, this) { code, pressed -> events += code to pressed }
        val navigation = Any()
        session.attach(navigation, listOf(left))
        session.update(navigation, listOf(KeyContact(0, 50f, 50f, true)), 0)
        advanceTimeBy(CornerKeyGesture.HOLD_DELAY_MILLIS - 1)
        runCurrent()
        assertTrue(events.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(KeyboardLayouts.KEY_LEFT to true), events)
        session.update(navigation, listOf(KeyContact(0, 50f, 50f, false)), 500)
        advanceUntilIdle()
        assertEquals(listOf(KeyboardLayouts.KEY_LEFT to true, KeyboardLayouts.KEY_LEFT to false), events)
    }

    @Test fun removingAPanelCancelsItsPendingGestureAndReleasesBorrowedModifiers() = runTest {
        val events = mutableListOf<Pair<Int, Boolean>>()
        val session = TouchKeyboardSession(10f, this) { code, pressed -> events += code to pressed }
        val keyboard = Any()
        val navigation = Any()
        session.attach(keyboard, listOf(control))
        session.attach(navigation, listOf(left))
        session.update(keyboard, listOf(KeyContact(0, 50f, 50f, true)), 0)
        session.update(navigation, listOf(KeyContact(1, 50f, 50f, true)), 50)
        session.update(keyboard, listOf(KeyContact(0, 50f, 50f, false)), 100)
        session.detach(navigation)
        advanceUntilIdle()
        assertEquals(listOf(KeyboardLayouts.MOD_LCTRL to true, KeyboardLayouts.MOD_LCTRL to false), events)
    }

    @Test fun aQuickTapKeepsVisualFeedbackWhileTheBluetoothKeyIsAlreadyReleased() = runTest {
        val events = mutableListOf<Pair<Int, Boolean>>()
        val session = TouchKeyboardSession(10f, this) { code, pressed -> events += code to pressed }
        val keyboard = Any()
        session.attach(keyboard, listOf(CornerGestureKey("a", KeyboardLayouts.KEY_A, 0f, 0f, 100f, 100f)))
        session.update(keyboard, listOf(KeyContact(0, 50f, 50f, true)), 0)
        session.update(keyboard, listOf(KeyContact(0, 50f, 50f, false)), 10)
        assertEquals(listOf(KeyboardLayouts.KEY_A to true, KeyboardLayouts.KEY_A to false), events)
        assertTrue("Even a tap between display frames must stay visible", "a" in session.visualPressedKeys)
        advanceTimeBy(KEY_RELEASE_FEEDBACK_MILLIS - 1)
        runCurrent()
        assertTrue("a" in session.visualPressedKeys)
        advanceTimeBy(1)
        runCurrent()
        assertFalse("a" in session.visualPressedKeys)
        assertEquals(2, events.size)
    }

    @Test fun everyHidModifierStaysLatchedAcrossRepeatedLettersUntilTappedAgain() = runTest {
        (0xE0..0xE7).forEach { modifier ->
            val events = mutableListOf<Pair<Int, Boolean>>()
            val session = TouchKeyboardSession(10f, this) { code, pressed -> events += code to pressed }
            val owner = Any()
            session.attach(owner, listOf(control.copy(keyCode = modifier),
                CornerGestureKey("a", KeyboardLayouts.KEY_A, 100f, 0f, 100f, 100f)))
            fun tap(x: Float) {
                session.update(owner, listOf(KeyContact(0, x, 50f, true)), 0)
                session.update(owner, listOf(KeyContact(0, x, 50f, false)), 1)
            }
            tap(50f)
            assertEquals(setOf(modifier), session.latchedModifiers)
            repeat(2) { tap(150f) }
            advanceUntilIdle()
            assertEquals(listOf(modifier to true, KeyboardLayouts.KEY_A to true, KeyboardLayouts.KEY_A to false,
                KeyboardLayouts.KEY_A to true, KeyboardLayouts.KEY_A to false), events)
            tap(50f)
            assertTrue(session.latchedModifiers.isEmpty())
            assertEquals(modifier to false, events.last())
            assertEquals(6, events.size)
            session.detach(owner)
        }
    }

    @Test fun turningALatchedModifierOffAlsoRemovesItFromAPendingCorner() = runTest {
        val events = mutableListOf<Pair<Int, Boolean>>()
        val session = TouchKeyboardSession(10f, this) { code, pressed -> events += code to pressed }
        val keyboard = Any()
        val navigation = Any()
        session.attach(keyboard, listOf(control))
        session.attach(navigation, listOf(left))
        fun tapControl() {
            session.update(keyboard, listOf(KeyContact(0, 50f, 50f, true)), 0)
            session.update(keyboard, listOf(KeyContact(0, 50f, 50f, false)), 1)
        }
        tapControl()
        session.update(navigation, listOf(KeyContact(1, 50f, 50f, true)), 10)
        tapControl()
        session.update(navigation, listOf(KeyContact(1, 20f, 20f, true)), 20)
        session.update(navigation, listOf(KeyContact(1, 20f, 20f, false)), 30)
        assertEquals(listOf(KeyboardLayouts.MOD_LCTRL to true, KeyboardLayouts.MOD_LCTRL to false,
            KeyboardLayouts.KEY_HOME to true, KeyboardLayouts.KEY_HOME to false), events)
        assertTrue(session.latchedModifiers.isEmpty())
    }

    @Test fun twoFingersOnTheSameModifierOnlyToggleItOnce() = runTest {
        val events = mutableListOf<Pair<Int, Boolean>>()
        val session = TouchKeyboardSession(10f, this) { code, pressed -> events += code to pressed }
        val owner = Any()
        session.attach(owner, listOf(control))
        session.update(owner, listOf(KeyContact(0, 30f, 50f, true), KeyContact(1, 70f, 50f, true)), 0)
        session.update(owner, listOf(KeyContact(0, 30f, 50f, false), KeyContact(1, 70f, 50f, true)), 1)
        session.update(owner, listOf(KeyContact(1, 70f, 50f, false)), 2)
        assertEquals(listOf(KeyboardLayouts.MOD_LCTRL to true), events)
        assertEquals(setOf(KeyboardLayouts.MOD_LCTRL), session.latchedModifiers)
        session.reset()
        assertEquals(KeyboardLayouts.MOD_LCTRL to false, events.last())
        assertTrue(session.latchedModifiers.isEmpty())
    }

    @Test fun fnCornerRemainsMomentaryAndDoesNotLatchWinWhenCtrlIsAlreadyLatched() = runTest {
        val events = mutableListOf<Pair<Int, Boolean>>()
        val session = TouchKeyboardSession(10f, this) { code, pressed -> events += code to pressed }
        val owner = Any()
        session.attach(owner, listOf(control,
            CornerGestureKey("win", KeyboardLayouts.MOD_LWIN, 100f, 0f, 100f, 100f,
                mapOf(KeyCorner.TOP_LEFT to CornerAction("Fn", KeyboardLayouts.KEY_FN))),
            CornerGestureKey("f1", KeyboardLayouts.KEY_F1, 200f, 0f, 100f, 100f)))
        session.updatePreviewKeys(owner, listOf(KeyPreviewInfo("f1", Rect(200f, 0f, 300f, 100f), "F1",
            background = Color.Red, foreground = Color.Black, fnLabel = "Mute")))
        session.update(owner, listOf(KeyContact(0, 50f, 50f, true)), 0)
        session.update(owner, listOf(KeyContact(0, 50f, 50f, false)), 1)
        session.update(owner, listOf(KeyContact(0, 150f, 50f, true)), 2)
        session.update(owner, listOf(KeyContact(0, 120f, 20f, true)), 3)
        session.update(owner, listOf(KeyContact(0, 120f, 20f, true), KeyContact(1, 250f, 50f, true)), 4)
        assertEquals("Mute", session.keyPreview?.label)
        session.update(owner, listOf(KeyContact(0, 120f, 20f, true), KeyContact(1, 250f, 50f, false)), 5)
        session.update(owner, listOf(KeyContact(0, 120f, 20f, false)), 6)
        assertEquals("The released key must keep its Fn label after Fn is released", "Mute", session.keyPreview?.label)
        assertEquals(listOf(KeyboardLayouts.MOD_LCTRL to true, KeyboardLayouts.KEY_FN to true,
            KeyboardLayouts.KEY_F1 to true, KeyboardLayouts.KEY_F1 to false, KeyboardLayouts.KEY_FN to false), events)
        assertEquals(setOf(KeyboardLayouts.MOD_LCTRL), session.latchedModifiers)
        session.reset()
        assertTrue(session.latchedModifiers.isEmpty())
    }

    @Test fun characterCornersTemporarilySelectTheirLevelAndRestoreEveryLatchedModifier() = runTest {
        listOf(
            CornerAction("!", KeyboardLayouts.KEY_1, setOf(KeyboardLayouts.MOD_LSHIFT)),
            CornerAction("@", KeyboardLayouts.KEY_Q, setOf(KeyboardLayouts.MOD_RALT)),
            CornerAction("Æ", KeyboardLayouts.KEY_A, setOf(KeyboardLayouts.MOD_LSHIFT, KeyboardLayouts.MOD_RALT)),
        ).forEach { action ->
            val held = mutableSetOf<Int>()
            val characterChords = mutableListOf<Set<Int>>()
            val session = TouchKeyboardSession(10f, this) { code, pressed ->
                if (pressed && code == action.keyCode) characterChords += held.toSet()
                if (pressed) held += code else held -= code
            }
            val owner = Any()
            session.attach(owner, listOf(control,
                CornerGestureKey("shift", KeyboardLayouts.MOD_RSHIFT, 100f, 0f, 100f, 100f),
                CornerGestureKey("altgr", KeyboardLayouts.MOD_RALT, 200f, 0f, 100f, 100f),
                CornerGestureKey("character", action.keyCode, 300f, 0f, 100f, 100f,
                    mapOf(KeyCorner.TOP_RIGHT to action))))
            listOf(50f, 150f, 250f).forEach { x ->
                session.update(owner, listOf(KeyContact(0, x, 50f, true)), 0)
                session.update(owner, listOf(KeyContact(0, x, 50f, false)), 1)
            }
            val original = setOf(KeyboardLayouts.MOD_LCTRL, KeyboardLayouts.MOD_RSHIFT, KeyboardLayouts.MOD_RALT)
            assertEquals(original, session.latchedModifiers)
            session.update(owner, listOf(KeyContact(0, 350f, 50f, true)), 2)
            session.update(owner, listOf(KeyContact(0, 380f, 20f, true)), 3)
            session.update(owner, listOf(KeyContact(0, 380f, 20f, false)), 4)
            assertEquals(listOf(setOf(KeyboardLayouts.MOD_LCTRL) + action.characterModifiers!!), characterChords)
            assertEquals(original, held)
            assertEquals(original, session.latchedModifiers)
            session.reset()
            assertTrue(held.isEmpty())
        }
    }
}
