package dev.arnv.bluke.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchpadGesturePolicyTest {
    @Test fun edgeScrollStartsOnlyInTheRightRailAboveTheMouseButtons() {
        assertTrue(TouchpadGesturePolicy.startsEdgeScroll(390f, 100f, 400f, 300f, 24f))
        assertTrue(TouchpadGesturePolicy.startsEdgeScroll(376f, 0f, 400f, 300f, 24f))
        assertFalse(TouchpadGesturePolicy.startsEdgeScroll(375f, 100f, 400f, 300f, 24f))
        assertFalse(TouchpadGesturePolicy.startsEdgeScroll(390f, 246f, 400f, 300f, 24f))
        assertFalse(TouchpadGesturePolicy.startsEdgeScroll(400f, 100f, 400f, 300f, 24f))
        assertFalse(TouchpadGesturePolicy.startsEdgeScroll(390f, -1f, 400f, 300f, 24f))
    }

    @Test
    fun tapAllowsNormalFingerMotionButRejectsLongPressOrDrag() {
        assertTrue(TouchpadGesturePolicy.isTap(120, distanceSquaredPx = 100f, tapSlopPx = 12f))
        assertFalse(TouchpadGesturePolicy.isTap(250, distanceSquaredPx = 0f, tapSlopPx = 12f))
        assertFalse(TouchpadGesturePolicy.isTap(120, distanceSquaredPx = 145f, tapSlopPx = 12f))
    }

    @Test
    fun secondTapTimeEligibilityIsBounded() {
        assertTrue(TouchpadGesturePolicy.isSecondTap(300))
        assertFalse(TouchpadGesturePolicy.isSecondTap(301))
        assertFalse(TouchpadGesturePolicy.isSecondTap(-1))
    }

    @Test
    fun timingHelperAcceptsAQuickSecondTap() {
        assertTrue(
            TouchpadGesturePolicy.isSecondTap(elapsedSinceReleaseMillis = 120)
        )
    }
}
