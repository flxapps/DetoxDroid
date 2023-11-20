package com.flx_apps.digitaldetox.util

import android.view.KeyEvent

object KeyEventUtil {
    /**
     * Returns a short string representation of the given key code. Basically, it just removes the
     * "KEYCODE_" prefix.
     */
    fun keyCodeToShortString(keyCode: Int): String {
        return KeyEvent.keyCodeToString(keyCode).replace("KEYCODE_", "")
    }
}