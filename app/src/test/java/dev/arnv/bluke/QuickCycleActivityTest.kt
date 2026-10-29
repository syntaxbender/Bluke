package dev.arnv.bluke

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.arnv.bluke.ui.InputMode
import dev.arnv.bluke.ui.defaultInputModeKeys
import dev.arnv.bluke.ui.enabledInputModes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w800dp-h360dp-land-xhdpi", sdk = [36])
class QuickCycleActivityTest {
    @get:Rule val compose = createAndroidComposeRule<QuickCycleActivity>()

    @Test fun splitModeStartsEnabledAndCanStillBeDisabledFromTheExistingSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val splitKey = InputMode.SPLIT_KEYBOARD.preferenceKey
        compose.runOnIdle {
            assertTrue(preferences.enabledInputModes().contains(InputMode.SPLIT_KEYBOARD))
            assertEquals(InputMode.SPLIT_KEYBOARD.id, preferences.getInt("launch_mode", -1))
        }
        compose.onNodeWithText("Keyboard + Touchpad").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertFalse(preferences.enabledInputModes().contains(InputMode.SPLIT_KEYBOARD))
            assertEquals(defaultInputModeKeys - splitKey, preferences.getStringSet("cycle_connection_modes", null))
        }
        compose.onNodeWithText("Keyboard + Touchpad").performClick()
        compose.runOnIdle {
            assertEquals(defaultInputModeKeys, preferences.getStringSet("cycle_connection_modes", null))
        }
        compose.onRoot().captureRoboImage(filePath = "build/outputs/screenshots/input-mode-cycle-split-enabled.png")
    }
}
