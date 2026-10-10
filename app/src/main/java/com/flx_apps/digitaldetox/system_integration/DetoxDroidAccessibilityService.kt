package com.flx_apps.digitaldetox.system_integration

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.flx_apps.digitaldetox.DetoxDroidApplication
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.data.repository.UsageStatsRepository
import com.flx_apps.digitaldetox.feature_types.Feature
import com.flx_apps.digitaldetox.feature_types.OnAppOpenedSubscriptionFeature
import com.flx_apps.digitaldetox.feature_types.OnScrollEventSubscriptionFeature
import com.flx_apps.digitaldetox.features.CommitmentPasswordFeature
import com.flx_apps.digitaldetox.features.EmergencyBrakeFeature
import com.flx_apps.digitaldetox.features.FeaturesProvider
import com.flx_apps.digitaldetox.features.GrayscaleAppsFeature
import com.flx_apps.digitaldetox.features.PauseButtonFeature
import com.flx_apps.digitaldetox.features.UsageStatsTracker
import com.flx_apps.digitaldetox.ui.screens.device_admin_revoked.DeviceAdminRevokedWarningActivity
import com.flx_apps.digitaldetox.util.AccessibilityEventUtil
import com.flx_apps.digitaldetox.workers.ServiceReliabilityScheduler
import com.flx_apps.digitaldetox.system_integration.DetoxDroidAccessibilityService.Companion.instance
import com.flx_apps.digitaldetox.system_integration.DetoxDroidAccessibilityService.Companion.state
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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

    /**
     * Decides which app is in front, so that the features downstream all read one answer instead
     * of interpreting raw window events for themselves.
     */
    private val foregroundAppMonitor by lazy { ForegroundAppMonitor(packageName) }

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Publishes a switch that had to wait out the settle window, unless something has cancelled it
     * in the meantime.
     */
    private val settleForegroundApp = Runnable {
        foregroundAppMonitor.settle(SystemClock.uptimeMillis())?.let { dispatchForegroundApp(it) }
    }

    /**
     * The package that is currently in front ("" before the first window event). Features can
     * re-evaluate against it when they (re)start without a fresh window event, e.g. resuming from
     * a pause while the user is still inside an affected app, where no new window event will
     * arrive until the next app switch.
     */
    val currentForegroundPackage: String get() = foregroundAppMonitor.currentPackage

    /**
     * Drops the remembered foreground package, so the next window event counts as an app switch
     * even if it names the same app. Used when the screen turns off: features tear their effects
     * down for the lock screen and need a chance to put them back on the way in.
     */
    fun forgetForegroundPackage() {
        mainHandler.removeCallbacks(settleForegroundApp)
        foregroundAppMonitor.forget()
    }

    /**
     * Runs the app-opened features against whatever is in front right now instead of waiting for a
     * window event. The screen coming back on needs this: features drop what they were showing
     * while it was off, and an app that was already in the foreground when the screen went off does
     * not have to send another window event when it comes back.
     */
    fun reevaluateForegroundApp() {
        EmergencyBrakeFeature.settle(this)
        val packageName = kotlin.runCatching { rootInActiveWindow?.packageName?.toString() }
            .onFailure { Timber.w(it, "Could not read the foreground app") }
            .getOrNull().orEmpty()
        if (packageName == this.packageName) return
        val decision = foregroundAppMonitor.publishNow(
            packageName, isFullScreen = true, nowMs = SystemClock.uptimeMillis()
        )
        if (decision is ForegroundDecision.Changed) {
            mainHandler.removeCallbacks(settleForegroundApp)
            dispatchForegroundApp(decision)
        }
    }

    /**
     * The launcher passes through the foreground on the way from one app to the next under gesture
     * navigation, and the keyboards do it whenever one opens. Neither is an app the user switched
     * to.
     */
    private fun registerTransientPackages() {
        val keyboards = (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .enabledInputMethodList.map { it.packageName }
        foregroundAppMonitor.ignorePackages(keyboards)
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launchers = kotlin.runCatching {
            packageManager.queryIntentActivities(homeIntent, 0).map { it.activityInfo.packageName }
        }.onFailure { Timber.w(it, "Could not read the installed launchers") }.getOrDefault(emptyList())
        foregroundAppMonitor.settleForPackages(launchers)
    }

    private var screenTurnedOffReceiver = ScreenTurnedOffReceiver()
    private var screenTurnedOnReceiver = ScreenTurnedOnReceiver()
    private val commitmentPasswordTamperGuard by lazy {
        CommitmentPasswordTamperGuard(this)
    }

    var onKeyEventListener: ((KeyEvent) -> Boolean)? = null
        set(value) {
            field = value
            updateKeyEventFiltering()
        }

    /**
     * Requests hardware-key filtering only while it is actually needed — a configured pause key,
     * or the settings dialog listening for one. With the flag set, *every* hardware key press on
     * the device does a binder round-trip through [onKeyEvent], so it must not stay on for the
     * majority of users who never configure a key. The flag is declared in the service XML (so a
     * failure here degrades to today's behavior) and switched off as soon as the service connects.
     */
    fun updateKeyEventFiltering() {
        val info = serviceInfo ?: return
        val needed =
            onKeyEventListener != null || PauseButtonFeature.hardwareKey != KeyEvent.KEYCODE_UNKNOWN
        val hasFlag =
            info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS != 0
        if (needed == hasFlag) return
        info.flags = if (needed) {
            info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        } else {
            info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
        }
        kotlin.runCatching { serviceInfo = info }
            .onFailure { Timber.w(it, "Could not update key event filtering") }
    }

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

        registerReceiver(screenTurnedOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        registerReceiver(screenTurnedOnReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        })

        registerTransientPackages()

        // a brake outlives the process, and the features below start under it
        EmergencyBrakeFeature.restore(this)

        // Reload before starting anything, so the periodic reload cannot take a feature that is
        // being started here for one whose schedule just opened and start it a second time.
        // onStart is not idempotent everywhere: DoNotDisturbFeature remembers the interruption
        // filter it finds, and a second run would remember its own.
        FeaturesProvider.reloadActiveFeatures()
        FeaturesProvider.activeFeatures.forEach { it.onStart(this) }
        EmergencyBrakeFeature.reapply(this)
        // Only a feature that is starting gets to hand the system's display settings back, and a
        // feature outside its schedule never starts. The daltonizer and extra dim are secure
        // settings, so they survive a reboot, and so does our record of having set them: without
        // this the screen stays gray until the schedule next opens and closes again.
        if (!FeaturesProvider.activeFeatures.contains(GrayscaleAppsFeature)) {
            runCatching { GrayscaleAppsFeature.restoreSystemFilters(this) }.onFailure {
                Timber.w(it, "Could not restore the display filters on service start")
            }
        }
        updateState()

        UsageStatsTracker.init(this)
    }

    override fun onServiceConnected() {
        Timber.i("DetoxDroidAccessibilityService: onServiceConnected")
        super.onServiceConnected()

        updateKeyEventFiltering()
        updateForegroundNotification()
        // The watchdog is only scheduled while there is something to watch, and this is the one
        // place every way of switching DetoxDroid on passes through, including the system settings.
        ServiceReliabilityScheduler.schedule(this)
        ServiceReliabilityScheduler.scheduleNextScheduleBoundary(this)
        DetoxDroidDeviceAdminReceiver.allowSystemBackups(this)
    }

    /**
     * A core method of the [AccessibilityService]. It is called whenever an accessibility event
     * occurs. It forwards the event to the [FeaturesProvider.activeFeatures] for processing.
     */
    override fun onAccessibilityEvent(accessibilityEvent: AccessibilityEvent) {
        if (accessibilityEvent.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            if (AccessibilityEventUtil.isTextFieldScrollEvent(accessibilityEvent)) return
            // usage statistics are pure measurement and keep counting during a pause —
            // a pause only suspends the interventions below
            if (accessibilityEvent.source != null && accessibilityEvent.packageName != null) {
                UsageStatsTracker.onScrollEvent(accessibilityEvent)
            }
            // per-feature pause gating happens inside handleScrollEvent
            handleScrollEvent(accessibilityEvent)
            return
        }

        if (accessibilityEvent.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // the tamper guard stays disabled during a pause, as before; per-feature pause gating
            // for the interventions happens inside handleAppOpenedEvent
            if (!PauseButtonFeature.isPausing() &&
                commitmentPasswordTamperGuard.handleTamperAttempt(accessibilityEvent)
            ) return
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
        // The timer for the end of a brake does not run while the phone sleeps, so the end is
        // caught here. Before the monitor hears of the new window: releasing the brake re-evaluates
        // the app that was in front, and the new one then arrives once, with its own event.
        EmergencyBrakeFeature.settle(this)
        val signal = WindowSignal(
            packageName = accessibilityEvent.packageName?.toString().orEmpty(),
            className = accessibilityEvent.className?.toString().orEmpty(),
            isFullScreen = accessibilityEvent.isFullScreen,
        )
        when (val decision = foregroundAppMonitor.onWindow(signal, SystemClock.uptimeMillis())) {
            ForegroundDecision.Unchanged -> {}

            ForegroundDecision.Cancelled -> mainHandler.removeCallbacks(settleForegroundApp)

            is ForegroundDecision.Changed -> {
                mainHandler.removeCallbacks(settleForegroundApp)
                dispatchAppOpened(decision.packageName, accessibilityEvent)
            }

            is ForegroundDecision.Settling -> {
                mainHandler.removeCallbacks(settleForegroundApp)
                mainHandler.postDelayed(settleForegroundApp, decision.delayMs)
            }
        }
    }

    /**
     * Hands a settled foreground app to the features. The window event it came from is long gone by
     * then, so the features get one that carries what they read off it.
     */
    private fun dispatchForegroundApp(decision: ForegroundDecision.Changed) {
        dispatchAppOpened(
            decision.packageName,
            AccessibilityEventUtil.createEvent(isFullScreen = decision.isFullScreen)
        )
    }

    /**
     * Hands an app that came to the front to the active [OnAppOpenedSubscriptionFeature]s, minus
     * the ones a running pause suspends.
     */
    private fun dispatchAppOpened(packageName: String, accessibilityEvent: AccessibilityEvent) {
        // usage statistics are pure measurement and keep counting during a pause —
        // a pause only suspends the interventions below
        UsageStatsTracker.onForegroundApp(packageName)
        FeaturesProvider.activeFeatures.intersect(FeaturesProvider.onAppOpenedFeatures).forEach {
            if (PauseButtonFeature.isFeaturePaused(it as Feature)) return@forEach
            (it as OnAppOpenedSubscriptionFeature).onAppOpened(
                this, packageName, accessibilityEvent
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
        if (accessibilityEvent.source == null || scrollViewSize == -1 ||
            accessibilityEvent.className == null || accessibilityEvent.packageName == null
        ) {
            return
        }

        val scrollViewId =
            OnScrollEventSubscriptionFeature.calculateScrollViewId(accessibilityEvent)

        FeaturesProvider.activeFeatures.intersect(FeaturesProvider.onScrollEventFeatures).forEach {
            if (PauseButtonFeature.isFeaturePaused(it as Feature)) return@forEach
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

        mainHandler.removeCallbacks(settleForegroundApp)
        PauseButtonFeature.pauseFeatures(this, stop = true)
        kotlin.runCatching { unregisterReceiver(screenTurnedOffReceiver) }
        kotlin.runCatching { unregisterReceiver(screenTurnedOnReceiver) }

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
        val isPausing = PauseButtonFeature.isPausing()
        val isBraking = EmergencyBrakeFeature.isBraking()
        val builder = NotificationCompat.Builder(this, DetoxDroidApplication.SERVICE_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_displayName))
            .setContentText(
                if (isBraking) {
                    getString(
                        R.string.home_state_braking, EmergencyBrakeFeature.endsAtText()
                    )
                } else if (isPausing) {
                    // show the actual end of the pause instead of a bare "Paused"
                    getString(
                        R.string.home_state_paused,
                        Instant.ofEpochMilli(PauseButtonFeature.pauseUntil)
                            .atZone(ZoneId.systemDefault()).toLocalTime()
                            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
                    )
                } else getString(R.string.app_notification_active)
            )
            .setSmallIcon(R.drawable.ic_pause)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
        if (isBraking || isPausing) {
            // live countdown to the end of the brake or the pause in the collapsed notification
            builder.setWhen(
                if (isBraking) EmergencyBrakeFeature.endsAtWallClock() else PauseButtonFeature.pauseUntil
            )
            builder.setUsesChronometer(true)
            builder.setChronometerCountDown(true)
        }
        if (EmergencyBrakeFeature.canUndo()) {
            builder.addAction(
                R.drawable.ic_brake,
                getString(R.string.feature_brake_undo),
                EmergencyBrakeReceiver.undoIntent(this)
            )
        }
        // Only offer the pause/resume action when the Pause feature is actually enabled, and not
        // under a brake, which refuses a pause.
        if (PauseButtonFeature.isActivated && !isBraking) {
            val pauseIntent = Intent(this, PauseInteractionService::class.java).apply {
                action = "com.flx_apps.digitaldetox.ACTION_PAUSE"
            }
            val pausePendingIntent = android.app.PendingIntent.getService(
                this,
                0,
                pauseIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_pause,
                getString(
                    if (isPausing) R.string.app_notification_action_resume
                    else R.string.app_notification_action_pause
                ),
                pausePendingIntent
            )
        }

        val notification: Notification = builder.build()
        try {
            // ID 101 is an arbitrary constant; the specialUse type matches the manifest declaration
            // (required on targetSdk >= 34, ignored on older devices).
            ServiceCompat.startForeground(
                this, 101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
            Timber.i("Service moved to foreground")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start foreground service")
        }
    }

    /**
     * Updates the foreground notification (e.g., when pause state changes)
     */
    fun updateForegroundNotification() {
        // Run as a foreground service only when the user opted into the keepalive, or while a pause
        // is active (the pause notification needs it), or a brake. Otherwise stay a plain bound
        // service.
        if (ReliabilitySettings.keepServiceAliveEnabled || PauseButtonFeature.isActivated ||
            EmergencyBrakeFeature.isBraking()
        ) {
            startForegroundService()
        } else {
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        }
    }
}
