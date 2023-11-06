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
    }
}