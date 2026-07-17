package com.flx_apps.digitaldetox.feature_types

import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * An interface for features that react to "screen turned off" events.
 * @see Intent.ACTION_SCREEN_OFF
 */
interface OnScreenTurnedOffSubscriptionFeature {
    /**
     * Called when the screen is turned off.
     */
    fun onScreenTurnedOff(context: Context?)
}

/**
 * An interface for features that react to app open events.
 * @see AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
 */
interface OnAppOpenedSubscriptionFeature {
    /**
     * Called when an app is opened.
     * @param packageName The package name of the app.
     * @param accessibilityEvent The accessibility event that triggered the app open.
     */
    fun onAppOpened(context: Context, packageName: String, accessibilityEvent: AccessibilityEvent)
}

/**
 * An interface for features that react to scroll events.
 * @see AccessibilityEvent.TYPE_VIEW_SCROLLED
 */
interface OnScrollEventSubscriptionFeature {
    companion object {
        /**
         * Calculates a stable ID for the scroll view that triggered the scroll event.
         *
         * We do not want to use accessibilityEvent.source.hashCode(), because that would generate
         * new ids for e.g. different Twitter profile feeds. However, we want to treat different
         * Twitter profile feeds as one scroll view, because they belong to the same "infinitely
         * scrolling" behavior triggering UI element.
         *
         * The ID is therefore derived from the class name, package name and view-id resource name.
         * The view bounds are deliberately *not* part of the ID: they shift with IME/layout changes
         * and would reset the endless-feed tracking mid-session. (Historically the code appeared to
         * hash the bounds, but actually hashed the `Unit` return value of `getBoundsInScreen` — a
         * constant — so this is the behavior the detection has always had.)
         *
         * @param accessibilityEvent The accessibility event that triggered the scroll event.
         */
        fun calculateScrollViewId(accessibilityEvent: AccessibilityEvent): Int {
            return accessibilityEvent.className.hashCode() +
                    accessibilityEvent.packageName.hashCode() +
                    (accessibilityEvent.source?.viewIdResourceName?.hashCode() ?: 0)
        }
    }

    /**
     * Called when a scroll event is detected.
     * @param scrollViewId The (calculated) ID of the scroll view.
     * @param scrollViewSize The size of the scroll view, either in pixels or items count
     * (depending on which information is available).
     * @param accessibilityEvent The accessibility event that triggered the scroll event.
     */
    fun onScrollEvent(
        context: Context,
        scrollViewId: Int,
        scrollViewSize: Int,
        accessibilityEvent: AccessibilityEvent
    )
}