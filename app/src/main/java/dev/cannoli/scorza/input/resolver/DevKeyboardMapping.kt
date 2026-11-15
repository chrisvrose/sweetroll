package dev.cannoli.scorza.input.resolver

import android.view.KeyEvent
import dev.cannoli.scorza.input.ConnectedDevice
import dev.cannoli.scorza.input.DeviceMapping
import dev.cannoli.scorza.input.DeviceMatchRule
import dev.cannoli.scorza.input.GlyphStyle
import dev.cannoli.scorza.input.InputBinding
import dev.cannoli.scorza.input.MappingSource

/**
 * Mapping for the development-only keyboard controller, used to drive the launcher and the IGM
 * from an Android Virtual Device where no gamepad is attached. Only ever built when
 * `ControllerBridge.devKeyboardEnabled` is set, and never persisted to the MappingRepository.
 *
 * This is the whole "keyboard keys arrive as button presses" step: a DeviceMapping is already a
 * keycode-to-CanonicalButton table, so binding keyboard keycodes here routes them through the
 * same PortEvaluator / PortRouter / InputDispatcher path a real pad uses.
 */
object DevKeyboardMapping {

    const val ID = "dev_keyboard"

    const val DISPLAY_NAME = "Dev Keyboard"

    val BINDINGS: Map<dev.cannoli.igm.CanonicalButton, List<Int>> = mapOf(
        dev.cannoli.igm.CanonicalButton.BTN_UP to listOf(KeyEvent.KEYCODE_DPAD_UP),
        dev.cannoli.igm.CanonicalButton.BTN_DOWN to listOf(KeyEvent.KEYCODE_DPAD_DOWN),
        dev.cannoli.igm.CanonicalButton.BTN_LEFT to listOf(KeyEvent.KEYCODE_DPAD_LEFT),
        dev.cannoli.igm.CanonicalButton.BTN_RIGHT to listOf(KeyEvent.KEYCODE_DPAD_RIGHT),
        dev.cannoli.igm.CanonicalButton.BTN_SOUTH to listOf(
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER
        ),
        // KEYCODE_BACK alongside ESCAPE: some AVD keyboard layouts deliver Esc as BACK.
        dev.cannoli.igm.CanonicalButton.BTN_EAST to listOf(
            KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_BACK
        ),
        // Y and X sit on the buttons the PLUMBER glyphs label Y and X, so the key pressed matches
        // the letter the BottomBar shows. Tab and Backspace were tried here first and never
        // arrived: the view hierarchy consumes them for focus traversal before onKeyDown runs.
        dev.cannoli.igm.CanonicalButton.BTN_WEST to listOf(KeyEvent.KEYCODE_Y),
        dev.cannoli.igm.CanonicalButton.BTN_NORTH to listOf(KeyEvent.KEYCODE_X),
        dev.cannoli.igm.CanonicalButton.BTN_L to listOf(KeyEvent.KEYCODE_L),
        dev.cannoli.igm.CanonicalButton.BTN_R to listOf(KeyEvent.KEYCODE_R),
        dev.cannoli.igm.CanonicalButton.BTN_L2 to listOf(KeyEvent.KEYCODE_SEMICOLON),
        dev.cannoli.igm.CanonicalButton.BTN_R2 to listOf(KeyEvent.KEYCODE_T),
        dev.cannoli.igm.CanonicalButton.BTN_L3 to listOf(KeyEvent.KEYCODE_Z),
        dev.cannoli.igm.CanonicalButton.BTN_R3 to listOf(KeyEvent.KEYCODE_C),
        dev.cannoli.igm.CanonicalButton.BTN_START to listOf(KeyEvent.KEYCODE_SPACE),
        dev.cannoli.igm.CanonicalButton.BTN_SELECT to listOf(
            KeyEvent.KEYCODE_SHIFT_LEFT,
            KeyEvent.KEYCODE_SHIFT_RIGHT
        ),
        dev.cannoli.igm.CanonicalButton.BTN_MENU to listOf(KeyEvent.KEYCODE_M),
    )

    fun create(device: ConnectedDevice): DeviceMapping = DeviceMapping(
        id = ID,
        displayName = DISPLAY_NAME,
        match = DeviceMatchRule(name = device.name.takeIf { it.isNotEmpty() }),
        bindings = BINDINGS.mapValues { (_, keyCodes) -> keyCodes.map { InputBinding.Button(it) } },
        menuConfirm = dev.cannoli.igm.CanonicalButton.BTN_SOUTH,
        menuBack = dev.cannoli.igm.CanonicalButton.BTN_EAST,
        glyphStyle = GlyphStyle.PLUMBER,
        source = MappingSource.ANDROID_DEFAULT,
    )
}
