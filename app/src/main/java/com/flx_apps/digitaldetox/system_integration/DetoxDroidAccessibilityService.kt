package com.flx_apps.digitaldetox.system_integration

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.app.NotificationCompat
import com.flx_apps.digitaldetox.DetoxDroidApplication
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.data.repository.UsageStatsRepository
import com.flx_apps.digitaldetox.feature_types.OnAppOpenedSubscriptionFeature
import com.flx_apps.digitaldetox.feature_types.OnScrollEventSubscriptionFeature
import com.flx_apps.digitaldetox.features.CommitmentPasswordFeature
import com.flx_apps.digitaldetox.features.FeaturesProvider
import com.flx_apps.digitaldetox.features.PauseButtonFeature
import com.flx_apps.digitaldetox.features.UsageStatsTracker
import com.flx_apps.digitaldetox.ui.screens.device_admin_revoked.DeviceAdminRevokedWarningActivity
import com.flx_apps.digitaldetox.system_integration.DetoxDroidAccessibilityService.Companion.instance
import com.flx_apps.digitaldetox.system_integration.DetoxDroidAccessibilityService.Companion.state
import com.flx_apps.digitaldetox.util.NotificationHelper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import timber.log.Timber

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UsageStatsSnapshotEntryPoint {
    fun repository(): UsageStatsRepository
}

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

        /**
         * Ensures the logging exception handler is only chained once per process, no matter how
         * often the service is recreated.
         */
        private var exceptionHandlerInstalled = false
    }

    private var lastPackage = ""

    /**
     * Class-name prefixes of transient system surfaces (keyboard, volume dialog, recents) whose
     * window events must not be treated as "an app was opened".
     */
    private val ignoredEventClassPrefixes = listOf(
        "android.inputmethodservice.SoftInputWindow",
        "com.android.systemui.volume",
        "com.android.quickstep.RecentsActivity"
    )
    private var ignoredPackages = mutableSetOf<String>()
    private var screenTurnedOffReceiver = ScreenTurnedOffReceiver()
    private val commitmentPasswordTamperGuard by lazy {
        CommitmentPasswordTamperGuard(this)
    }

    var onKeyEventListener: ((KeyEvent) -> Boolean)? = null

    /**
     * The view model/logic holder for the [DetoxDroidAccessibilityService].
     * (Note: AccessibilityService is not a typical place for Hilt injection, but if you need dependencies
     * you might need to use @AndroidEntryPoint on the Service)
     */

    override fun onCreate() {
        Timber.i("DetoxDroidAccessibilityService: onCreate")
        super.onCreate()
        instance = this

        // Hook into uncaught exceptions (once per process — service recreation must not chain
        // another wrapper around the previous one)
        if (!exceptionHandlerInstalled) {
            exceptionHandlerInstalled = true
            val defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                Timber.e(
                    throwable,
                    "DetoxDroidAccessibilityService: Uncaught Exception on thread ${thread.name}"
                )
                defaultExceptionHandler?.uncaughtException(thread, throwable)
            }
        }

        val intentFilter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenTurnedOffReceiver, intentFilter)

        // add all known keyboard packages to list of apps where we will not interfere with grayscale / color settings
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).enabledInputMethodList.forEach {
            ignoredPackages.add(it.packageName)
        }

        // call onStart() for all active features and update the state
        FeaturesProvider.activeFeatures.onEach { it.onStart(this) }
        updateState()

        UsageStatsTracker.init(this)
    }

    override fun onServiceConnected() {
        Timber.i("DetoxDroidAccessibilityService: onServiceConnected")
        super.onServiceConnected()

        startForegroundService()
    }

    /**
     * A core method of the [AccessibilityService]. It is called whenever an accessibility event
     * occurs. It forwards the event to the [FeaturesProvider.activeFeatures] for processing.
     */
    override fun onAccessibilityEvent(accessibilityEvent: AccessibilityEvent) {
        if (accessibilityEvent.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            // usage statistics are pure measurement and keep counting during a pause —
            // a pause only suspends the interventions below
            if (accessibilityEvent.source != null && accessibilityEvent.packageName != null) {
                UsageStatsTracker.onScrollEvent(accessibilityEvent)
            }
            if (PauseButtonFeature.isPausing()) return
            handleScrollEvent(accessibilityEvent)
            return
        }

        if (PauseButtonFeature.isPausing()) return

        if (accessibilityEvent.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (commitmentPasswordTamperGuard.handleTamperAttempt(accessibilityEvent)) return
            handleAppOpenedEvent(accessibilityEvent)
            return
        }
    }

    /**
     * Called when the service is interrupted. This is not used by DetoxDroid (however, [onDestroy]
     * is used to handle all cleanup operations).
     */
    override fun onInterrupt() {
        Timber.w("DetoxDroidAccessibilityService: onInterrupt")
    }

    /**
     * Encourages the system to restart the service if it is killed.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("DetoxDroidAccessibilityService: onStartCommand flags=$flags startId=$startId")
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Timber.i("DetoxDroidAccessibilityService: onTaskRemoved")
        super.onTaskRemoved(rootIntent)
    }

    override fun onLowMemory() {
        Timber.w("DetoxDroidAccessibilityService: onLowMemory")
        super.onLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        Timber.w("DetoxDroidAccessibilityService: onTrimMemory level=$level")
        super.onTrimMemory(level)
    }

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
        val packageName = accessibilityEvent.packageName?.toString()
        if (packageName == null || packageName == lastPackage) {
            // ignore events that do not contain a package name or that are triggered by the same app
            return
        }

        val className = accessibilityEvent.className?.toString().orEmpty()
        if (ignoredEventClassPrefixes.any { className.startsWith(it) } ||
            ignoredPackages.contains(packageName)
        ) {
            // ignore events that are known to be irrelevant, without treating them as an app
            // switch — otherwise returning from e.g. the volume dialog to the previous app would
            // be misdetected as a new app open
            return
        }
        lastPackage = packageName

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
     *
     * If the Commitment Password feature is active, triggers the accessibility disabled warning
     * to prevent the user from bypassing their commitment.
     */
    override fun onDestroy() {
        Timber.i("DetoxDroidAccessibilityService: onDestroy")
        super.onDestroy()

        UsageStatsTracker.shutdown()

        PauseButtonFeature.pauseFeatures(this, stop = true)
        kotlin.runCatching { unregisterReceiver(screenTurnedOffReceiver) }

        val cpRequiresWarning = kotlin.runCatching {
            CommitmentPasswordFeature.isActivated && !CommitmentPasswordFeature.isSessionUnlocked()
        }.getOrDefault(false)
        if (cpRequiresWarning) {
            DeviceAdminRevokedWarningActivity.requireAccessibilityWarning(this)
            DeviceAdminRevokedWarningActivity.launch(
                this, DeviceAdminRevokedWarningActivity.WarningReason.ACCESSIBILITY_DISABLED
            )
        }

        instance = null
        updateState()
    }

    private fun startForegroundService() {
        // Only show notification if PauseButtonFeature is activated and notifications are enabled
        if (!PauseButtonFeature.isActivated) {
            return
        }

        // Check if notifications are fully enabled (permission + channel settings)
        if (!NotificationHelper.areNotificationsEnabled(this)) {
            return
        }

        val pauseIntent = Intent(this, PauseInteractionService::class.java).apply {
            action = "com.flx_apps.digitaldetox.ACTION_PAUSE"
        }
        val pausePendingIntent = android.app.PendingIntent.getService(
            this,
            0,
            pauseIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification =
            NotificationCompat.Builder(this, DetoxDroidApplication.SERVICE_CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name_)).setContentText(
                    getString(
                        if (PauseButtonFeature.isPausing()) R.string.app_notification_paused
                        else R.string.app_notification_active
                    )
                ).setSmallIcon(R.drawable.ic_pause).setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true).addAction(
                    R.drawable.ic_pause, getString(
                        if (PauseButtonFeature.isPausing()) R.string.app_notification_action_resume
                        else R.string.app_notification_action_pause
                    ), pausePendingIntent
                ).build()

        try {
            // ID 101 is just an arbitrary constant integration ID
            startForeground(101, notification)
            Timber.i("Service moved to foreground")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start foreground service")
        }
    }

    /**
     * Updates the foreground notification (e.g., when pause state changes)
     */
    fun updateForegroundNotification() {
        if (NotificationHelper.areNotificationsEnabled(this) && PauseButtonFeature.isActivated) {
            startForegroundService()
        } else {
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
                Timber.i("Service removed from foreground due to setting change or missing permission")
            } catch (_: Exception) {
                // Ignore - service might not have been in foreground
            }
        }
    }
}
