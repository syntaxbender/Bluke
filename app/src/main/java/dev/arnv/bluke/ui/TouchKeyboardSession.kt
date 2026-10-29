package dev.arnv.bluke.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val KEY_RELEASE_FEEDBACK_MILLIS = 160L

/** Shares finger/modifier ownership between the keyboard and the separate navigation panel. */
class TouchKeyboardSession internal constructor(
    swipeSlop: Float,
    private val scope: CoroutineScope,
    sendKey: (Int, Boolean) -> Unit,
) {
    private data class Region(val offset: Float, val keys: List<CornerGestureKey>, var contacts: List<KeyContact> = emptyList())
    private val regions = linkedMapOf<Any, Region>()
    private val gesture = CornerKeyGesture(emptyList(), swipeSlop, stickyModifiers = true, sendKey = sendKey)
    private var now = 0L
    private var holdJob: Job? = null
    private var scheduledDeadline: Long? = null
    private val feedbackJobs = mutableMapOf<String, Job>()
    var visualPressedKeys by mutableStateOf<Set<String>>(emptySet())
        private set
    var feedback by mutableStateOf<Map<String, KeyCorner?>>(emptyMap())
        private set
    var latchedModifiers by mutableStateOf<Set<Int>>(emptySet())
        private set

    internal fun attach(owner: Any, keys: List<CornerGestureKey>) {
        val index = generateSequence(0) { it + 1 }.first { candidate -> regions.values.none { it.offset == candidate * REGION_STRIDE } }
        val offset = index * REGION_STRIDE
        regions[owner] = Region(offset, keys.map { it.copy(left = it.left + offset) })
        gesture.replaceKeys(regions.values.flatMap { it.keys })
    }

    internal fun update(owner: Any, contacts: List<KeyContact>, timeMillis: Long) {
        val region = regions[owner] ?: return
        now = maxOf(now, timeMillis)
        region.contacts = contacts.map { it.copy(x = it.x + region.offset) }
        gesture.process(allContacts(), now)
        region.contacts = region.contacts.filter { it.pressed }
        refresh()
    }

    internal fun detach(owner: Any) {
        if (regions.remove(owner) == null) return
        gesture.replaceKeys(regions.values.flatMap { it.keys })
        reset()
    }

    internal fun reset() {
        regions.values.forEach { it.contacts = emptyList() }
        gesture.cancel()
        refresh()
    }

    internal fun activatePendingModifiers() {
        gesture.activatePendingModifiers()
        refresh()
    }

    private fun allContacts() = regions.values.flatMap { it.contacts }

    private fun refresh() {
        latchedModifiers = gesture.latchedModifiers
        val live = gesture.feedback
        live.keys.forEach { feedbackJobs.remove(it)?.cancel() }
        visualPressedKeys = visualPressedKeys + live.keys
        (feedback.keys - live.keys).forEach { id ->
            feedbackJobs.remove(id)?.cancel()
            feedbackJobs[id] = scope.launch {
                delay(KEY_RELEASE_FEEDBACK_MILLIS)
                if (id !in feedback) visualPressedKeys = visualPressedKeys - id
                feedbackJobs.remove(id)
            }
        }
        feedback = live
        if (regions.isEmpty()) {
            feedbackJobs.values.forEach { it.cancel() }
            feedbackJobs.clear()
            visualPressedKeys = emptySet()
        }
        val deadline = gesture.nextHoldDeadline
        if (deadline == scheduledDeadline) return
        holdJob?.cancel()
        scheduledDeadline = deadline
        holdJob = deadline?.let {
            scope.launch {
                delay((it - now).coerceAtLeast(0L))
                now = maxOf(now, it)
                scheduledDeadline = null
                gesture.advanceTime(now)
                refresh()
            }
        }
    }

    private companion object { const val REGION_STRIDE = 65_536f }
}
