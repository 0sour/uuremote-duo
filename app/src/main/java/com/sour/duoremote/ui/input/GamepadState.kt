package com.sour.duoremote.ui.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * XInput-shaped gamepad snapshot sent to the remote through `gpd_update`.
 *
 * Buttons use the Windows XInput bitmask, sticks are -32767..32767 (Y up positive), and
 * triggers are 0..255. The class is intentionally free of Android types so the mapping can be
 * exercised by plain JVM unit tests; [GamepadMapper] is the Android adapter.
 */
data class GamepadState(
    val buttons: Int = 0,
    val leftTrigger: Int = 0,
    val rightTrigger: Int = 0,
    val leftThumbX: Int = 0,
    val leftThumbY: Int = 0,
    val rightThumbX: Int = 0,
    val rightThumbY: Int = 0,
) {
    val isNeutral: Boolean
        get() = buttons == 0 &&
            leftTrigger == 0 &&
            rightTrigger == 0 &&
            leftThumbX == 0 &&
            leftThumbY == 0 &&
            rightThumbX == 0 &&
            rightThumbY == 0

    fun withControl(control: GamepadControl, down: Boolean): GamepadState {
        val next = if (down) {
            buttons or control.bit
        } else {
            buttons and control.bit.inv()
        }
        return copy(buttons = next and 0xffff)
    }

    fun withTriggers(left: Float, right: Float): GamepadState = copy(
        leftTrigger = triggerValue(left),
        rightTrigger = triggerValue(right),
    )

    fun withLeftStick(
        x: Float,
        y: Float,
        deadzone: Float = DEADZONE,
        invertY: Boolean = false,
    ): GamepadState = copy(
        leftThumbX = axisValue(x, deadzone),
        leftThumbY = axisValue(if (invertY) y else -y, deadzone),
    )

    fun withRightStick(
        x: Float,
        y: Float,
        deadzone: Float = DEADZONE,
        invertY: Boolean = false,
    ): GamepadState = copy(
        rightThumbX = axisValue(x, deadzone),
        rightThumbY = axisValue(if (invertY) y else -y, deadzone),
    )

    /** Android hat axes are -1 (up/left) .. 1 (down/right). */
    fun withHat(x: Float, y: Float): GamepadState {
        var next = this
        if (x <= -HAT_THRESHOLD) next = next.withControl(GamepadControl.DPAD_LEFT, true)
        if (x >= HAT_THRESHOLD) next = next.withControl(GamepadControl.DPAD_RIGHT, true)
        if (y <= -HAT_THRESHOLD) next = next.withControl(GamepadControl.DPAD_UP, true)
        if (y >= HAT_THRESHOLD) next = next.withControl(GamepadControl.DPAD_DOWN, true)
        if (abs(x) < HAT_CLEAR) {
            next = next.withControl(GamepadControl.DPAD_LEFT, false)
                .withControl(GamepadControl.DPAD_RIGHT, false)
        }
        if (abs(y) < HAT_CLEAR) {
            next = next.withControl(GamepadControl.DPAD_UP, false)
                .withControl(GamepadControl.DPAD_DOWN, false)
        }
        return next
    }

    companion object {
        const val AXIS_MAX = 32767
        const val DEADZONE = 0.16f
        private const val HAT_THRESHOLD = 0.5f
        private const val HAT_CLEAR = 0.35f

        /**
         * Scales a normalized stick axis and applies a radial deadzone. Values just outside the
         * deadzone start near zero instead of jumping to 16% deflection.
         */
        fun axisValue(value: Float, deadzone: Float = DEADZONE): Int {
            val clamped = value.coerceIn(-1f, 1f)
            val magnitude = abs(clamped)
            val zone = deadzone.coerceIn(0f, 0.9f)
            if (magnitude <= zone) return 0
            val scaled = (magnitude - zone) / (1f - zone)
            return (scaled * AXIS_MAX * if (clamped < 0f) -1f else 1f)
                .roundToInt()
                .coerceIn(-AXIS_MAX, AXIS_MAX)
        }

        fun triggerValue(value: Float): Int =
            (value.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
    }
}

enum class GamepadControl(val bit: Int) {
    DPAD_UP(1),
    DPAD_DOWN(2),
    DPAD_LEFT(4),
    DPAD_RIGHT(8),
    START(16),
    BACK(32),
    L3(64),
    R3(128),
    LB(256),
    RB(512),
    A(4096),
    B(8192),
    X(16384),
    Y(32768),
}

/**
 * Translates Android key/motion events into [GamepadState].
 *
 * Face keys must already be normalized to canonical Xbox keycodes by [InputRouter]; Retro mode
 * swaps them at the entry point so `normalizeFaceKey` output is what the remote sees.
 */
object GamepadMapper {

    fun controlForKey(keyCode: Int): GamepadControl? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> GamepadControl.DPAD_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> GamepadControl.DPAD_DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> GamepadControl.DPAD_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> GamepadControl.DPAD_RIGHT
        KeyEvent.KEYCODE_BUTTON_START -> GamepadControl.START
        KeyEvent.KEYCODE_BUTTON_SELECT -> GamepadControl.BACK
        KeyEvent.KEYCODE_BUTTON_THUMBL -> GamepadControl.L3
        KeyEvent.KEYCODE_BUTTON_THUMBR -> GamepadControl.R3
        KeyEvent.KEYCODE_BUTTON_L1 -> GamepadControl.LB
        KeyEvent.KEYCODE_BUTTON_R1 -> GamepadControl.RB
        KeyEvent.KEYCODE_BUTTON_A -> GamepadControl.A
        KeyEvent.KEYCODE_BUTTON_B -> GamepadControl.B
        KeyEvent.KEYCODE_BUTTON_X -> GamepadControl.X
        KeyEvent.KEYCODE_BUTTON_Y -> GamepadControl.Y
        else -> null
    }

    fun applyKey(state: GamepadState, keyCode: Int, down: Boolean): GamepadState {
        val control = controlForKey(keyCode)
        if (control != null) return state.withControl(control, down)
        return when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_L2 -> state.copy(leftTrigger = if (down) 255 else 0)
            KeyEvent.KEYCODE_BUTTON_R2 -> state.copy(rightTrigger = if (down) 255 else 0)
            else -> state
        }
    }

    fun applyMotion(
        state: GamepadState,
        event: MotionEvent,
        deadzone: Float = GamepadState.DEADZONE,
        invertLeftY: Boolean = false,
        invertRightY: Boolean = false,
    ): GamepadState {
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK) &&
            !event.isFromSource(InputDevice.SOURCE_GAMEPAD)
        ) {
            return state
        }
        val device = event.device ?: return state

        fun axis(code: Int): Float? {
            val range = device.getMotionRange(code) ?: return null
            val span = range.max - range.min
            if (span <= 0f) return null
            return ((event.getAxisValue(code) - range.min) / span).coerceIn(0f, 1f)
        }

        fun signed(code: Int): Float? {
            val range = device.getMotionRange(code) ?: return null
            val span = range.max - range.min
            if (span <= 0f) return null
            val value = ((event.getAxisValue(code) - range.min) / span) * 2f - 1f
            return value.coerceIn(-1f, 1f)
        }

        var next = state

        val leftX = signed(MotionEvent.AXIS_X)
        val leftY = signed(MotionEvent.AXIS_Y)
        if (leftX != null || leftY != null) {
            next = next.withLeftStick(leftX ?: 0f, leftY ?: 0f, deadzone, invertLeftY)
        }

        // Xbox-style pads report the right stick on Z/RZ; some report RX/RY instead.
        val rightX = signed(MotionEvent.AXIS_Z) ?: signed(MotionEvent.AXIS_RX)
        val rightY = signed(MotionEvent.AXIS_RZ) ?: signed(MotionEvent.AXIS_RY)
        if (rightX != null || rightY != null) {
            next = next.withRightStick(rightX ?: 0f, rightY ?: 0f, deadzone, invertRightY)
        }

        val leftTrigger = axis(MotionEvent.AXIS_LTRIGGER)
            ?: axis(MotionEvent.AXIS_BRAKE)
        val rightTrigger = axis(MotionEvent.AXIS_RTRIGGER)
            ?: axis(MotionEvent.AXIS_GAS)
        if (leftTrigger != null) {
            next = next.copy(leftTrigger = GamepadState.triggerValue(leftTrigger))
        }
        if (rightTrigger != null) {
            next = next.copy(rightTrigger = GamepadState.triggerValue(rightTrigger))
        }

        val hatX = signed(MotionEvent.AXIS_HAT_X)
        val hatY = signed(MotionEvent.AXIS_HAT_Y)
        if (hatX != null || hatY != null) {
            next = next.withHat(hatX ?: 0f, hatY ?: 0f)
        }

        return next
    }
}
