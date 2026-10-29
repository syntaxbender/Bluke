package dev.arnv.bluke.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.*
import org.junit.Test

class KeyPressPreviewTest {
    @Test fun previewsStayInsideTheWindowAndAwayFromTheFingerAtEveryEdge() {
        val window = IntSize(640, 320)
        listOf(0f, 300f, 600f).forEach { left ->
            listOf(0f, 120f, 260f).forEach { top ->
                val key = Rect(left, top, left + 40f, top + 50f)
                listOf(52, 104).forEach { width ->
                    val size = IntSize(width, 48)
                    val position = keyPreviewPosition(key, size, window, 8f, 6f)
                    val bounds = Rect(position.x.toFloat(), position.y.toFloat(),
                        position.x + size.width.toFloat(), position.y + size.height.toFloat())
                    assertTrue(bounds.left >= 0 && bounds.top >= 0)
                    assertTrue(bounds.right <= window.width && bounds.bottom <= window.height)
                    assertFalse("Preview must stay outside the finger's centre: $key -> $bounds", bounds.contains(key.center))
                }
            }
        }
    }

    @Test fun previewUsesTurkishCasingAndTheActualShiftAndAltGrSymbol() {
        val layout = KeyboardLayouts.getLayout(KeyboardGeometry.COMPACT_75, KeyboardCharacterLayout.TURKISH_Q).flatten()
        fun label(code: Int, modifiers: Set<Int> = emptySet(), caps: Boolean = false) = keyPreviewLabel(
            layout.single { it.physicalKeyCode == code }, KeyboardCharacterLayout.TURKISH_Q, modifiers, caps,
        )
        assertEquals(":", label(KeyboardLayouts.KEY_SLASH, setOf(KeyboardLayouts.MOD_LSHIFT)))
        assertEquals("@", label(KeyboardLayouts.KEY_Q, setOf(KeyboardLayouts.MOD_RALT)))
        assertEquals("İ", label(KeyboardLayouts.KEY_I, setOf(KeyboardLayouts.MOD_RALT, KeyboardLayouts.MOD_LSHIFT)))
        assertEquals("I", label(KeyboardLayouts.KEY_I, caps = true))
        assertEquals("İ", label(KeyboardLayouts.KEY_APOSTROPHE, caps = true))
        assertEquals("ı", label(KeyboardLayouts.KEY_I, setOf(KeyboardLayouts.MOD_LSHIFT), caps = true))
    }
}
