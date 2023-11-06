package com.flx_apps.digitaldetox.feature_types

import android.content.Context
import android.content.Intent
import android.graphics.Rect
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
         * Calculates a unique ID for the scroll view that triggered the scroll event.
         *
         * We do not want to use accessibilityEvent.source.hashCode(), because that would generate
         * new ids for e.g. different Twitter profile feeds. However, we want to treat different
         * Twitter profile feeds as one scroll view though, because they belong to the same
         * "infinitely scrolling" behavior triggering UI element.
         *
         * So instead, we do some elaborate calculation to get a unique ID for the scroll view
         * that takes into account the class name, package name, bounds and view id resource name.
         *
         * @param accessibilityEvent The accessibility event that triggered the scroll event.
         */
        fun calculateScrollViewId(accessibilityEvent: AccessibilityEvent): Int {
            // we will not use accessibilityEvent.source.hashCode(), because that generates new ids
            // for e.g. different Twitter profile feeds - we want to treat different Twitter profile
            // feeds as one scroll view though, because they belong to the same "infinitely scrolling"
            // behavior triggering UI element
            // so instead, we do some elaborate calculation to get a unique ID for the scroll view
            // that takes into account the class name, package name, bounds and view id resource name
            return accessibilityEvent.className.hashCode() + accessibilityEvent.packageName.hashCode() + accessibilityEvent.source!!.getBoundsInScreen(
                Rect()
            ).hashCode() + (accessibilityEvent.source!!.viewIdResourceName?.hashCode() ?: 0)
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