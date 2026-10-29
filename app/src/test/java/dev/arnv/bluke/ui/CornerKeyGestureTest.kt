package dev.arnv.bluke.ui

import org.junit.Assert.*
import org.junit.Test

class CornerKeyGestureTest {
    private class Fixture(corners: Map<KeyCorner, CornerAction> = mapOf(
        KeyCorner.TOP_LEFT to CornerAction("Home", KeyboardLayouts.KEY_HOME),
        KeyCorner.TOP_RIGHT to CornerAction("A", KeyboardLayouts.KEY_A),
        KeyCorner.BOTTOM_LEFT to CornerAction("PgUp", KeyboardLayouts.KEY_PAGEUP),
        KeyCorner.BOTTOM_RIGHT to CornerAction("Del", KeyboardLayouts.KEY_DELETE),
    )) {
        val events = mutableListOf<Pair<Int, Boolean>>()
        private val contacts = linkedMapOf<Long, KeyContact>()
        val gesture = CornerKeyGesture(
            keys = listOf(
                CornerGestureKey("tab", KeyboardLayouts.KEY_TAB, 0f, 0f, 100f, 100f, corners),
                CornerGestureKey("b", KeyboardLayouts.KEY_B, 100f, 0f, 100f, 100f),
                CornerGestureKey("ctrl", KeyboardLayouts.MOD_LCTRL, 200f, 0f, 100f, 100f),
                CornerGestureKey("alt", KeyboardLayouts.MOD_LALT, 300f, 0f, 100f, 100f),
                CornerGestureKey("win", KeyboardLayouts.MOD_LWIN, 400f, 0f, 100f, 100f),
                CornerGestureKey("shift", KeyboardLayouts.MOD_LSHIFT, 500f, 0f, 100f, 100f),
                CornerGestureKey("f1", KeyboardLayouts.KEY_F1, 600f, 0f, 100f, 100f),
            ), swipeSlop = 10f, sendKey = { code, pressed -> events += code to pressed },
        )
        fun down(id: Long, x: Float, y: Float = 50f) {
            contacts[id] = KeyContact(id, x, y, true)
            gesture.process(contacts.values.toList())
        }
        fun move(id: Long, x: Float, y: Float) {
            contacts[id] = contacts.getValue(id).copy(x = x, y = y)
            gesture.process(contacts.values.toList())
        }
        fun up(id: Long) {
            contacts[id] = contacts.getValue(id).copy(pressed = false)
            gesture.process(contacts.values.toList())
            contacts.remove(id)
        }
    }

    @Test fun touchingACornerDirectlyStillTapsTheMainKey() {
        val f = Fixture()
        f.down(1, 1f, 1f)
        assertTrue(f.events.isEmpty())
        f.up(1)
        assertEquals(listOf(KeyboardLayouts.KEY_TAB to true, KeyboardLayouts.KEY_TAB to false), f.events)
    }

    @Test fun draggingFromAnywhereSelectsTheDirectionWithoutSendingTheMainKey() {
        val f = Fixture()
        f.down(1, 5f, 80f)
        f.move(1, 30f, 50f) // Northeast even though the finger is still in the left half of the key.
        assertEquals(KeyCorner.TOP_RIGHT, f.gesture.feedback["tab"])
        assertTrue(f.events.isEmpty())
        f.up(1)
        assertEquals(listOf(KeyboardLayouts.KEY_A to true, KeyboardLayouts.KEY_A to false), f.events)
    }

    @Test fun changingDirectionBeforeReleaseSendsOnlyTheFinalCorner() {
        val f = Fixture()
        f.down(1, 50f)
        f.move(1, 25f, 25f)
        f.move(1, 75f, 25f)
        f.up(1)
        assertEquals(listOf(KeyboardLayouts.KEY_A to true, KeyboardLayouts.KEY_A to false), f.events)
    }

    @Test fun aSmallFingerMovementDoesNotSelectACorner() {
        val f = Fixture()
        f.down(1, 50f)
        f.move(1, 55f, 55f)
        f.up(1)
        assertEquals(KeyboardLayouts.KEY_TAB to true, f.events.first())
    }

    @Test fun movingIntoANeighbourDoesNotTypeTheNeighbour() {
        val f = Fixture()
        f.down(1, 50f)
        f.move(1, 150f, 5f)
        f.up(1)
        assertEquals(listOf(KeyboardLayouts.KEY_A to true, KeyboardLayouts.KEY_A to false), f.events)
    }

    @Test fun ctrlAltAndWinWorkWithAnArbitraryLetterInACorner() {
        listOf(KeyboardLayouts.MOD_LCTRL to 250f, KeyboardLayouts.MOD_LALT to 350f, KeyboardLayouts.MOD_LWIN to 450f)
            .forEach { (modifier, x) ->
                val f = Fixture()
                f.down(0, x)
                f.down(1, 50f)
                f.move(1, 80f, 20f)
                f.up(1)
                f.up(0)
                assertEquals(listOf(modifier to true, KeyboardLayouts.KEY_A to true,
                    KeyboardLayouts.KEY_A to false, modifier to false), f.events)
            }
    }

    @Test fun releasingCtrlBeforeTheGestureFinishesStillSendsCtrlA() {
        val f = Fixture()
        f.down(0, 250f)
        f.down(1, 50f)
        f.up(0)
        assertEquals(listOf(KeyboardLayouts.MOD_LCTRL to true), f.events)
        f.move(1, 80f, 20f)
        f.up(1)
        assertEquals(listOf(
            KeyboardLayouts.MOD_LCTRL to true, KeyboardLayouts.KEY_A to true,
            KeyboardLayouts.KEY_A to false, KeyboardLayouts.MOD_LCTRL to false,
        ), f.events)
    }

    @Test fun releasingWinEarlyDoesNotSendAStandaloneWinTapBeforeTheCorner() {
        val f = Fixture()
        f.down(0, 450f)
        f.down(1, 50f)
        f.up(0)
        assertEquals(listOf(KeyboardLayouts.MOD_LWIN to true), f.events)
        f.move(1, 80f, 20f)
        f.up(1)
        assertEquals(listOf(KeyboardLayouts.MOD_LWIN to true, KeyboardLayouts.KEY_A to true,
            KeyboardLayouts.KEY_A to false, KeyboardLayouts.MOD_LWIN to false), f.events)
    }

    @Test fun pressingModifiersAfterStartingTheGestureAlsoWorks() {
        val f = Fixture()
        f.down(1, 50f)
        f.down(0, 250f)
        f.down(2, 550f)
        f.move(1, 25f, 25f)
        f.up(1)
        f.up(0)
        f.up(2)
        assertEquals(listOf(KeyboardLayouts.MOD_LCTRL to true, KeyboardLayouts.MOD_LSHIFT to true,
            KeyboardLayouts.KEY_HOME to true, KeyboardLayouts.KEY_HOME to false,
            KeyboardLayouts.MOD_LCTRL to false, KeyboardLayouts.MOD_LSHIFT to false), f.events)
    }

    @Test fun releasingModifierAndCornerTogetherKeepsTheCombinationInEitherEventOrder() {
        listOf(false, true).forEach { reverse ->
            val f = Fixture()
            f.down(0, 250f)
            f.down(1, 50f)
            f.move(1, 80f, 20f)
            val contacts = listOf(KeyContact(0, 250f, 50f, false), KeyContact(1, 80f, 20f, false))
            f.gesture.process(if (reverse) contacts.reversed() else contacts)
            assertEquals(listOf(KeyboardLayouts.MOD_LCTRL to true, KeyboardLayouts.KEY_A to true,
                KeyboardLayouts.KEY_A to false, KeyboardLayouts.MOD_LCTRL to false), f.events)
        }
    }

    @Test fun fnCornerIsHeldImmediatelySoAnotherFingerCanPressAFunctionKey() {
        val f = Fixture(mapOf(KeyCorner.TOP_LEFT to CornerAction("Fn", KeyboardLayouts.KEY_FN)))
        f.down(0, 50f)
        f.move(0, 20f, 20f)
        assertEquals(listOf(KeyboardLayouts.KEY_FN to true), f.events)
        f.down(1, 650f)
        f.up(1)
        f.up(0)
        assertEquals(listOf(KeyboardLayouts.KEY_FN to true, KeyboardLayouts.KEY_F1 to true,
            KeyboardLayouts.KEY_F1 to false, KeyboardLayouts.KEY_FN to false), f.events)
    }

    @Test fun cancellingDropsAPendingGestureAndReleasesHeldModifiers() {
        val f = Fixture()
        f.down(0, 250f)
        f.down(1, 50f)
        f.move(1, 80f, 20f)
        f.gesture.cancel()
        assertEquals(listOf(KeyboardLayouts.MOD_LCTRL to true, KeyboardLayouts.MOD_LCTRL to false), f.events)
        assertTrue(f.gesture.feedback.isEmpty())
    }

    @Test fun twoFingersOnTheSameKeyDoNotReleaseEachOther() {
        val f = Fixture()
        f.down(0, 150f)
        f.down(1, 160f)
        f.up(0)
        assertEquals(listOf(KeyboardLayouts.KEY_B to true), f.events)
        f.up(1)
        assertEquals(listOf(KeyboardLayouts.KEY_B to true, KeyboardLayouts.KEY_B to false), f.events)
    }

    private fun arrowGesture(events: MutableList<Pair<Int, Boolean>>) = CornerKeyGesture(listOf(
        CornerGestureKey("left", KeyboardLayouts.KEY_LEFT, 0f, 0f, 100f, 100f,
            mapOf(KeyCorner.TOP_LEFT to CornerAction("Home", KeyboardLayouts.KEY_HOME)), holdPrimaryAfterDelay = true),
    ), 10f) { code, pressed -> events += code to pressed }

    @Test fun aQuickArrowTapEmitsOnlyTheArrowOnRelease() {
        val events = mutableListOf<Pair<Int, Boolean>>()
        val gesture = arrowGesture(events)
        gesture.process(listOf(KeyContact(0, 50f, 50f, true)), 0)
        assertTrue(events.isEmpty())
        gesture.process(listOf(KeyContact(0, 50f, 50f, false)), 100)
        assertEquals(listOf(KeyboardLayouts.KEY_LEFT to true, KeyboardLayouts.KEY_LEFT to false), events)
    }

    @Test fun holdingAnArrowSendsADownReportAndLocksTheGestureUntilRelease() {
        val events = mutableListOf<Pair<Int, Boolean>>()
        val gesture = arrowGesture(events)
        gesture.process(listOf(KeyContact(0, 50f, 50f, true)), 0)
        gesture.advanceTime(CornerKeyGesture.HOLD_DELAY_MILLIS - 1)
        assertTrue(events.isEmpty())
        gesture.advanceTime(CornerKeyGesture.HOLD_DELAY_MILLIS)
        assertEquals(listOf(KeyboardLayouts.KEY_LEFT to true), events)
        gesture.process(listOf(KeyContact(0, 20f, 20f, true)), 500)
        gesture.process(listOf(KeyContact(0, 20f, 20f, false)), 600)
        assertEquals(listOf(KeyboardLayouts.KEY_LEFT to true, KeyboardLayouts.KEY_LEFT to false), events)
    }

    @Test fun selectingAnArrowCornerNeverStartsThePrimaryHoldEvenAfterWaiting() {
        val events = mutableListOf<Pair<Int, Boolean>>()
        val gesture = arrowGesture(events)
        gesture.process(listOf(KeyContact(0, 50f, 50f, true)), 0)
        gesture.process(listOf(KeyContact(0, 20f, 20f, true)), 100)
        gesture.advanceTime(1_000)
        assertTrue(events.isEmpty())
        gesture.process(listOf(KeyContact(0, 20f, 20f, false)), 1_100)
        assertEquals(listOf(KeyboardLayouts.KEY_HOME to true, KeyboardLayouts.KEY_HOME to false), events)
    }

    private fun winGesture(events: MutableList<Pair<Int, Boolean>>) = CornerKeyGesture(listOf(
        CornerGestureKey("win", KeyboardLayouts.MOD_LWIN, 0f, 0f, 100f, 100f,
            mapOf(KeyCorner.TOP_LEFT to CornerAction("Fn", KeyboardLayouts.KEY_FN))),
        CornerGestureKey("a", KeyboardLayouts.KEY_A, 100f, 0f, 100f, 100f),
        CornerGestureKey("ctrl", KeyboardLayouts.MOD_LCTRL, 200f, 0f, 100f, 100f),
    ), 10f) { code, pressed -> events += code to pressed }

    @Test fun tappingWinStillSendsAnOrdinaryWinTap() {
        val events = mutableListOf<Pair<Int, Boolean>>()
        val gesture = winGesture(events)
        gesture.process(listOf(KeyContact(0, 50f, 50f, true)), 0)
        assertTrue(events.isEmpty())
        gesture.process(listOf(KeyContact(0, 50f, 50f, false)), 100)
        assertEquals(listOf(KeyboardLayouts.MOD_LWIN to true, KeyboardLayouts.MOD_LWIN to false), events)
    }

    @Test fun winACommitsTheCentreModifierBeforeTheLetterAndCannotSwitchToFnMidChord() {
        val events = mutableListOf<Pair<Int, Boolean>>()
        val gesture = winGesture(events)
        gesture.process(listOf(KeyContact(0, 50f, 50f, true)), 0)
        gesture.process(listOf(KeyContact(0, 50f, 50f, true), KeyContact(1, 150f, 50f, true)), 100)
        gesture.process(listOf(KeyContact(0, 20f, 20f, true), KeyContact(1, 150f, 50f, true)), 150)
        gesture.process(emptyList(), 200)
        assertEquals(listOf(KeyboardLayouts.MOD_LWIN to true, KeyboardLayouts.KEY_A to true,
            KeyboardLayouts.KEY_A to false, KeyboardLayouts.MOD_LWIN to false), events)
    }

    @Test fun holdingCtrlFirstDoesNotPreventSelectingFnOnWin() {
        val events = mutableListOf<Pair<Int, Boolean>>()
        val gesture = winGesture(events)
        gesture.process(listOf(KeyContact(0, 250f, 50f, true)), 0)
        gesture.process(listOf(KeyContact(0, 250f, 50f, true), KeyContact(1, 50f, 50f, true)), 50)
        gesture.process(listOf(KeyContact(0, 250f, 50f, true), KeyContact(1, 20f, 20f, true)), 100)
        gesture.process(emptyList(), 150)
        assertEquals(listOf(KeyboardLayouts.MOD_LCTRL to true, KeyboardLayouts.KEY_FN to true,
            KeyboardLayouts.MOD_LCTRL to false, KeyboardLayouts.KEY_FN to false), events)
    }

    @Test fun aCommittedArrowReleasesCtrlNormallyWhileTheArrowStaysDown() {
        val events = mutableListOf<Pair<Int, Boolean>>()
        val gesture = CornerKeyGesture(listOf(
            CornerGestureKey("left", KeyboardLayouts.KEY_LEFT, 0f, 0f, 100f, 100f,
                mapOf(KeyCorner.TOP_LEFT to CornerAction("Home", KeyboardLayouts.KEY_HOME)), holdPrimaryAfterDelay = true),
            CornerGestureKey("ctrl", KeyboardLayouts.MOD_LCTRL, 100f, 0f, 100f, 100f),
        ), 10f) { code, pressed -> events += code to pressed }
        gesture.process(listOf(KeyContact(0, 150f, 50f, true)), 0)
        gesture.process(listOf(KeyContact(0, 150f, 50f, true), KeyContact(1, 50f, 50f, true)), 10)
        gesture.advanceTime(400)
        gesture.process(listOf(KeyContact(0, 150f, 50f, false), KeyContact(1, 50f, 50f, true)), 450)
        assertEquals(listOf(KeyboardLayouts.MOD_LCTRL to true, KeyboardLayouts.KEY_LEFT to true,
            KeyboardLayouts.MOD_LCTRL to false), events)
        gesture.process(emptyList(), 500)
        assertEquals(KeyboardLayouts.KEY_LEFT to false, events.last())
    }

    @Test fun fnCanBeSelectedAfterHoldingWinWithoutSendingAWinPress() {
        val events = mutableListOf<Pair<Int, Boolean>>()
        val gesture = winGesture(events)
        gesture.process(listOf(KeyContact(0, 50f, 50f, true)), 0)
        gesture.advanceTime(2_000)
        assertTrue(events.isEmpty())
        gesture.process(listOf(KeyContact(0, 20f, 20f, true)), 2_100)
        gesture.process(emptyList(), 2_200)
        assertEquals(listOf(KeyboardLayouts.KEY_FN to true, KeyboardLayouts.KEY_FN to false), events)
    }
}
