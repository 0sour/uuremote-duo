package com.sour.duoremote.ui.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GamepadEmulationTest {

    @Test
    fun desktopMapsFaceButtonsToMouseAndSticksToPointerAndWheel() {
        val emulation = GamepadEmulation(GamepadEmulationMode.DESKTOP)

        assertEquals(
            listOf(EmulatedAction.MouseButton(1, true)),
            emulation.onButton(GamepadControl.A, true),
        )
        assertEquals(
            listOf(EmulatedAction.MouseButton(1, false)),
            emulation.onButton(GamepadControl.A, false),
        )
        assertEquals(
            listOf(EmulatedAction.MouseButton(2, true)),
            emulation.onButton(GamepadControl.B, true),
        )

        val move = emulation.onSticks(leftX = 1f, leftY = 0f, rightX = 0f, rightY = 0f)
        assertTrue(move.single() is EmulatedAction.MouseMove)
        assertTrue((move.single() as EmulatedAction.MouseMove).dx > 0f)

        val scroll = emulation.onSticks(leftX = 0f, leftY = 0f, rightX = 0f, rightY = 1f)
        assertTrue(scroll.single() is EmulatedAction.Scroll)
        assertTrue((scroll.single() as EmulatedAction.Scroll).dy > 0)
    }

    @Test
    fun fpsMapsLeftStickToWasdAndTriggersToMouseButtons() {
        val emulation = GamepadEmulation(GamepadEmulationMode.FPS)

        val down = emulation.onSticks(0f, -1f, 0f, 0f)
        assertEquals(1, down.size)
        assertEquals(EmulatedAction.Key('W'.code, true), down.single())
        val up = emulation.onSticks(0f, 0f, 0f, 0f)
        assertEquals(EmulatedAction.Key('W'.code, false), up.single())

        val aim = emulation.onTriggers(left = 1f, right = 0f)
        assertEquals(listOf(EmulatedAction.MouseButton(2, true)), aim)
        val fire = emulation.onTriggers(left = 1f, right = 1f)
        assertEquals(listOf(EmulatedAction.MouseButton(1, true)), fire)
        val release = emulation.onTriggers(left = 0f, right = 0f)
        assertEquals(2, release.size)
        assertTrue(release.all { it is EmulatedAction.MouseButton && !it.down })
    }

    @Test
    fun emulatorMapsFaceButtonsAndDpad() {
        val emulation = GamepadEmulation(GamepadEmulationMode.EMULATOR)
        assertEquals(
            listOf(EmulatedAction.Key('Z'.code, true)),
            emulation.onButton(GamepadControl.A, true),
        )
        assertEquals(
            listOf(EmulatedAction.Key(40, true)),
            emulation.onButton(GamepadControl.DPAD_DOWN, true),
        )
    }

    @Test
    fun releaseAllReleasesHeldKeysAndButtons() {
        val emulation = GamepadEmulation(GamepadEmulationMode.FPS)
        emulation.onSticks(0f, -1f, 1f, 0f)
        emulation.onButton(GamepadControl.A, true)
        emulation.onTriggers(0f, 1f)

        val released = emulation.releaseAll()
        val keys = released.filterIsInstance<EmulatedAction.Key>()
        val buttons = released.filterIsInstance<EmulatedAction.MouseButton>()
        assertTrue(keys.any { it.vk == 'W'.code && !it.down })
        assertTrue(keys.any { it.vk == 32 && !it.down })
        assertTrue(buttons.any { it.button == 1 && !it.down })
        assertTrue(emulation.releaseAll().isEmpty())
    }

    @Test
    fun offModeIgnoresInput() {
        val emulation = GamepadEmulation(GamepadEmulationMode.OFF)
        assertTrue(emulation.onButton(GamepadControl.A, true).isEmpty())
        assertTrue(emulation.onSticks(1f, 1f, 1f, 1f).isEmpty())
        assertTrue(emulation.onTriggers(1f, 1f).isEmpty())
    }
}
