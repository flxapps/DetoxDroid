package com.flx_apps.digitaldetox.util

import android.view.accessibility.AccessibilityEvent

class AccessibilityEventUtil {
    companion object {
        fun createEvent(eventType: Int = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED): AccessibilityEvent {
            val accessibilityEvent =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    AccessibilityEvent(eventType)
                } else {
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
         *
         * Note: the unit of [AccessibilityEvent.scrollDeltaY] is framework-dependent — pixels for
         * RecyclerView and Compose, list positions (values like 1, –1) for legacy list widgets,
         * and always 0 for frameworks that don't populate the field. See [ScrollDistanceEstimator]
         * for how these flavors are disambiguated when a physical distance is needed.
         */
        fun getScrollDeltaY(accessibilityEvent: AccessibilityEvent): Int {
            var result = 1 // assume a positive scroll delta by default
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                result = accessibilityEvent.scrollDeltaY
            }
            return result
        }

        /**
         * Returns true if the scroll event is a downward scroll.
         *
         * On API 28+, [AccessibilityEvent.scrollDeltaY] is used:
         * - positive value  → content moved down (user scrolled down) → true
         * - negative value  → user scrolled up → false
         * - zero value      → direction unknown (container did not report delta) → **true**,
         *   because treating it as down is less harmful than discarding the event entirely.
         *   Many apps (especially those with virtualized lists) report 0 even for valid scrolls.
         *
         * On API < 28, the OS does not provide direction info, so we conservatively assume
         * all scrolls are downward.
         */
        fun isDownScrollEvent(accessibilityEvent: AccessibilityEvent): Boolean {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) return true
            val deltaY = accessibilityEvent.scrollDeltaY
            return deltaY >= 0
        }
    }
}