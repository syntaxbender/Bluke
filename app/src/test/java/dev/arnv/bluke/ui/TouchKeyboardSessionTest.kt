package dev.arnv.bluke.ui

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
        session.update(owner, listOf(KeyContact(0, 50f, 50f, true)), 0)
        session.update(owner, listOf(KeyContact(0, 50f, 50f, false)), 1)
        session.update(owner, listOf(KeyContact(0, 150f, 50f, true)), 2)
        session.update(owner, listOf(KeyContact(0, 120f, 20f, true)), 3)
        session.update(owner, listOf(KeyContact(0, 120f, 20f, true), KeyContact(1, 250f, 50f, true)), 4)
        session.update(owner, listOf(KeyContact(0, 120f, 20f, true), KeyContact(1, 250f, 50f, false)), 5)
        session.update(owner, listOf(KeyContact(0, 120f, 20f, false)), 6)
        assertEquals(listOf(KeyboardLayouts.MOD_LCTRL to true, KeyboardLayouts.KEY_FN to true,
            KeyboardLayouts.KEY_F1 to true, KeyboardLayouts.KEY_F1 to false, KeyboardLayouts.KEY_FN to false), events)
        assertEquals(setOf(KeyboardLayouts.MOD_LCTRL), session.latchedModifiers)
        session.reset()
        assertTrue(session.latchedModifiers.isEmpty())
    }
}
