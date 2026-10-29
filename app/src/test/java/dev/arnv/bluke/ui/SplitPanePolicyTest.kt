package dev.arnv.bluke.ui

import org.junit.Assert.*
import org.junit.Test

class SplitPanePolicyTest {
    @Test fun largeScreensAllowTwoFivePointStepsInEachDirection() {
        assertEquals(0.72f, SplitPanePolicy.keyboardFraction(0, 1_000f), 0.001f)
        assertEquals(0.62f, SplitPanePolicy.keyboardFraction(-2, 1_000f), 0.001f)
        assertEquals(0.82f, SplitPanePolicy.keyboardFraction(2, 1_000f), 0.001f)
        assertNull(SplitPanePolicy.nextStep(-2, -1, 1_000f))
        assertNull(SplitPanePolicy.nextStep(2, 1, 1_000f))
    }

    @Test fun smallScreensKeepTheTouchpadUsableAndSkipDuplicateClampedSteps() {
        val width = 600f
        val maximum = SplitPanePolicy.keyboardFraction(2, width)
        assertTrue(width * (1f - maximum) >= 159.9f)
        assertEquals(maximum, SplitPanePolicy.keyboardFraction(1, width), 0.001f)
        assertNull(SplitPanePolicy.nextStep(1, 1, width))
        assertEquals(0, SplitPanePolicy.nextStep(2, -1, width))
        (-10..10).forEach { assertTrue(SplitPanePolicy.keyboardFraction(it, width) in 0.62f..0.82f) }
    }
}
