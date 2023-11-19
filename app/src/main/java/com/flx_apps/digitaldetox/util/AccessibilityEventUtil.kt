package com.flx_apps.digitaldetox.util

import android.view.accessibility.AccessibilityEvent

class AccessibilityEventUtil {
    companion object {
        fun createEvent(eventType: Int = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED): AccessibilityEvent {
            val accessibilityEvent =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    // for API level 33 and above, we need to use the AccessibilityEvent constructor
                    AccessibilityEvent(eventType)
                } else {
                    // for older API levels, we can use the AccessibilityEvent.obtain() method
                    AccessibilityEvent.obtain(eventType)
                }.apply {
                    if (packageName == null) {
                        packageName = "com.flx_apps.digitaldetox"
                    }
                    isFullScreen = true
                }
            return accessibilityEvent
        }

        /**
         * Returns the scroll delta of the given [accessibilityEvent] in the y direction.
         * This is only available for API level 28 and above, otherwise, 1 is returned.
         */
        fun getScrollDeltaY(accessibilityEvent: AccessibilityEvent): Int {
            var result = 1 // assume a positive scroll delta by default
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                result = accessibilityEvent.scrollDeltaY
            }
            return result
        }
    }
}