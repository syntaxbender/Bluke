package dev.arnv.bluke.ui

import android.content.SharedPreferences
import androidx.core.content.edit

private const val INPUT_MODE_CYCLE_PREFERENCE = "cycle_connection_modes"
internal const val SPLIT_KEYBOARD_DEFAULT_MIGRATED = "split_keyboard_default_migrated"

enum class InputMode(
    val id: Int,
    val preferenceKey: String,
    val displayName: String,
) {
    KEYBOARD(0, "keyboard", "Keyboard"),
    SPLIT_KEYBOARD(5, "split_keyboard_touchpad", "Keyboard + Touchpad"),
    TOUCHPAD(1, "touchpad", "Touchpad"),
    GAMEPAD(2, "gamepad", "Gamepad"),
    MEDIA_PRESENTATION(4, "media_presentation", "Multimedia"),
    ;

    companion object {
        fun fromId(id: Int): InputMode = entries.firstOrNull { it.id == id } ?: KEYBOARD
    }
}

private val legacyDefaultModeKeys = setOf("keyboard", "touchpad", "gamepad")
private val previousDefaultModeKeys = legacyDefaultModeKeys + "media_presentation"
private const val REMOVED_MOUSE_MODE_KEY = "mouse"
private const val REMOVED_KEYBOARD_TOUCHPAD_MODE_KEY = "keyboard_touchpad"
val defaultInputModeKeys: Set<String> = InputMode.entries.mapTo(linkedSetOf()) { it.preferenceKey }

fun normalizeInputModeKeys(savedKeys: Set<String>?, upgradeLegacyDefaults: Boolean = true): Set<String> {
    val normalized = when {
        savedKeys == null || (upgradeLegacyDefaults && (savedKeys == legacyDefaultModeKeys || savedKeys == previousDefaultModeKeys)) -> defaultInputModeKeys
        else -> savedKeys.mapNotNullTo(linkedSetOf()) { key ->
            when (key) {
                REMOVED_MOUSE_MODE_KEY -> InputMode.MEDIA_PRESENTATION.preferenceKey
                REMOVED_KEYBOARD_TOUCHPAD_MODE_KEY -> null
                else -> key
            }
        }.filterTo(linkedSetOf()) { key ->
            InputMode.entries.any { it.preferenceKey == key }
        }
    }
    return normalized.ifEmpty { setOf(InputMode.KEYBOARD.preferenceKey) }
}

fun SharedPreferences.enabledInputModes(): List<InputMode> {
    val saved = getStringSet(INPUT_MODE_CYCLE_PREFERENCE, null)?.toSet()
    val applySplitDefault = !getBoolean(SPLIT_KEYBOARD_DEFAULT_MIGRATED, false)
    val normalized = normalizeInputModeKeys(saved, upgradeLegacyDefaults = applySplitDefault)
    val enabled = if (applySplitDefault) normalized + InputMode.SPLIT_KEYBOARD.preferenceKey else normalized
    if (saved != enabled || applySplitDefault) {
        edit {
            putStringSet(INPUT_MODE_CYCLE_PREFERENCE, enabled)
            if (applySplitDefault) {
                putInt("launch_mode", InputMode.SPLIT_KEYBOARD.id)
                putBoolean(SPLIT_KEYBOARD_DEFAULT_MIGRATED, true)
            }
        }
    }
    return InputMode.entries.filter { enabled.contains(it.preferenceKey) }
}
