package com.javzzap.laxbrowser.ui

import android.view.KeyEvent

/** Desktop-style keyboard shortcuts for tablets and hardware keyboards. */
object KeyboardShortcuts {
    fun match(event: KeyEvent): KeyboardShortcut? {
        val ctrl = event.isCtrlPressed
        val shift = event.isShiftPressed
        val alt = event.isAltPressed
        return when {
            ctrl && alt && event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> KeyboardShortcut.NEXT_SPACE
            ctrl && alt && event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> KeyboardShortcut.PREVIOUS_SPACE
            alt && !ctrl && event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> KeyboardShortcut.BACK
            alt && !ctrl && event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> KeyboardShortcut.FORWARD
            alt && !ctrl && event.keyCode == KeyEvent.KEYCODE_D -> KeyboardShortcut.FOCUS_ADDRESS
            event.keyCode == KeyEvent.KEYCODE_F5 -> KeyboardShortcut.RELOAD
            event.keyCode == KeyEvent.KEYCODE_F6 -> KeyboardShortcut.FOCUS_ADDRESS
            !ctrl -> null
            shift && event.keyCode == KeyEvent.KEYCODE_N -> KeyboardShortcut.NEW_PRIVATE_TAB
            shift && event.keyCode == KeyEvent.KEYCODE_T -> KeyboardShortcut.REOPEN_TAB
            shift && event.keyCode == KeyEvent.KEYCODE_A -> KeyboardShortcut.FOCUS_ADDRESS
            shift && event.keyCode == KeyEvent.KEYCODE_TAB -> KeyboardShortcut.PREVIOUS_TAB
            event.keyCode == KeyEvent.KEYCODE_TAB || event.keyCode == KeyEvent.KEYCODE_PAGE_DOWN -> KeyboardShortcut.NEXT_TAB
            event.keyCode == KeyEvent.KEYCODE_PAGE_UP -> KeyboardShortcut.PREVIOUS_TAB
            event.keyCode == KeyEvent.KEYCODE_T -> KeyboardShortcut.NEW_TAB
            event.keyCode == KeyEvent.KEYCODE_W -> KeyboardShortcut.CLOSE_TAB
            event.keyCode == KeyEvent.KEYCODE_L || event.keyCode == KeyEvent.KEYCODE_K -> KeyboardShortcut.FOCUS_ADDRESS
            event.keyCode == KeyEvent.KEYCODE_F -> KeyboardShortcut.FIND
            event.keyCode == KeyEvent.KEYCODE_D -> KeyboardShortcut.BOOKMARK
            event.keyCode == KeyEvent.KEYCODE_R -> KeyboardShortcut.RELOAD
            event.keyCode == KeyEvent.KEYCODE_EQUALS || event.keyCode == KeyEvent.KEYCODE_PLUS || event.keyCode == KeyEvent.KEYCODE_NUMPAD_ADD -> KeyboardShortcut.ZOOM_IN
            event.keyCode == KeyEvent.KEYCODE_MINUS || event.keyCode == KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> KeyboardShortcut.ZOOM_OUT
            event.keyCode == KeyEvent.KEYCODE_0 || event.keyCode == KeyEvent.KEYCODE_NUMPAD_0 -> KeyboardShortcut.ZOOM_RESET
            else -> null
        }
    }
}
