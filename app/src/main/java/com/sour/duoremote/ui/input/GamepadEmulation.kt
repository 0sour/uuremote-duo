package com.sour.duoremote.ui.input

import kotlin.math.abs

/**
 * Client-side gamepad-to-keyboard/mouse templates. It needs no host support: the controller turns
 * each [GamepadControl] or stick sample into ordinary mouse/key RPC actions, so the remote sees the
 * same input as the touchpad and built-in keyboard.
 *
 * The class is Android-free apart from the shared [GamepadControl] enum, which keeps the mapping
 * unit-testable.
 */
enum class GamepadEmulationMode(val storage: String, val label: String) {
    OFF("off", "关闭"),
    DESKTOP("desktop", "桌面"),
    FPS("fps", "FPS"),
    EMULATOR("emulator", "模拟器");

    companion object {
        fun fromStorage(value: String?): GamepadEmulationMode =
            entries.firstOrNull { it.storage == value } ?: OFF
    }
}

sealed interface EmulatedAction {
    data class Key(val vk: Int, val down: Boolean) : EmulatedAction
    data class MouseButton(val button: Int, val down: Boolean) : EmulatedAction
    data class MouseMove(val dx: Float, val dy: Float) : EmulatedAction
    data class Scroll(val dx: Int, val dy: Int) : EmulatedAction
}

class GamepadEmulation(val mode: GamepadEmulationMode) {

    private val heldKeys = LinkedHashSet<Int>()
    private val heldMouseButtons = LinkedHashSet<Int>()
    private val stickKeys = LinkedHashSet<Int>()
    private var leftTriggerKey = false
    private var rightTriggerKey = false
    private var leftTriggerValue = 0f
    private var rightTriggerValue = 0f
    private var scrollX = 0f
    private var scrollY = 0f

    fun onButton(control: GamepadControl, down: Boolean): List<EmulatedAction> = when (mode) {
        GamepadEmulationMode.OFF -> emptyList()
        GamepadEmulationMode.DESKTOP -> desktopButton(control, down)
        GamepadEmulationMode.FPS -> fpsButton(control, down)
        GamepadEmulationMode.EMULATOR -> emulatorButton(control, down)
    }

    fun onTriggerKey(left: Boolean, down: Boolean): List<EmulatedAction> {
        if (mode != GamepadEmulationMode.FPS) return emptyList()
        if (left) leftTriggerKey = down else rightTriggerKey = down
        return syncTriggerButtons()
    }

    fun onTriggers(left: Float, right: Float): List<EmulatedAction> {
        if (mode != GamepadEmulationMode.FPS) return emptyList()
        leftTriggerValue = left.coerceIn(0f, 1f)
        rightTriggerValue = right.coerceIn(0f, 1f)
        return syncTriggerButtons()
    }

    /**
     * Feeds all available stick axes. Desktop uses the left stick as the pointer and the right
     * stick as a wheel; FPS uses the right stick as the pointer and the left as WASD; the emulator
     * template uses the left stick as a D-pad.
     */
    fun onSticks(
        leftX: Float,
        leftY: Float,
        rightX: Float,
        rightY: Float,
    ): List<EmulatedAction> {
        if (mode == GamepadEmulationMode.OFF) return emptyList()
        val lx = shape(leftX)
        val ly = shape(leftY)
        val rx = shape(rightX)
        val ry = shape(rightY)
        return when (mode) {
            GamepadEmulationMode.DESKTOP -> {
                val actions = ArrayList<EmulatedAction>(1)
                if (lx != 0f || ly != 0f) {
                    actions += EmulatedAction.MouseMove(lx * MOUSE_SPEED, ly * MOUSE_SPEED)
                }
                actions += scrollFrom(rx, ry)
                actions
            }

            GamepadEmulationMode.FPS -> {
                val actions = ArrayList<EmulatedAction>(1)
                actions += directionKeys(lx, ly, VK_W, VK_S, VK_A, VK_D)
                if (rx != 0f || ry != 0f) {
                    actions += EmulatedAction.MouseMove(rx * MOUSE_SPEED, ry * MOUSE_SPEED)
                }
                actions
            }

            GamepadEmulationMode.EMULATOR -> {
                if (abs(lx) < STICK_THRESHOLD && abs(ly) < STICK_THRESHOLD) {
                    directionKeys(0f, 0f, VK_UP, VK_DOWN, VK_LEFT, VK_RIGHT)
                } else {
                    directionKeys(lx, ly, VK_UP, VK_DOWN, VK_LEFT, VK_RIGHT)
                }
            }

            GamepadEmulationMode.OFF -> emptyList()
        }
    }

    /** Releases every key/button this template is holding; call when disabling or leaving input. */
    fun releaseAll(): List<EmulatedAction> {
        val actions = ArrayList<EmulatedAction>()
        heldKeys.toList().forEach { actions += EmulatedAction.Key(it, false) }
        heldMouseButtons.toList().forEach { actions += EmulatedAction.MouseButton(it, false) }
        heldKeys.clear()
        heldMouseButtons.clear()
        stickKeys.clear()
        leftTriggerKey = false
        rightTriggerKey = false
        leftTriggerValue = 0f
        rightTriggerValue = 0f
        scrollX = 0f
        scrollY = 0f
        return actions
    }

    private fun desktopButton(control: GamepadControl, down: Boolean): List<EmulatedAction> =
        when (control) {
            GamepadControl.A -> mouse(1, down)
            GamepadControl.B -> mouse(2, down)
            GamepadControl.X -> mouse(3, down)
            GamepadControl.Y -> key(VK_ENTER, down)
            GamepadControl.LB -> key(VK_BROWSER_BACK, down)
            GamepadControl.RB -> key(VK_BROWSER_FORWARD, down)
            GamepadControl.START -> key(VK_ENTER, down)
            GamepadControl.BACK -> key(VK_ESC, down)
            GamepadControl.DPAD_UP -> key(VK_UP, down)
            GamepadControl.DPAD_DOWN -> key(VK_DOWN, down)
            GamepadControl.DPAD_LEFT -> key(VK_LEFT, down)
            GamepadControl.DPAD_RIGHT -> key(VK_RIGHT, down)
            GamepadControl.L3, GamepadControl.R3 -> emptyList()
        }

    private fun fpsButton(control: GamepadControl, down: Boolean): List<EmulatedAction> =
        when (control) {
            GamepadControl.A -> key(VK_SPACE, down)
            GamepadControl.B -> key(VK_CTRL, down)
            GamepadControl.X -> key(VK_R, down)
            GamepadControl.Y -> key(VK_E, down)
            GamepadControl.LB -> key(VK_SHIFT, down)
            GamepadControl.RB -> key(VK_TAB, down)
            GamepadControl.START -> key(VK_ESC, down)
            GamepadControl.BACK -> key(VK_TAB, down)
            GamepadControl.L3 -> key(VK_CTRL, down)
            GamepadControl.R3 -> key(VK_SHIFT, down)
            GamepadControl.DPAD_UP -> key(VK_1, down)
            GamepadControl.DPAD_RIGHT -> key(VK_2, down)
            GamepadControl.DPAD_DOWN -> key(VK_3, down)
            GamepadControl.DPAD_LEFT -> key(VK_4, down)
        }

    private fun emulatorButton(control: GamepadControl, down: Boolean): List<EmulatedAction> =
        when (control) {
            GamepadControl.A -> key(VK_Z, down)
            GamepadControl.B -> key(VK_X, down)
            GamepadControl.X -> key(VK_A, down)
            GamepadControl.Y -> key(VK_S, down)
            GamepadControl.LB -> key(VK_Q, down)
            GamepadControl.RB -> key(VK_W, down)
            GamepadControl.START -> key(VK_ENTER, down)
            GamepadControl.BACK -> key(VK_SHIFT, down)
            GamepadControl.L3, GamepadControl.R3 -> emptyList()
            GamepadControl.DPAD_UP -> key(VK_UP, down)
            GamepadControl.DPAD_DOWN -> key(VK_DOWN, down)
            GamepadControl.DPAD_LEFT -> key(VK_LEFT, down)
            GamepadControl.DPAD_RIGHT -> key(VK_RIGHT, down)
        }

    private fun key(vk: Int, down: Boolean): List<EmulatedAction> {
        if (down) {
            if (!heldKeys.add(vk)) return emptyList()
        } else {
            if (!heldKeys.remove(vk)) return emptyList()
        }
        return listOf(EmulatedAction.Key(vk, down))
    }

    private fun mouse(button: Int, down: Boolean): List<EmulatedAction> {
        if (down) {
            if (!heldMouseButtons.add(button)) return emptyList()
        } else {
            if (!heldMouseButtons.remove(button)) return emptyList()
        }
        return listOf(EmulatedAction.MouseButton(button, down))
    }

    private fun directionKeys(
        x: Float,
        y: Float,
        up: Int,
        down: Int,
        left: Int,
        right: Int,
    ): List<EmulatedAction> {
        val wanted = LinkedHashSet<Int>(4)
        if (y < -STICK_THRESHOLD) wanted += up
        if (y > STICK_THRESHOLD) wanted += down
        if (x < -STICK_THRESHOLD) wanted += left
        if (x > STICK_THRESHOLD) wanted += right
        val actions = ArrayList<EmulatedAction>(4)
        (stickKeys - wanted).forEach { vk ->
            stickKeys.remove(vk)
            if (heldKeys.remove(vk)) actions += EmulatedAction.Key(vk, false)
        }
        (wanted - stickKeys).forEach { vk ->
            stickKeys += vk
            if (heldKeys.add(vk)) actions += EmulatedAction.Key(vk, true)
        }
        return actions
    }

    private fun scrollFrom(x: Float, y: Float): List<EmulatedAction> {
        scrollX = (scrollX + x * SCROLL_SPEED).coerceIn(-40f, 40f)
        scrollY = (scrollY + y * SCROLL_SPEED).coerceIn(-40f, 40f)
        val dx = scrollX.toInt()
        val dy = scrollY.toInt()
        if (dx == 0 && dy == 0) return emptyList()
        scrollX -= dx
        scrollY -= dy
        return listOf(EmulatedAction.Scroll(dx, dy))
    }

    private fun syncTriggerButtons(): List<EmulatedAction> {
        val actions = ArrayList<EmulatedAction>(2)
        actions += triggerButton(2, leftTriggerKey || leftTriggerValue > 0.5f)
        actions += triggerButton(1, rightTriggerKey || rightTriggerValue > 0.5f)
        return actions
    }

    private fun triggerButton(button: Int, down: Boolean): List<EmulatedAction> {
        val changed = if (down) heldMouseButtons.add(button) else heldMouseButtons.remove(button)
        return if (changed) listOf(EmulatedAction.MouseButton(button, down)) else emptyList()
    }

    private fun shape(value: Float): Float {
        val magnitude = abs(value)
        if (magnitude <= STICK_DEADZONE) return 0f
        val scaled = (magnitude - STICK_DEADZONE) / (1f - STICK_DEADZONE)
        return scaled.coerceIn(0f, 1f) * if (value < 0f) -1f else 1f
    }

    private companion object {
        const val STICK_DEADZONE = 0.18f
        const val STICK_THRESHOLD = 0.35f
        const val MOUSE_SPEED = 16f
        const val SCROLL_SPEED = 1.5f

        const val VK_TAB = 9
        const val VK_ENTER = 13
        const val VK_SHIFT = 16
        const val VK_CTRL = 17
        const val VK_ESC = 27
        const val VK_SPACE = 32
        const val VK_LEFT = 37
        const val VK_UP = 38
        const val VK_RIGHT = 39
        const val VK_DOWN = 40
        const val VK_1 = 49
        const val VK_2 = 50
        const val VK_3 = 51
        const val VK_4 = 52
        const val VK_A = 65
        const val VK_D = 68
        const val VK_E = 69
        const val VK_Q = 81
        const val VK_R = 82
        const val VK_S = 83
        const val VK_W = 87
        const val VK_X = 88
        const val VK_Z = 90
        const val VK_BROWSER_BACK = 166
        const val VK_BROWSER_FORWARD = 167
    }
}
