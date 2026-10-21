package com.flx_apps.digitaldetox.system_integration

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Browser
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.flx_apps.digitaldetox.DetoxDroidApplication
import com.flx_apps.digitaldetox.MainActivity
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
import com.flx_apps.digitaldetox.rules.RuleGroups
import com.flx_apps.digitaldetox.rules.Rules
import com.flx_apps.digitaldetox.ui.screens.device_admin_revoked.DeviceAdminRevokedWarningActivity
import com.flx_apps.digitaldetox.ui.screens.usage_stats.UsageStatsSettings
import com.flx_apps.digitaldetox.util.AccessibilityEventUtil
import com.flx_apps.digitaldetox.util.appLabel
import com.flx_apps.digitaldetox.util.toHrMinString
import com.flx_apps.digitaldetox.websites.BrowserAddressBars
import com.flx_apps.digitaldetox.websites.StrandedPages
import com.flx_apps.digitaldetox.websites.WebPage
import com.flx_apps.digitaldetox.websites.WebsiteCollections
import com.flx_apps.digitaldetox.websites.WebsiteMonitor
import com.flx_apps.digitaldetox.websites.Websites
import com.flx_apps.digitaldetox.workers.ServiceReliabilityScheduler
import com.flx_apps.digitaldetox.system_integration.DetoxDroidAccessibilityService.Companion.instance
import com.flx_apps.digitaldetox.system_integration.DetoxDroidAccessibilityService.Companion.state
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

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
        /** How many of the most used apps the expanded notification names. */
        private const val NOTIFICATION_APP_COUNT = 3

        /**
         * How long after going back from a website its page is judged again. Long enough for the
         * browser to have shown the page before, short enough that a site that is held back as
         * well does not get to show much of itself.
         */
        private const val LEAVE_WEBSITE_RECHECK_MS = 600L

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
     * The package that is currently in front ("" before the first window event). Features that
     * re-evaluate when they (re)start without a fresh window event read [foregroundFor] instead,
     * which knows about websites.
     */
    val currentForegroundPackage: String get() = foregroundAppMonitor.currentPackage

    /**
     * The page of the browser in front, for the features that have websites in their lists.
     */
    private val websiteMonitor by lazy {
        WebsiteMonitor(
            addressBarIds = { BrowserAddressBars.idsFor(this, it) },
            readRoot = { rootInActiveWindow },
            onPageChanged = ::dispatchWebsiteChange
        )
    }

    /** The websites going back could not leave, see [leaveWebsite]. */
    private val strandedPages = StrandedPages()

    /**
     * What the features were last told is in front: an app, or for a website the entry the groups
     * list it under (see [Rules.Foreground.subject]). A new page only reaches the features when it
     * changes this.
     */
    private var dispatchedSubject: String? = null

    /**
     * What [feature] is told is in front right now, for a feature that re-evaluates without a fresh
     * window event (resuming from a pause, a brake starting): the subject the groups resolved for
     * the last app switch, or the app itself before any.
     */
    fun foregroundFor(@Suppress("UNUSED_PARAMETER") feature: Feature): String =
        Rules.foreground?.subject ?: foregroundAppMonitor.currentPackage

    /** The page the browser in front shows, or null when no readable browser is in front. */
    private fun pageInFront(app: String): WebPage? =
        websiteMonitor.page?.takeIf { app == websiteMonitor.browserPackage }

    /**
     * The site the browser in front shows, the way it would be typed ("youtube.com"), or null when
     * no browser DetoxDroid can read is in front. For naming a website that one of DetoxDroid's
     * screens holds back where the list only says "Porn sites".
     */
    fun currentWebsiteName(): String? {
        if (foregroundAppMonitor.currentPackage != websiteMonitor.browserPackage) return null
        return websiteMonitor.page?.host?.removePrefix("www.")
    }

    /**
     * Goes back in the browser in front, the way out of a website one of DetoxDroid's screens held
     * back. Going home would leave the browser on that page, and opening the browser again would
     * end on the same screen. It goes back once per call and never on its own: if the page before
     * is held back as well, it gets its screen again, and the user decides again.
     *
     * A page that going back could not leave last time, because its tab had nothing to go back to
     * (see [StrandedPages]), gets an empty page put in front of it instead.
     */
    fun leaveWebsite() {
        if (dispatchedSubject?.let(Websites::isWebsite) == true) dispatchedSubject = null
        val browser = websiteMonitor.browserPackage
        val page = websiteMonitor.page
        if (browser != null && page != null) {
            if (!strandedPages.isStranded(browser, page) || !openEmptyPage(browser)) {
                strandedPages.onWentBack(browser, page, SystemClock.elapsedRealtime())
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
        // the page before can be on the same site and leave the address bar as it was
        websiteMonitor.recheck(LEAVE_WEBSITE_RECHECK_MS)
    }

    /**
     * Opens an empty page in [browser], in front of the page it shows. Chrome opens it in a tab of
     * its own, marked as DetoxDroid's, and reuses that tab the next time instead of adding another.
     * The tab the user was in stays as it was: no app can navigate it from outside. False when the
     * browser does not open the page.
     */
    private fun openEmptyPage(browser: String): Boolean = runCatching {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("about:blank"))
                .setPackage(browser)
                .putExtra(Browser.EXTRA_APPLICATION_ID, packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure { Timber.w(it, "%s did not open an empty page", browser) }.isSuccess

    /**
     * Drops the remembered foreground package, so the next window event counts as an app switch
     * even if it names the same app. Used when the screen turns off: features tear their effects
     * down for the lock screen and need a chance to put them back on the way in.
     */
    fun forgetForegroundPackage() {
        mainHandler.removeCallbacks(settleForegroundApp)
        foregroundAppMonitor.forget()
        websiteMonitor.reset()
        dispatchedSubject = null
        updateWebsiteEvents(false)
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
        ForceStopRecovery.recordRunning(this, true)
        DetoxDroidDeviceAdminReceiver.allowSystemBackups(this)
    }

    /**
     * A core method of the [AccessibilityService]. It is called whenever an accessibility event
     * occurs. It forwards the event to the [FeaturesProvider.activeFeatures] for processing.
     */
    override fun onAccessibilityEvent(accessibilityEvent: AccessibilityEvent) {
        if (accessibilityEvent.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            // only asked for while a browser is in front, see updateWebsiteEvents
            websiteMonitor.onContentChanged(accessibilityEvent.packageName)
            return
        }
        if (accessibilityEvent.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            // every read of the source hands out a fresh copy of the node, and scrolling sends up
            // to ten events a second, so it is read once here and recycled (which still frees
            // native memory below API 33). Without a source the event says too little to count.
            val source = accessibilityEvent.source ?: return
            val isTextField = AccessibilityEventUtil.isTextFieldScrollEvent(source)
            val viewIdResourceName = source.viewIdResourceName
            source.recycle()
            if (isTextField) return
            // usage statistics are pure measurement and keep counting during a pause —
            // a pause only suspends the interventions below
            if (accessibilityEvent.packageName != null) {
                UsageStatsTracker.onScrollEvent(accessibilityEvent)
            }
            // per-feature pause gating happens inside handleScrollEvent
            handleScrollEvent(accessibilityEvent, viewIdResourceName)
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
     * the ones a running pause suspends. For a browser, each feature hears about the website it
     * shows where that is in its list, see [foregroundFor].
     */
    private fun dispatchAppOpened(packageName: String, accessibilityEvent: AccessibilityEvent) {
        // usage statistics are pure measurement and keep counting during a pause —
        // a pause only suspends the interventions below. They count apps, never websites.
        UsageStatsTracker.onForegroundApp(packageName)
        // the app that was left has its time booked now, and going home is when the widget is seen
        ScreenTimeSurfaces.requestRefresh(this)
        strandedPages.onAppSwitch(packageName, SystemClock.elapsedRealtime())
        watchWebsites(packageName)
        websiteMonitor.browserPackage?.let { strandedPages.onPageShown(it, websiteMonitor.page) }
        UsageStatsTracker.onWebsite(countedSite(packageName))
        val foreground = Rules.onForeground(packageName, pageInFront(packageName))
        dispatchedSubject = foreground.subject
        dispatchToFeatures(foreground.subject, accessibilityEvent)
    }

    /** Hands [subject] to the active [OnAppOpenedSubscriptionFeature]s a pause does not suspend. */
    private fun dispatchToFeatures(subject: String, accessibilityEvent: AccessibilityEvent) {
        FeaturesProvider.activeFeatures.intersect(FeaturesProvider.onAppOpenedFeatures).forEach {
            if (PauseButtonFeature.isFeaturePaused(it as Feature)) return@forEach
            (it as OnAppOpenedSubscriptionFeature).onAppOpened(this, subject, accessibilityEvent)
        }
    }

    /**
     * Judges the app in front again without a window event: a budget ran out, a group's window
     * opened or closed, a group was edited, a pause ended. The budgets and the features see it as
     * if the app had just come to the front, the usage statistics do not.
     */
    fun redispatchForegroundApp() {
        val app = foregroundAppMonitor.currentPackage
        if (app.isEmpty()) return
        watchWebsites(app)
        val foreground = Rules.onForeground(app, pageInFront(app))
        dispatchedSubject = foreground.subject
        dispatchToFeatures(foreground.subject, AccessibilityEventUtil.createEvent(isFullScreen = true))
    }

    /**
     * The browser in front shows another page. The budgets hear of it either way; the features
     * only when it changes what they are told is in front, otherwise they are still in the same
     * browser.
     */
    private fun dispatchWebsiteChange() {
        val app = foregroundAppMonitor.currentPackage
        if (app != websiteMonitor.browserPackage) return
        strandedPages.onPageShown(app, websiteMonitor.page)
        UsageStatsTracker.onWebsite(countedSite(app))
        val foreground = Rules.onForeground(app, websiteMonitor.page)
        if (dispatchedSubject == foreground.subject) return
        dispatchedSubject = foreground.subject
        dispatchToFeatures(foreground.subject, AccessibilityEventUtil.createEvent(isFullScreen = true))
    }

    /**
     * The site the browser [app] shows, for the time on websites in the usage stats, or null when
     * [app] shows none or the user does not have websites counted.
     */
    private fun countedSite(app: String): String? {
        if (!UsageStatsSettings.countWebsites || app != websiteMonitor.browserPackage) return null
        return websiteMonitor.page?.let(Websites::siteOf)
    }

    /**
     * Follows the websites of [app] when it is a browser and websites matter to some active group
     * (see [Websites.matterTo]), or the user has the time on them counted. Otherwise the address
     * bar is never read and no content change is asked for.
     */
    private fun watchWebsites(app: String) {
        val groups = RuleGroups.active().filter(Websites::matterTo)
        val collections = groups
            .flatMap { it.appExceptions.mapNotNull(Websites::collectionOf) }
            .toSet()
        if (collections.isNotEmpty()) {
            // a collection that is still loading cannot match yet, so the page is judged again
            // once it is in
            WebsiteCollections.prefetch(collections) {
                mainHandler.post { websiteMonitor.recheck(0L) }
            }
        }
        websiteMonitor.onForegroundApp(
            app, watch = groups.isNotEmpty() || UsageStatsSettings.countWebsites
        )
        updateWebsiteEvents(websiteMonitor.browserPackage != null)
    }

    /**
     * Asks for window content changes only while a browser whose websites matter is in front. They
     * are by far the busiest event type, and nothing else here needs them. Not declared in the
     * service XML for that reason: if this fails, websites go unrecognised and nothing else changes.
     */
    private fun updateWebsiteEvents(needed: Boolean) {
        val info = serviceInfo ?: return
        val contentChanged = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        if ((info.eventTypes and contentChanged != 0) == needed) return
        info.eventTypes = if (needed) {
            info.eventTypes or contentChanged
        } else {
            info.eventTypes and contentChanged.inv()
        }
        kotlin.runCatching { serviceInfo = info }
            .onFailure { Timber.w(it, "Could not update the content change events") }
    }

    /**
     * Called when a scroll event is detected. It does some calculations to detect whether the event
     * is relevant and does some preprocessing before forwarding it to the intersection of
     * [FeaturesProvider.activeFeatures] and [FeaturesProvider.onScrollEventFeatures].
     */
    private fun handleScrollEvent(
        accessibilityEvent: AccessibilityEvent, viewIdResourceName: String?
    ) {
        val scrollViewSize = when {
            accessibilityEvent.itemCount > 0 -> accessibilityEvent.itemCount
            else -> accessibilityEvent.maxScrollY
        }

        // dispose scroll events that contain too less information
        if (scrollViewSize == -1 ||
            accessibilityEvent.className == null || accessibilityEvent.packageName == null
        ) {
            return
        }

        val scrollViewId =
            OnScrollEventSubscriptionFeature.calculateScrollViewId(
                accessibilityEvent, viewIdResourceName
            )

        val app = accessibilityEvent.packageName.toString()
        // a scroll in a browser is a scroll in the website the groups list it under, like a switch
        val subject = if (app == foregroundAppMonitor.currentPackage) {
            dispatchedSubject ?: app
        } else {
            app
        }
        FeaturesProvider.activeFeatures.intersect(FeaturesProvider.onScrollEventFeatures).forEach {
            if (PauseButtonFeature.isFeaturePaused(it as Feature)) return@forEach
            (it as OnScrollEventSubscriptionFeature).onScrollEvent(
                this,
                subject,
                scrollViewId,
                scrollViewSize = scrollViewSize,
                viewIdResourceName = viewIdResourceName,
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
        ForceStopRecovery.recordRunning(this, false)

        mainHandler.removeCallbacks(settleForegroundApp)
        // nothing is in front of a service that is gone, so the budgets stop counting
        Rules.onNothingInFront()
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

    private var isInForeground = false

    private fun startForegroundService() {
        val isPausing = PauseButtonFeature.isPausing()
        val isBraking = EmergencyBrakeFeature.isBraking()
        val stateText = if (isBraking) {
            getString(R.string.home_state_braking, EmergencyBrakeFeature.endsAtText())
        } else if (isPausing) {
            // show the actual end of the pause instead of a bare "Paused"
            getString(R.string.home_state_paused, PauseButtonFeature.endsAtText())
        } else getString(R.string.app_notification_active)
        // Reading the usage records blocks, so they are read off the main thread and the refresh
        // comes back through updateForegroundNotification(). Until then the title is the app name.
        val today = ScreenTimeSurfaces.today
        if (today == null) ScreenTimeSurfaces.refreshIfStale(this)
        val title = today?.let {
            getString(
                R.string.app_notification_screenTime,
                it.screenTimeMs.milliseconds.toHrMinString(this)
            )
        } ?: getString(R.string.app_displayName)

        fun NotificationCompat.Builder.withStatus() = apply {
            setContentTitle(title)
            setContentText(stateText)
            setSmallIcon(R.drawable.ic_pause)
            // the timestamp of the last refresh says nothing, a countdown does
            setShowWhen(isBraking || isPausing)
            if (isBraking || isPausing) {
                // live countdown to the end of the brake or the pause in the collapsed notification
                setWhen(
                    if (isBraking) EmergencyBrakeFeature.endsAtWallClock()
                    else PauseButtonFeature.pauseUntil
                )
                setUsesChronometer(true)
                setChronometerCountDown(true)
            }
        }

        val builder = NotificationCompat.Builder(this, DetoxDroidApplication.SERVICE_CHANNEL_ID)
            .withStatus()
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            // the title changes with every refresh, which must not make a sound each time
            .setOnlyAlertOnce(true)
            .setContentIntent(MainActivity.openUsageStatsIntent(this))
            // a lock screen that hides notification content still gets the total and the state,
            // just not which apps the time went to
            .setPublicVersion(
                NotificationCompat.Builder(this, DetoxDroidApplication.SERVICE_CHANNEL_ID)
                    .withStatus().build()
            )
        today?.let { notificationDetails(it) }?.let { details ->
            builder.setStyle(NotificationCompat.BigTextStyle().bigText("$stateText\n$details"))
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
            // the screen time refreshes repost the notification every minute or so
            if (!isInForeground) Timber.i("Service moved to foreground")
            isInForeground = true
        } catch (e: Exception) {
            Timber.e(e, "Failed to start foreground service")
        }
    }

    /**
     * What the expanded notification adds below the state: what is left of the budgets, and where
     * the time went. Null when there is nothing to add.
     */
    private fun notificationDetails(today: TodaySummary): String? {
        val lines = buildList {
            today.budgets.forEach {
                add(
                    getString(
                        R.string.app_notification_budget,
                        it.name,
                        it.leftText(this@DetoxDroidAccessibilityService)
                    )
                )
            }
            val apps = today.topApps.take(NOTIFICATION_APP_COUNT)
            if (apps.isNotEmpty()) {
                add(getString(R.string.app_notification_mostUsed, apps.joinToString(" · ") {
                    appLabel(it.packageName) + " " + it.screenTimeMs.milliseconds.toHrMinString(
                        this@DetoxDroidAccessibilityService
                    )
                }))
            }
        }
        return lines.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    /**
     * Whether the service runs in the foreground with its notification: when the user opted into
     * the keepalive, while the Pause feature is on (its notification carries the pause action), or
     * under a brake. Otherwise it stays a plain bound service.
     */
    val showsNotification: Boolean
        get() = ReliabilitySettings.keepServiceAliveEnabled || PauseButtonFeature.isActivated ||
                EmergencyBrakeFeature.isBraking()

    /**
     * Updates the foreground notification (e.g., when pause state changes)
     */
    fun updateForegroundNotification() {
        if (showsNotification) {
            startForegroundService()
        } else {
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            isInForeground = false
        }
    }
}
