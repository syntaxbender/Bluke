package dev.arnv.bluke.ui

import org.junit.Assert.*
import org.junit.Test

class TouchKeyboardLayoutTest {
    @Test fun everyGeometryAndCharacterLayoutHasLargeAlignedArrowsAndCornerNavigation() {
        KeyboardGeometry.entries.forEach { geometry ->
            KeyboardCharacterLayout.entries.forEach { characters ->
                val keyboard = touchKeyboardLayout(geometry, characters)
                val keys = keyboard + touchNavigationLayout(geometry)
                fun key(code: Int) = keys.single { it.layout.physicalKeyCode == code }
                val up = key(KeyboardLayouts.KEY_UP).layout
                val down = key(KeyboardLayouts.KEY_DOWN).layout
                val left = key(KeyboardLayouts.KEY_LEFT).layout
                val right = key(KeyboardLayouts.KEY_RIGHT).layout
                assertEquals(up.y, down.y, 0.001f)
                assertEquals(left.y, right.y, 0.001f)
                assertEquals(left.x + left.widthRatio, down.x, 0.001f)
                assertEquals(down.x + down.widthRatio, up.x, 0.001f)
                assertEquals(up.x + up.widthRatio, right.x, 0.001f)
                assertEquals(up.widthRatio, right.widthRatio, 0.001f)
                val typingKeys = keyboard.filter { it.layout.physicalKeyCode in KeyboardLayouts.KEY_A..KeyboardLayouts.KEY_Z ||
                    it.layout.physicalKeyCode in KeyboardLayouts.KEY_1..KeyboardLayouts.KEY_0 }
                assertEquals(36, typingKeys.size)
                val minimum = typingKeys.minOf { it.layout.widthRatio }
                val maximum = typingKeys.maxOf { it.layout.widthRatio }
                assertTrue("Typing widths should stay close: $geometry/$characters ($minimum..$maximum)", maximum / minimum <= 1.1f)
                typingKeys.forEach {
                    assertEquals(1f, it.layout.heightRatio, 0.001f)
                }
                val reference = key(KeyboardLayouts.KEY_A).layout.widthRatio
                listOf(KeyboardLayouts.KEY_CAPSLOCK, KeyboardLayouts.KEY_TAB,
                    KeyboardLayouts.MOD_LSHIFT, KeyboardLayouts.MOD_RSHIFT).forEach { code ->
                    assertTrue(key(code).layout.widthRatio / reference in 0.9f..1.1f)
                }
                assertTrue(keys.none { it.layout.keyCode in setOf(KeyboardLayouts.KEY_FN,
                    KeyboardLayouts.KEY_HOME, KeyboardLayouts.KEY_END, KeyboardLayouts.KEY_PAGEUP, KeyboardLayouts.KEY_PAGEDOWN) })
                val actions = keys.flatMap { it.corners.values }.map { it.keyCode }
                assertTrue(actions.containsAll(listOf(KeyboardLayouts.KEY_FN, KeyboardLayouts.KEY_HOME,
                    KeyboardLayouts.KEY_END, KeyboardLayouts.KEY_PAGEUP, KeyboardLayouts.KEY_PAGEDOWN)))
                assertTrue(key(KeyboardLayouts.MOD_LCTRL).corners.isEmpty())
                assertTrue(key(KeyboardLayouts.MOD_LALT).corners.isEmpty())
                assertEquals(KeyboardLayouts.KEY_FN, key(KeyboardLayouts.MOD_LWIN).corners.getValue(KeyCorner.TOP_LEFT).keyCode)
                assertTrue(key(KeyboardLayouts.KEY_TAB).corners.isEmpty())
                assertFalse(key(KeyboardLayouts.KEY_CAPSLOCK).corners.values.any { it.keyCode == KeyboardLayouts.KEY_FN || it.keyCode == KeyboardLayouts.KEY_DELETE })
                assertEquals(0f, key(KeyboardLayouts.KEY_DELETE).layout.y, 0.001f)
                assertTrue("Delete should remain full sized: $geometry/$characters (${key(KeyboardLayouts.KEY_DELETE).layout.widthRatio})",
                    key(KeyboardLayouts.KEY_DELETE).layout.widthRatio >= 0.73f)
                assertTrue(keys.all { it.layout.widthRatio > 0f })
                assertEquals(setOf(KeyboardLayouts.KEY_HOME), key(KeyboardLayouts.KEY_LEFT).corners.values.map { it.keyCode }.toSet())
                assertEquals(setOf(KeyboardLayouts.KEY_END), key(KeyboardLayouts.KEY_RIGHT).corners.values.map { it.keyCode }.toSet())
                assertEquals(setOf(KeyboardLayouts.KEY_PAGEUP), key(KeyboardLayouts.KEY_UP).corners.values.map { it.keyCode }.toSet())
                assertEquals(setOf(KeyboardLayouts.KEY_PAGEDOWN), key(KeyboardLayouts.KEY_DOWN).corners.values.map { it.keyCode }.toSet())
            }
        }
    }
}
