package dev.arnv.bluke.ui

import kotlin.math.abs

internal const val SPLIT_WIDTH_STEP_PREFERENCE = "split_width_step"

internal object SplitPanePolicy {
    const val MIN_STEP = -2
    const val MAX_STEP = 2
    const val CONTROL_WIDTH_DP = 18f
    const val DIVIDER_WIDTH_DP = CONTROL_WIDTH_DP + 4f

    fun keyboardFraction(step: Int, availableWidthDp: Float): Float {
        val width = availableWidthDp.coerceAtLeast(1f)
        val minimum = maxOf(0.62f, minOf(0.72f, 320f / width))
        val maximum = minOf(0.82f, maxOf(0.72f, 1f - 160f / width))
        return (0.72f + step.coerceIn(MIN_STEP, MAX_STEP) * 0.05f).coerceIn(minimum, maximum)
    }

    fun nextStep(current: Int, direction: Int, availableWidthDp: Float): Int? {
        val step = current.coerceIn(MIN_STEP, MAX_STEP)
        val next = step + direction
        val candidates = if (direction < 0) (next downTo MIN_STEP) else (next..MAX_STEP)
        return candidates.firstOrNull {
            it in MIN_STEP..MAX_STEP && abs(keyboardFraction(it, availableWidthDp) - keyboardFraction(step, availableWidthDp)) > 0.001f
        }
    }
}
