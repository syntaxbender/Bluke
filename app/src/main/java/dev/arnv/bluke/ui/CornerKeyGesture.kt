package dev.arnv.bluke.ui

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal data class CornerGestureKey(
    val id: String,
    val keyCode: Int,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val corners: Map<KeyCorner, CornerAction> = emptyMap(),
    val holdPrimaryAfterDelay: Boolean = false,
)

internal data class KeyContact(val id: Long, val x: Float, val y: Float, val pressed: Boolean)

/** One owner per finger; pending gestures never leak their centre key into the host. */
internal class CornerKeyGesture(
    private var keys: List<CornerGestureKey>,
    private val swipeSlop: Float,
    private val stickyModifiers: Boolean = false,
    private val sendKey: (Int, Boolean) -> Unit,
) {
    private data class Touch(
        val key: CornerGestureKey,
        val startX: Float,
        val startY: Float,
        val startedAt: Long,
        var corner: KeyCorner? = null,
        var committedCode: Int? = null,
        var heldCode: Int? = null,
        val modifiers: MutableSet<Int> = mutableSetOf(),
    )

    private val touches = linkedMapOf<Long, Touch>()
    private var sent = emptySet<Int>()
    private var lastContacts = emptyList<KeyContact>()
    private var lastTime = 0L
    var latchedModifiers: Set<Int> = emptySet()
        private set
    val nextHoldDeadline: Long?
        get() = touches.values.filter { it.key.holdPrimaryAfterDelay && it.corner == null && it.committedCode == null }
            .minOfOrNull { it.startedAt + HOLD_DELAY_MILLIS }
    val feedback: Map<String, KeyCorner?>
        get() = touches.values.associate { it.key.id to it.corner }
    val pressedKeyCodes: Set<Int>
        get() = sent

    fun process(contacts: List<KeyContact>, timeMillis: Long = lastTime) {
        lastTime = maxOf(lastTime, timeMillis)
        lastContacts = contacts.filter { it.pressed }
        val newIds = contacts.filter { it.pressed && it.id !in touches }.mapTo(mutableSetOf()) { it.id }
        contacts.filter { it.pressed && it.id !in touches }.forEach { contact ->
            keys.firstOrNull { key ->
                contact.x >= key.left && contact.x < key.left + key.width &&
                    contact.y >= key.top && contact.y < key.top + key.height
            }?.let { touches[contact.id] = Touch(it, contact.x, contact.y, lastTime) }
        }
        contacts.forEach { contact ->
            touches[contact.id]?.let { touch ->
                val dx = contact.x - touch.startX
                val dy = contact.y - touch.startY
                val threshold = min(swipeSlop, min(touch.key.width, touch.key.height) * 0.2f)
                val corner = if (max(abs(dx), abs(dy)) >= threshold &&
                    min(abs(dx), abs(dy)) >= max(abs(dx), abs(dy)) * 0.3f
                ) {
                    when {
                        dx < 0 && dy < 0 -> KeyCorner.TOP_LEFT
                        dx >= 0 && dy < 0 -> KeyCorner.TOP_RIGHT
                        dx < 0 -> KeyCorner.BOTTOM_LEFT
                        else -> KeyCorner.BOTTOM_RIGHT
                    }
                } else null
                if (touch.committedCode == null) touch.corner = corner?.takeIf { it in touch.key.corners }
            }
        }

        val pressedIds = contacts.filter { it.pressed }.mapTo(mutableSetOf()) { it.id }
        val released = touches.filterKeys { it !in pressedIds }.values.toList()
        val stillTouchedCodes = touches.filterKeys { it in pressedIds }.values.mapTo(mutableSetOf(), ::actionFor)
        val toggled = if (stickyModifiers) released.map(::actionFor)
            .filter { it in 0xE0..0xE7 && it !in stillTouchedCodes }.toSet() else emptySet()
        val turnedOff = toggled intersect latchedModifiers
        latchedModifiers = (latchedModifiers - toggled) + (toggled - latchedModifiers)
        // Explicitly switching a modifier off also removes it from pending corner gestures.
        touches.values.forEach { it.modifiers.removeAll(turnedOff) }
        val previousModifiers = (sent - turnedOff).filterTo(mutableSetOf(), ::isModifier)
        touches.filterKeys { it in pressedIds }.forEach { (id, touch) ->
            val action = touch.corner?.let { touch.key.corners.getValue(it).keyCode } ?: touch.key.keyCode
            val chordStarted = if (touch.corner != null) pressedIds.size > 1 else newIds.any { other ->
                other != id && (id !in newIds || !isModifier(touches[other]?.key?.keyCode ?: 0))
            }
            if (touch.committedCode == null &&
                ((isModifier(action) && chordStarted) ||
                    (touch.corner == null && touch.key.holdPrimaryAfterDelay && lastTime - touch.startedAt >= HOLD_DELAY_MILLIS))
            ) touch.committedCode = action
        }
        val newlyHeld = mutableListOf<Touch>()
        val active = touches.filterKeys { it in pressedIds }.values.mapNotNullTo(mutableSetOf()) { touch ->
            val held = touch.committedCode ?: if (touch.key.corners.isEmpty()) touch.key.keyCode
                else touch.corner?.let { touch.key.corners.getValue(it).keyCode }?.takeIf(::isModifier)
            if (touch.heldCode == null && held != null && !isModifier(held) && touch.key.corners.isNotEmpty()) newlyHeld += touch
            touch.heldCode = held
            held
        }
        val modifierContext = previousModifiers + active.filter(::isModifier) + latchedModifiers
        touches.values.forEach { it.modifiers += modifierContext }
        // Keep an in-progress chord held: releasing Win/Alt early must not open a host menu
        // before its pending corner key is sent.
        val retainedModifiers = touches.filterKeys { it in pressedIds }.values
            .filter { it.key.corners.isNotEmpty() && it.heldCode == null &&
                !isModifier(it.corner?.let { corner -> it.key.corners.getValue(corner).keyCode } ?: it.key.keyCode) }
            .flatMap { it.modifiers }
        val heldActive = active + retainedModifiers + latchedModifiers
        if (newlyHeld.isNotEmpty()) reconcile(heldActive + newlyHeld.flatMap { it.modifiers })

        released.filter { it.key.corners.isNotEmpty() }.forEach { touch ->
            val action = actionFor(touch)
            // A centre hold or a modifier corner was already sent; pending taps/swipes need one pulse.
            if (touch.heldCode == null && !(stickyModifiers && action in 0xE0..0xE7)) {
                val inherited = heldActive + touch.modifiers
                val characterModifiers = touch.corner?.let { touch.key.corners.getValue(it).characterModifiers }
                val withModifiers = if (characterModifiers == null) inherited else
                    (inherited - CHARACTER_LEVEL_MODIFIERS) + characterModifiers
                reconcile(withModifiers)
                reconcile(withModifiers + action)
                reconcile(withModifiers)
            }
        }
        touches.keys.retainAll(pressedIds)
        reconcile(heldActive)
    }

    fun cancel() {
        touches.clear()
        lastContacts = emptyList()
        latchedModifiers = emptySet()
        reconcile(emptySet())
    }

    fun replaceKeys(updatedKeys: List<CornerGestureKey>) { keys = updatedKeys }

    fun cancelPointers(ids: Set<Long>, remaining: List<KeyContact>, timeMillis: Long) {
        touches.keys.removeAll(ids)
        process(remaining, timeMillis)
    }

    fun advanceTime(timeMillis: Long) = process(lastContacts, timeMillis)

    fun activatePendingModifiers() {
        touches.values.forEach { touch ->
            if (touch.corner == null && isModifier(touch.key.keyCode)) touch.committedCode = touch.key.keyCode
        }
        process(lastContacts, lastTime)
    }

    private fun reconcile(wanted: Set<Int>) {
        // Release keys before modifiers; press modifiers before keys, also for multiple fingers in one event.
        (sent - wanted).sortedBy { if (isModifier(it)) 1 else 0 }.forEach { sendKey(it, false) }
        (wanted - sent).sortedBy { if (isModifier(it)) 0 else 1 }.forEach { sendKey(it, true) }
        sent = wanted
    }

    private fun isModifier(code: Int): Boolean = code in 0xE0..0xE7 || code == KeyboardLayouts.KEY_FN

    private fun actionFor(touch: Touch): Int = touch.committedCode
        ?: touch.corner?.let { touch.key.corners.getValue(it).keyCode } ?: touch.key.keyCode

    companion object {
        const val HOLD_DELAY_MILLIS = 350L
        private val CHARACTER_LEVEL_MODIFIERS = setOf(
            KeyboardLayouts.MOD_LSHIFT, KeyboardLayouts.MOD_RSHIFT, KeyboardLayouts.MOD_RALT,
        )
    }
}
