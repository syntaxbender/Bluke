package dev.arnv.bluke.ui

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InputModePreferencesTest {
    private val preferences = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("input-mode-migration-test", Context.MODE_PRIVATE)

    @Test fun anUpgradeEnablesAndSelectsTheSplitKeyboard() {
        preferences.edit {
            clear()
            putStringSet("cycle_connection_modes", setOf("keyboard", "touchpad", "gamepad", "media_presentation"))
            putInt("launch_mode", InputMode.TOUCHPAD.id)
        }
        assertEquals(defaultInputModeKeys, preferences.enabledInputModes().mapTo(linkedSetOf()) { it.preferenceKey })
        assertEquals(InputMode.SPLIT_KEYBOARD.id, preferences.getInt("launch_mode", -1))
    }

    @Test fun theOneTimeUpgradeKeepsOtherExistingFavorites() {
        val existing = setOf("keyboard", "touchpad")
        preferences.edit {
            clear()
            putStringSet("cycle_connection_modes", existing)
            putInt("launch_mode", InputMode.KEYBOARD.id)
        }
        assertEquals(existing + InputMode.SPLIT_KEYBOARD.preferenceKey,
            preferences.enabledInputModes().mapTo(linkedSetOf()) { it.preferenceKey })
        assertEquals(InputMode.SPLIT_KEYBOARD.id, preferences.getInt("launch_mode", -1))
    }

    @Test fun laterExplicitModeChoicesArePreserved() {
        preferences.edit { clear() }
        preferences.enabledInputModes()
        listOf(
            setOf("keyboard", "touchpad", "gamepad", "media_presentation"),
            setOf("keyboard", "touchpad", "gamepad"),
            setOf("keyboard", "touchpad"),
        ).forEach { selected ->
            preferences.edit {
                putStringSet("cycle_connection_modes", selected)
                putInt("launch_mode", InputMode.TOUCHPAD.id)
            }
            assertEquals(selected, preferences.enabledInputModes().mapTo(linkedSetOf()) { it.preferenceKey })
            assertEquals(InputMode.TOUCHPAD.id, preferences.getInt("launch_mode", -1))
        }
    }
}
