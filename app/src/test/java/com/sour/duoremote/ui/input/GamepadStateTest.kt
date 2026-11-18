package com.sour.duoremote.ui.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GamepadStateTest {

    @Test
    fun buttonBitsMatchXInput() {
        var state = GamepadState()
        val expected = mapOf(
            GamepadControl.DPAD_UP to 1,
            GamepadControl.DPAD_DOWN to 2,
            GamepadControl.DPAD_LEFT to 4,
            GamepadControl.DPAD_RIGHT to 8,
            GamepadControl.START to 16,
            GamepadControl.BACK to 32,
            GamepadControl.L3 to 64,
            GamepadControl.R3 to 128,
            GamepadControl.LB to 256,
            GamepadControl.RB to 512,
            GamepadControl.A to 4096,
            GamepadControl.B to 8192,
            GamepadControl.X to 16384,
            GamepadControl.Y to 32768,
        )
        for ((control, bit) in expected) {
            state = state.withControl(control, true)
            assertEquals(bit, state.buttons and bit)
            state = state.withControl(control, false)
            assertEquals(0, state.buttons and bit)
        }
        assertTrue(state.isNeutral)
    }

    @Test
    fun holdsMultipleButtons() {
        val state = GamepadState()
            .withControl(GamepadControl.A, true)
            .withControl(GamepadControl.RB, true)
            .withControl(GamepadControl.DPAD_LEFT, true)

        assertEquals(4096 or 512 or 4, state.buttons)
        assertEquals(4096 or 4, state.withControl(GamepadControl.RB, false).buttons)
    }

    @Test
    fun stickAppliesDeadzoneAndRescales() {
        assertEquals(0, GamepadState.axisValue(0f))
        assertEquals(0, GamepadState.axisValue(0.16f))
        assertEquals(0, GamepadState.axisValue(-0.16f))
        assertTrue(GamepadState.axisValue(0.17f) > 0)
        assertEquals(GamepadState.AXIS_MAX, GamepadState.axisValue(1f))
        assertEquals(-GamepadState.AXIS_MAX, GamepadState.axisValue(-1f))
        assertEquals(GamepadState.AXIS_MAX, GamepadState.axisValue(4f))
    }

    @Test
    fun leftStickInvertsAndroidY() {
        val state = GamepadState().withLeftStick(x = 0.8f, y = 0.8f)
        assertTrue(state.leftThumbX > 0)
        assertTrue(state.leftThumbY < 0)
    }

    @Test
    fun customDeadzoneAndInversionAreApplied() {
        val state = GamepadState().withLeftStick(
            x = 0.2f,
            y = 0.5f,
            deadzone = 0.25f,
            invertY = true,
        )
        assertEquals(0, state.leftThumbX)
        assertTrue(state.leftThumbY > 0)

        val right = GamepadState().withRightStick(
            x = -0.8f,
            y = 0.5f,
            deadzone = 0.08f,
            invertY = true,
        )
        assertTrue(right.rightThumbX < 0)
        assertTrue(right.rightThumbY > 0)
    }

    @Test
    fun rightStickIsIndependent() {
        val state = GamepadState().withRightStick(x = -1f, y = -1f)
        assertEquals(-GamepadState.AXIS_MAX, state.rightThumbX)
        assertEquals(GamepadState.AXIS_MAX, state.rightThumbY)
        assertEquals(0, state.leftThumbX)
    }

    @Test
    fun triggersAreScaledToBytes() {
        assertEquals(0, GamepadState.triggerValue(-1f))
        assertEquals(0, GamepadState.triggerValue(0f))
        assertEquals(128, GamepadState.triggerValue(0.5f))
        assertEquals(255, GamepadState.triggerValue(1f))
        assertEquals(255, GamepadState.triggerValue(3f))
    }

    @Test
    fun hatSetsAndClearsDpadBits() {
        val up = GamepadState().withHat(0f, -1f)
        assertEquals(GamepadControl.DPAD_UP.bit, up.buttons)

        val right = up.withHat(1f, 0f)
        assertEquals(GamepadControl.DPAD_RIGHT.bit, right.buttons)

        assertEquals(0, right.withHat(0f, 0f).buttons)
    }
}
