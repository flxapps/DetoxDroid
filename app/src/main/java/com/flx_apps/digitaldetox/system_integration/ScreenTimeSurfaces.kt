package com.flx_apps.digitaldetox.system_integration

import android.app.AppOpsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import com.flx_apps.digitaldetox.data.DataStore
import com.flx_apps.digitaldetox.features.DisableAppsFeature
import com.flx_apps.digitaldetox.util.UsageAccessUtil
import com.flx_apps.digitaldetox.widgets.minimal_launcher.MinimalLauncherWidgetProvider
import com.flx_apps.digitaldetox.widgets.onLocaleOrDensityChanged
import com.flx_apps.digitaldetox.widgets.today.TodayWidgetProvider
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDate

/**
 * Keeps today's screen time current where it shows outside the app: the screen time widget, the
 * service notification, and the launcher widgets that show the time in each app or dim the
 * blocked ones. A refresh reads the system's records once and hands the result to whichever of
 * them is showing.
 *
 * Neither can keep itself current. The widget host updates every 30 minutes at best, and polling
 * the usage records on a timer costs battery for numbers nobody is looking at. The records only
 * change when an app is left anyway: the system books an app's time when it goes to the
 * background, not while it is in front. So refreshes follow those moments, an app switch and the
 * screen going off, plus the screen coming back on into another day. What the surfaces show
 * besides the records (the apps left out, the budgets, what is blocked, whether DetoxDroid runs,
 * usage access, the clock, the language) is followed from [start] on, and a change there shows at
 * once, see [invalidate].
 *
 * Scheduling happens on the main thread only, so [pending] needs no lock. The refreshes themselves
 * run one at a time behind [lock].
 */
object ScreenTimeSurfaces {
    /** Both count in whole minutes, so refreshing more often buys nothing. */
    private const val MIN_INTERVAL_MS = 60_000L

    /**
     * The system books the time of the app that was left when it pauses, which can arrive a little
     * after the window of the next app does.
     */
    private const val SWITCH_SETTLE_MS = 1_000L

    /** With the screen going off, the app in front pauses only once the lock screen is up. */
    private const val SCREEN_OFF_SETTLE_MS = 2_000L

    /** Short enough to read as at once, long enough for a few toggles in a row to draw once. */
    private const val CHANGE_SETTLE_MS = 300L

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main +
                CoroutineExceptionHandler { _, e -> Timber.e(e, "Screen time refresh failed") }
    )
    private val lock = Mutex()

    /** The refresh that is waiting to run, if any. Main thread only. */
    private var pending: Job? = null

    @Volatile
    private var lastRefreshElapsedMs = -MIN_INTERVAL_MS

    /**
     * Counts the changes to what a reading is built from besides the usage records. A reading
     * taken under an older count no longer holds. Written on the main thread only.
     */
    @Volatile
    private var generation = 0

    /**
     * A summary, whether it is detailed (see [TodaySummary.read]), which only the screen time
     * widget needs, and the [generation] it was read under.
     */
    private class Reading(val summary: TodaySummary, val detailed: Boolean, val generation: Int)

    @Volatile
    private var latest: Reading? = null

    /** The summary read last, as long as it still holds and is about today. */
    val today: TodaySummary?
        get() = latest?.takeIf { it.generation == generation }?.summary
            ?.takeIf { it.date == LocalDate.now() }

    /**
     * Draws the surfaces for this process and follows, for as long as it lives, what the numbers
     * are built from besides the usage records. Main thread only, once.
     */
    fun start(context: Context) {
        val appContext = context.applicationContext
        requestRefresh(appContext)
        // the apps left out, the budgets and what is blocked, from whichever screen, wizard or
        // switch they change
        scope.launch {
            DataStore.data
                .map { TodaySummary.Settings.current() to DisableAppsFeature.blockedApps() }
                .distinctUntilChanged()
                .drop(1)
                .collect { invalidate(appContext) }
        }
        // DetoxDroid starting, stopping or pausing: the widget says when it is off, and nothing
        // is blocked while it is off or paused
        scope.launch {
            DetoxDroidAccessibilityService.state.drop(1).collect { invalidate(appContext) }
        }
        // With usage access the numbers come from the system's records, and only those know the
        // unlocks. The watcher also fires for other apps and other permissions, hence the compare.
        var hadUsageAccess = UsageAccessUtil.hasUsageAccess(appContext)
        runCatching {
            appContext.getSystemService(AppOpsManager::class.java)?.startWatchingMode(
                AppOpsManager.OPSTR_GET_USAGE_STATS, appContext.packageName
            ) { _, _ ->
                scope.launch {
                    val hasUsageAccess = UsageAccessUtil.hasUsageAccess(appContext)
                    if (hasUsageAccess == hadUsageAccess) return@launch
                    hadUsageAccess = hasUsageAccess
                    invalidate(appContext)
                }
            }
        }.onFailure { Timber.w(it, "Cannot follow usage access, the next app switch will") }
        // a new day, or the day starting at another moment
        appContext.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = invalidate(appContext)
        }, IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })
        // names and units are worded for one language, and the scroll distance is measured in
        // pixels of one density
        appContext.onLocaleOrDensityChanged { invalidate(appContext) }
    }

    /**
     * Asks for a refresh. Requests coalesce: one runs at most every [MIN_INTERVAL_MS], and a
     * request that arrives while one waits is folded into it rather than dropped. Main thread
     * only.
     */
    fun requestRefresh(context: Context) {
        if (pending != null) return
        val waitMs = lastRefreshElapsedMs + MIN_INTERVAL_MS - SystemClock.elapsedRealtime()
        schedule(context, waitMs.coerceAtLeast(SWITCH_SETTLE_MS))
    }

    /**
     * The screen going off ends a stretch of use, and whatever shows on the lock screen should
     * include it, so this one is not held back by the interval. Main thread only.
     */
    fun refreshAfterScreenOff(context: Context) {
        pending?.cancel()
        schedule(context, SCREEN_OFF_SETTLE_MS)
    }

    /**
     * An unlock leads to the home screen, whose widget counts the unlocks, so this one is not held
     * back by the interval either. Main thread only.
     */
    fun refreshAfterUnlock(context: Context) {
        pending?.cancel()
        schedule(context, SWITCH_SETTLE_MS)
    }

    /**
     * Refreshes now when there is nothing current to show: the screen coming on into another
     * day, or the notification appearing. Main thread only.
     */
    fun refreshIfStale(context: Context) {
        if (today != null) return
        pending?.cancel()
        schedule(context, 0L)
    }

    /**
     * Something the numbers are built from besides the usage records changed. The last reading
     * no longer holds, and the surfaces get new numbers right away rather than with the next app
     * switch. Main thread only.
     */
    fun invalidate(context: Context) {
        generation++
        pending?.cancel()
        schedule(context, CHANGE_SETTLE_MS)
    }

    private fun schedule(context: Context, delayMs: Long) {
        val appContext = context.applicationContext
        pending = scope.launch {
            delay(delayMs)
            // from here on, a new request has data this refresh may already have missed
            if (pending == coroutineContext.job) pending = null
            refresh(appContext)
        }
    }

    /** Reads today's numbers and hands them to the widgets and the notification, where shown. */
    suspend fun refresh(context: Context): Unit = withContext(Dispatchers.Default) {
        lock.withLock {
            lastRefreshElapsedMs = SystemClock.elapsedRealtime()
            val widgetIds = TodayWidgetProvider.widgetIds(context)
            val launcherIds = MinimalLauncherWidgetProvider.liveWidgetIds(context)
            val notificationShown =
                DetoxDroidAccessibilityService.instance?.showsNotification == true
            if (widgetIds.isEmpty() && launcherIds.isEmpty() && !notificationShown) {
                // nothing keeps the last reading current from here on, so nothing may show it
                latest = null
                return@withLock
            }

            // only the screen time widget shows the details, and they are the expensive part
            val summary = read(context, detailed = widgetIds.isNotEmpty()) ?: return@withLock
            if (widgetIds.isNotEmpty()) draw(context, widgetIds, summary)
            if (launcherIds.isNotEmpty()) {
                MinimalLauncherWidgetProvider.update(context, launcherIds)
            }
            if (notificationShown) {
                withContext(Dispatchers.Main) {
                    DetoxDroidAccessibilityService.instance?.updateForegroundNotification()
                }
            }
        }
    }

    /**
     * Draws the widgets with [appWidgetIds] again, for a new size or look rather than new numbers:
     * from the last reading where it has all the widget shows, from a new one otherwise.
     */
    suspend fun redraw(context: Context, appWidgetIds: IntArray): Unit =
        withContext(Dispatchers.Default) {
            lock.withLock {
                val summary = todayForWidgets()
                    ?: read(context, detailed = true)
                    ?: return@withLock
                draw(context, appWidgetIds, summary)
            }
        }

    /** What the widget shows right now, for a preview of it. Null if nothing can be read. */
    suspend fun widgetSummary(context: Context): TodaySummary? =
        todayForWidgets() ?: withContext(Dispatchers.IO) {
            runCatching { TodaySummary.read(context, detailed = true) }.getOrNull()
        }

    private fun todayForWidgets(): TodaySummary? = today?.takeIf { latest?.detailed == true }

    /** Today's numbers as last read, or read now where there are none. Null if that fails. */
    suspend fun current(context: Context): TodaySummary? =
        today ?: read(context, detailed = false)

    /** Reads and keeps today's numbers. Blocking, so on IO. */
    private suspend fun read(context: Context, detailed: Boolean): TodaySummary? =
        withContext(Dispatchers.IO) {
            // a change that lands while this reads may or may not be in it
            val generation = generation
            runCatching { TodaySummary.read(context, detailed) }
                .onSuccess { latest = Reading(it, detailed, generation) }
                .onFailure { Timber.w(it, "Could not read today's screen time") }
                .getOrNull()
        }

    private suspend fun draw(context: Context, appWidgetIds: IntArray, summary: TodaySummary) {
        runCatching { TodayWidgetProvider.update(context, appWidgetIds, summary) }
            .onFailure { Timber.w(it, "Could not update the screen time widget") }
    }
}
