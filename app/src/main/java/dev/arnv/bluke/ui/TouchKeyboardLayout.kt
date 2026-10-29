package dev.arnv.bluke.ui

enum class KeyCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

data class CornerAction(val legend: String, val keyCode: Int)

data class TouchKeyboardKey(
    val layout: KeyLayoutInfo,
    val corners: Map<KeyCorner, CornerAction> = emptyMap(),
    val holdPrimaryAfterDelay: Boolean = false,
)

/** Compact modifier keys let the three letter rows use the same, wider column grid. */
fun touchKeyboardLayout(
    geometry: KeyboardGeometry,
    characterLayout: KeyboardCharacterLayout,
): List<TouchKeyboardKey> {
    val source = KeyboardLayouts.getLayout(geometry, characterLayout)
    val sourceKeys = source.flatten()
    val hidden = setOf(
        KeyboardLayouts.KEY_FN, KeyboardLayouts.KEY_HOME, KeyboardLayouts.KEY_END,
        KeyboardLayouts.KEY_PAGEUP, KeyboardLayouts.KEY_PAGEDOWN,
        KeyboardLayouts.KEY_INSERT, KeyboardLayouts.KEY_DELETE,
        KeyboardLayouts.KEY_UP, KeyboardLayouts.KEY_DOWN, KeyboardLayouts.KEY_LEFT, KeyboardLayouts.KEY_RIGHT,
    )
    fun key(code: Int, legend: String, width: Float = 1f, category: KeyColorCategory = KeyColorCategory.MOD): KeyLayoutInfo =
        (sourceKeys.firstOrNull { it.physicalKeyCode == code }
            ?: KeyLayoutInfo(legend = legend, keyCode = code, physicalKeyCode = code, category = category))
            .copy(legend = legend, styleId = "touch:$code", widthRatio = width, keyCode = code, physicalKeyCode = code)

    val rows = source.map { row -> row.filter { it.physicalKeyCode !in hidden } }
        .filter { it.isNotEmpty() }.toMutableList()
    // Keep Delete as a dedicated control on the top row, including on compact geometries.
    rows[0] = rows[0] + key(KeyboardLayouts.KEY_DELETE, "Delete")
    val homeRow = rows.indexOfFirst { row -> row.any { it.physicalKeyCode == KeyboardLayouts.KEY_A } }
    if (homeRow >= 0 && rows[homeRow].none { it.physicalKeyCode == KeyboardLayouts.KEY_CAPSLOCK }) {
        rows[homeRow] = listOf(key(KeyboardLayouts.KEY_CAPSLOCK, "Caps Lock", 1.75f)) +
            rows[homeRow].filter { it.keyCode !in 0xE0..0xE7 }
    }
    val shiftRow = rows.lastIndex - 1
    val shifts = rows[shiftRow].count { it.keyCode in setOf(KeyboardLayouts.MOD_LSHIFT, KeyboardLayouts.MOD_RSHIFT) }
    var shiftIndex = 0
    rows[shiftRow] = rows[shiftRow].map { current ->
        if (current.keyCode in setOf(KeyboardLayouts.MOD_LSHIFT, KeyboardLayouts.MOD_RSHIFT)) {
            val code = if (shiftIndex++ == 0 || shifts == 1) KeyboardLayouts.MOD_LSHIFT else KeyboardLayouts.MOD_RSHIFT
            current.copy(keyCode = code, physicalKeyCode = code, widthRatio = 1f)
        } else current
    }
    fun moveToPunctuationRow(code: Int) {
        val current = rows.filterIndexed { index, _ -> index != shiftRow }.flatten()
            .firstOrNull { it.physicalKeyCode == code } ?: return
        rows.indices.filter { it != shiftRow }.forEach { index ->
            rows[index] = rows[index].filter { it.physicalKeyCode != code }
        }
        val punctuation = rows[shiftRow].toMutableList()
        val rightShift = punctuation.indexOfLast { it.physicalKeyCode == KeyboardLayouts.MOD_RSHIFT }
        punctuation.add(if (rightShift >= 0) rightShift else punctuation.size, current)
        rows[shiftRow] = punctuation
    }
    moveToPunctuationRow(KeyboardLayouts.KEY_BACKSLASH)
    if (rows[0].none { it.physicalKeyCode in KeyboardLayouts.KEY_F1..KeyboardLayouts.KEY_F12 } && rows[0].size > 14) {
        moveToPunctuationRow(KeyboardLayouts.KEY_GRAVE)
    }
    rows[rows.lastIndex] = listOf(
        key(KeyboardLayouts.MOD_LCTRL, "Ctrl", 1.5f),
        key(KeyboardLayouts.MOD_LWIN, "Win", 1.5f),
        key(KeyboardLayouts.MOD_LALT, "Alt", 1.5f),
        key(KeyboardLayouts.KEY_SPACE, "", 7.5f, KeyColorCategory.ACCENT),
        key(KeyboardLayouts.MOD_RALT, if (characterLayout == KeyboardCharacterLayout.TURKISH_Q) "AltGr" else "Alt", 1.5f),
        key(KeyboardLayouts.MOD_RCTRL, "Ctrl", 1.5f),
    )

    val corners = mapOf(
        KeyboardLayouts.MOD_LWIN to mapOf(
            KeyCorner.TOP_LEFT to CornerAction("Fn", KeyboardLayouts.KEY_FN),
        ),
        KeyboardLayouts.KEY_CAPSLOCK to mapOf(
            KeyCorner.TOP_RIGHT to CornerAction("Ins", KeyboardLayouts.KEY_INSERT),
            KeyCorner.BOTTOM_LEFT to CornerAction("ScrLk", KeyboardLayouts.KEY_SCROLLLOCK),
        ),
    ).toMutableMap()
    if (characterLayout == KeyboardCharacterLayout.TURKISH_Q) {
        corners[KeyboardLayouts.KEY_CAPSLOCK] = corners.getValue(KeyboardLayouts.KEY_CAPSLOCK) +
            (KeyCorner.TOP_LEFT to CornerAction("< >", KeyboardLayouts.KEY_NONUS_BACKSLASH))
    }
    val width = 14f
    val result = mutableListOf<TouchKeyboardKey>()
    rows.forEachIndexed { index, row ->
        val functionRow = row.any { it.physicalKeyCode in KeyboardLayouts.KEY_F1..KeyboardLayouts.KEY_F12 }
        val bottomRow = index == rows.lastIndex
        val weights = row.map { current ->
            when {
                bottomRow -> current.widthRatio
                functionRow && current.physicalKeyCode == KeyboardLayouts.KEY_DELETE -> 1.25f
                !functionRow && row.size > 14 && current.physicalKeyCode in setOf(
                    KeyboardLayouts.KEY_ESC, KeyboardLayouts.KEY_BACKSPACE, KeyboardLayouts.KEY_DELETE) -> 0.75f
                else -> 1f
            }
        }
        val scale = width / weights.sum()
        var x = 0f
        row.forEachIndexed { column, original ->
            val keyWidth = weights[column] * scale
            val positioned = original.copy(x = x, y = index.toFloat(), widthRatio = keyWidth, heightRatio = 1f)
            result += TouchKeyboardKey(positioned, corners[positioned.physicalKeyCode].orEmpty())
            x += keyWidth
        }
    }
    return result
}

/** Kept below the touchpad so the navigation cluster cannot narrow the letter rows. */
fun touchNavigationLayout(geometry: KeyboardGeometry): List<TouchKeyboardKey> {
    val source = KeyboardLayouts.getLayout(geometry).flatten()
    return listOf(
        Triple(KeyboardLayouts.KEY_LEFT, "←", 0f to 0f),
        Triple(KeyboardLayouts.KEY_DOWN, "↓", 1f to 0f),
        Triple(KeyboardLayouts.KEY_UP, "↑", 2f to 0f),
        Triple(KeyboardLayouts.KEY_RIGHT, "→", 3f to 0f),
    ).map { (code, legend, position) ->
        val original = source.firstOrNull { it.physicalKeyCode == code }
            ?: KeyLayoutInfo(legend = legend, keyCode = code, category = KeyColorCategory.ACCENT)
        val action = when (code) {
            KeyboardLayouts.KEY_LEFT -> CornerAction("Home", KeyboardLayouts.KEY_HOME)
            KeyboardLayouts.KEY_RIGHT -> CornerAction("End", KeyboardLayouts.KEY_END)
            KeyboardLayouts.KEY_UP -> CornerAction("PgUp", KeyboardLayouts.KEY_PAGEUP)
            else -> CornerAction("PgDn", KeyboardLayouts.KEY_PAGEDOWN)
        }
        val corners = when (code) {
            KeyboardLayouts.KEY_LEFT -> listOf(KeyCorner.TOP_LEFT, KeyCorner.BOTTOM_LEFT)
            KeyboardLayouts.KEY_RIGHT -> listOf(KeyCorner.TOP_RIGHT, KeyCorner.BOTTOM_RIGHT)
            KeyboardLayouts.KEY_UP -> listOf(KeyCorner.TOP_LEFT, KeyCorner.TOP_RIGHT)
            else -> listOf(KeyCorner.BOTTOM_LEFT, KeyCorner.BOTTOM_RIGHT)
        }.associateWith { action }
        TouchKeyboardKey(original.copy(
            styleId = "navigation:$code", x = position.first, y = position.second,
            widthRatio = 1f, heightRatio = 1f,
        ), corners, holdPrimaryAfterDelay = true)
    }
}
