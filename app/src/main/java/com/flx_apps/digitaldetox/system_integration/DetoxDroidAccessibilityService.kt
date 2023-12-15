package com.flx_apps.digitaldetox.system_integration

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.flx_apps.digitaldetox.feature_types.OnAppOpenedSubscriptionFeature
import com.flx_apps.digitaldetox.feature_types.OnScrollEventSubscriptionFeature
import com.flx_apps.digitaldetox.features.FeaturesProvider
import com.flx_apps.digitaldetox.features.PauseButtonFeature
import kotlinx.coroutines.flow.MutableStateFlow

enum class DetoxDroidState {
    /**
     * The service is currently running.
     */
    Active,

    /**
     * The service is currently running, but a pause is in effect.
     */
    Paused,

    /**
     * The service is currently not running.
     */
    Inactive
}

/**
 * The [DetoxDroidAccessibilityService] is the main service of DetoxDroid. It receives all
 * accessibility events and forwards them to the [features] for processing. It also handles
 * scroll events and forwards them to the [features].
 */
open class DetoxDroidAccessibilityService : AccessibilityService() {
    companion object {
        /**
         * The current instance of the [DetoxDroidAccessibilityService]. This is set in
         * [onCreate] and unset in [onDestroy]. It is guaranteed by Android that only one instance
         * of the service exists at a time, so out of simplicity, we can use a static variable here
         * for easy access.
         */
        @JvmStatic
        var instance: DetoxDroidAccessibilityService? = null

        /**
         * The current state of the service. This is used to determine whether the service is
         * currently active, paused or inactive. It is a [MutableStateFlow], so it can be observed
         * by other components.
         * @see DetoxDroidState
         */
        val state: MutableStateFlow<DetoxDroidState> = MutableStateFlow(DetoxDroidState.Inactive)

        /**
         * Updates the [state] of the service (and returns it).
         */
        fun updateState(): DetoxDroidState {
            state.value = when {
                instance == null -> DetoxDroidState.Inactive
                PauseButtonFeature.isPausing() -> DetoxDroidState.Paused
                else -> DetoxDroidState.Active
            }
            return state.value
        }
    }

    private var lastPackage = ""
    private var ignoredEventClasses = mutableSetOf(
        "android.inputmethodservice.SoftInputWindow",
        "com.android.systemui.volume",
        "com.android.quickstep.RecentsActivity"
    )
    private var ignoredPackages = mutableSetOf<String>()
    private var screenTurnedOffReceiver = ScreenTurnedOffReceiver()

    var onKeyEventListener: ((KeyEvent) -> Boolean)? = null

    override fun onCreate() {
        super.onCreate()
        instance = this

        val intentFilter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenTurnedOffReceiver, intentFilter)

        // add all known keyboard packages to list of apps where we will not interfere with grayscale / color settings
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).enabledInputMethodList.forEach {
            ignoredPackages.add(it.packageName)
        }

        // call onStart() for all active features and update the state
        FeaturesProvider.activeFeatures.onEach { it.onStart(this) }
        updateState()
    }

    /**
     * A core method of the [AccessibilityService]. It is called whenever an accessibility event
     * occurs. It forwards the event to the [FeaturesProvider.activeFeatures] for processing.
     */
    override fun onAccessibilityEvent(accessibilityEvent: AccessibilityEvent) {
        if (PauseButtonFeature.isPausing()) return

        if (accessibilityEvent.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            handleScrollEvent(accessibilityEvent)
            return
        }

        if (accessibilityEvent.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            handleAppOpenedEvent(accessibilityEvent)
            return
        }
    }

    /**
     * Called when the service is interrupted. This is not used by DetoxDroid (however, [onDestroy]
     * is used to handle all cleanup operations).
     */
    override fun onInterrupt() {}

    /**
     * Called whenever a hardware button is pressed. This is used to detect the pause button (if
     * configured) and pause the service if it is pressed for more than 2 seconds.
     * @see PauseButtonFeature.hardwareKey
     */
    override fun onKeyEvent(event: KeyEvent?): Boolean {
        event?.let {
            onKeyEventListener?.let { listener ->
                if (listener(event)) {
                    return true // event was handled by listener, consume it
                }
            }
        }
        if (event?.keyCode == PauseButtonFeature.hardwareKey && event.eventTime - event.downTime > 2000) {
            // TODO this currently waits until the key is released, then checks if it was pressed for more than 2 seconds;
            //   it would be better to check if the key is still pressed after 2 seconds and then pause,
            //   but that would need a background thread and come with some resource overhead
            PauseButtonFeature.togglePause(this)
        }
        return super.onKeyEvent(event)
    }

    /**
     * Called when an app is opened. If some conditions are met, it forwards the event to the
     * intersection of [FeaturesProvider.activeFeatures] and [FeaturesProvider.onAppOpenedFeatures].
     */
    private fun handleAppOpenedEvent(accessibilityEvent: AccessibilityEvent) {
        if (accessibilityEvent.packageName == null || accessibilityEvent.packageName == lastPackage) {
            // ignore events that do not contain a package name or that are triggered by the same app
            return
        }
        lastPackage = accessibilityEvent.packageName.toString()

        if (ignoredEventClasses.contains(accessibilityEvent.className) || ignoredPackages.contains(
                lastPackage
            )
        ) {
            // ignore events that are known to be irrelevant
            return
        }

        // forward event to all active features that implement the OnAppOpenedSubscriptionFeature interface
        FeaturesProvider.activeFeatures.intersect(FeaturesProvider.onAppOpenedFeatures).forEach {
            (it as OnAppOpenedSubscriptionFeature).onAppOpened(
                this, lastPackage, accessibilityEvent
            )
        }
    }

    /**
     * Called when a scroll event is detected. It does some calculations to detect whether the event
     * is relevant and does some preprocessing before forwarding it to the intersection of
     * [FeaturesProvider.activeFeatures] and [FeaturesProvider.onScrollEventFeatures].
     */
    private fun handleScrollEvent(accessibilityEvent: AccessibilityEvent) {
        val scrollViewSize = when {
            accessibilityEvent.itemCount > 0 -> accessibilityEvent.itemCount
            else -> accessibilityEvent.maxScrollY
        }

        // dispose scroll events that contain too less information
        if (accessibilityEvent.source == null || scrollViewSize == -1 || accessibilityEvent.className == null) {
            return
        }

        val scrollViewId =
            OnScrollEventSubscriptionFeature.calculateScrollViewId(accessibilityEvent)

        FeaturesProvider.activeFeatures.intersect(FeaturesProvider.onScrollEventFeatures).forEach {
            (it as OnScrollEventSubscriptionFeature).onScrollEvent(
                this,
                scrollViewId,
                scrollViewSize = scrollViewSize,
                accessibilityEvent = accessibilityEvent
            )
        }
    }

    /**
     * Called when the service is destroyed. It pauses all features and unregisters the screen
     * turned off receiver, then sets the [instance] to null and updates the [state].
     */
    override fun onDestroy() {
        super.onDestroy()
        PauseButtonFeature.pauseFeatures(this, stop = true)
        kotlin.runCatching { unregisterReceiver(screenTurnedOffReceiver) }
        instance = null
        updateState()
    }
}

