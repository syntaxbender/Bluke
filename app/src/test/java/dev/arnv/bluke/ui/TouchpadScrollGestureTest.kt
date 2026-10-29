package dev.arnv.bluke.ui

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.arnv.bluke.bluetooth.BluetoothKeyboardManager
import dev.arnv.bluke.ui.theme.MyApplicationTheme
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w800dp-h360dp-land-xhdpi", sdk = [36], shadows = [TouchpadScrollGestureTest.HidShadow::class])
class TouchpadScrollGestureTest {
    @get:Rule val compose = createComposeRule()

    @Implements(BluetoothHidDevice::class)
    class HidShadow {
        companion object { val reports = CopyOnWriteArrayList<Pair<Int, List<Byte>>>() }
        @Implementation
        fun sendReport(device: BluetoothDevice, id: Int, data: ByteArray): Boolean {
            reports.add(id to data.toList())
            return true
        }
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = BluetoothKeyboardManager(context)
    @After fun closeManager() = manager.close()

    private fun show() {
        HidShadow.reports.clear()
        val device = context.getSystemService(BluetoothManager::class.java).adapter
            .getRemoteDevice("00:11:22:33:44:55")
        ReflectionHelpers.setField(manager, "hidDeviceProfile", Shadow.newInstanceOf(BluetoothHidDevice::class.java))
        ReflectionHelpers.getField<MutableStateFlow<BluetoothDevice?>>(manager, "_connectedDevice").value = device
        compose.setContent {
            MyApplicationTheme {
                Box(Modifier.fillMaxSize().testTag("touch_surface")) {
                    TouchGestureLayer(manager, 1.5f, 1f, TrackpadButtonMode.TWO_BUTTONS, {}, false)
                }
            }
        }
    }

    private fun awaitReports() {
        ReflectionHelpers.getField<ExecutorService>(manager, "reportExecutor").submit {}.get(5, TimeUnit.SECONDS)
    }

    @Test fun twoFingersOnTheTouchpadSendScrollReports() {
        show()
        val pad = compose.onNodeWithTag("touch_surface").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput {
            down(0, pad - Offset(20f, 30f))
            down(1, pad + Offset(20f, -30f))
            moveBy(0, Offset(0f, 50f))
            moveBy(1, Offset(0f, 50f))
            up(0)
            up(1)
        }
        awaitReports()
        assertTrue(HidShadow.reports.any { (id, bytes) -> id == 2 && bytes[3] != 0.toByte() })
    }

    @Test fun scrollingDoesNotMoveTheCursorWhenEitherFingerIsLiftedFirst() {
        show()
        val pad = compose.onNodeWithTag("touch_surface").fetchSemanticsNode().boundsInRoot.center
        listOf(0, 1).forEach { liftedFirst ->
            awaitReports()
            HidShadow.reports.clear()
            compose.onRoot().performTouchInput {
                down(0, pad - Offset(20f, 40f))
                down(1, pad + Offset(20f, -40f))
                moveBy(0, Offset(0f, 50f))
                moveBy(1, Offset(0f, 50f))
                up(liftedFirst)
                moveBy(1 - liftedFirst, Offset(15f, 15f))
                up(1 - liftedFirst)
            }
            awaitReports()
            val scrollReports = HidShadow.reports.filter { it.first == 2 }.map { it.second }
            assertTrue("The gesture must actually scroll", scrollReports.any { it[3] != 0.toByte() })
            assertTrue("Scroll lift-off must not move the cursor ($liftedFirst first)",
                scrollReports.all { it[1] == 0.toByte() && it[2] == 0.toByte() })
            assertTrue("Scrolling must not produce a mouse click", scrollReports.all { it[0] == 0.toByte() })

            HidShadow.reports.clear()
            compose.onRoot().performTouchInput {
                down(0, pad)
                moveBy(0, Offset(30f, -10f))
                up(0)
            }
            awaitReports()
            assertTrue("A new single-finger gesture should move the cursor normally",
                HidShadow.reports.any { (id, bytes) -> id == 2 && (bytes[1] != 0.toByte() || bytes[2] != 0.toByte()) })
            assertTrue(HidShadow.reports.filter { it.first == 2 }.all { it.second[3] == 0.toByte() })
        }
    }

    @Test fun scrollingWithOneFingerStationaryDoesNotBecomeARightClickOnRelease() {
        show()
        val pad = compose.onNodeWithTag("touch_surface").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput {
            down(0, pad - Offset(20f, 40f))
            down(1, pad + Offset(20f, -40f))
            moveBy(0, Offset(0f, 80f))
            up(0)
            up(1)
        }
        awaitReports()
        val reports = HidShadow.reports.filter { it.first == 2 }.map { it.second }
        assertTrue(reports.any { it[3] != 0.toByte() })
        assertTrue("Lifting the stationary finger after a scroll must not click", reports.all { it[0] == 0.toByte() })
        assertTrue(reports.all { it[1] == 0.toByte() && it[2] == 0.toByte() })
    }

    @Test fun aTwoFingerTapStillRightClicksWithoutMovingTheCursor() {
        show()
        val pad = compose.onNodeWithTag("touch_surface").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput {
            down(0, pad - Offset(20f, 0f))
            down(1, pad + Offset(20f, 0f))
            up(0)
            up(1)
        }
        awaitReports()
        val reports = HidShadow.reports.filter { it.first == 2 }.map { it.second }
        assertTrue(reports.any { it[0] == 2.toByte() })
        assertEquals(0.toByte(), reports.last()[0])
        assertTrue(reports.all { it[1] == 0.toByte() && it[2] == 0.toByte() && it[3] == 0.toByte() })
    }

}
