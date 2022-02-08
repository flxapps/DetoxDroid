package com.flx_apps.digitaldetox

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import com.flx_apps.digitaldetox.prefs.Prefs_
import com.flx_apps.digitaldetox.prefs.TimeRule
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.androidannotations.annotations.EService
import org.androidannotations.annotations.SystemService
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit


/**
 * Creation Date: 10/14/20
 *
 * @author felix
 */
@EService
open class DetoxAccessibilityService : AccessibilityService() {
    companion object {
        @JvmStatic
        var instance: DetoxAccessibilityService? = null
    }

    lateinit var prefs: Prefs_

    @SystemService
    lateinit var notificationManager: NotificationManager

    private var lastPackage = ""
    private var isPausing = false
    private var ignoredEventClasses = mutableSetOf(
        "android.inputmethodservice.SoftInputWindow",
        "com.android.systemui.volume"
    )
    private var ignoredPackages = mutableSetOf<String>()
    private var timeRules = mutableSetOf<TimeRule>()

    data class ScrollViewInfo(var maxY: Int) {
        var added: Long = System.currentTimeMillis()
        var timesGrown = 0
        override fun toString(): String {
            return "ScrollViewInfo(maxY=$maxY, added=$added, timesGrown=$timesGrown)"
        }
    }

    /**
     * contains information about scroll views that the user has most recently used
     * the information expires, if they have not been used in the last three minutes
     */
    private val activeScrollViews: SelfExpiringHashMap<Int, ScrollViewInfo> =
        SelfExpiringHashMap(TimeUnit.SECONDS.toMillis(120))

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs_(this)
        instance = this

        // add all known keyboard packages to list of apps where we will not interfere with grayscale / color settings
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).enabledInputMethodList.forEach {
            log(it.packageName)
            ignoredPackages.add(it.packageName)
        }
        reloadTimeRules()
    }

    override fun onAccessibilityEvent(accessibilityEvent: AccessibilityEvent) {
        // check whether DetoxDroid should be inactive
        val localDateTime = LocalDateTime.now()
        var isActive = true
        timeRules.forEach {
            isActive = isActive && it.isActive(localDateTime)
            log("$it active? ${it.isActive(localDateTime)}")
        }
        if (!isActive) {
            DetoxUtil.setActive(this, false)
            return
        }

        // check whether DetoxDroid should be paused
        val now = System.currentTimeMillis()
        isPausing = now < prefs.pauseUntil().get()

        // TYPE_ASSIST_READING_CONTEXT happens when the home button is long-pressed
        if (accessibilityEvent.eventType == AccessibilityEvent.TYPE_ASSIST_READING_CONTEXT) {
            isPausing = DetoxUtil.togglePause(baseContext)
        }

        // if the "break doom scrolling" feature is activated, let's handle scroll events
        val handleScrollEvent = prefs.breakDoomScrollingEnabled().get() &&
                accessibilityEvent.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED &&
                !prefs.breakDoomScrollingExceptions().get().contains(accessibilityEvent.packageName)
        if (handleScrollEvent) {
            handleScrollEvent(accessibilityEvent)
        }

        // decide whether we want to handle this event or not
        var skipEvent = isPausing
                || ignoredPackages.contains(accessibilityEvent.packageName)
                || accessibilityEvent.packageName == lastPackage
                || (prefs.grayscaleIgnoreNonFullScreen().get() && !accessibilityEvent.isFullScreen)
                || accessibilityEvent.text.isNullOrEmpty()
                || accessibilityEvent.contentChangeTypes != AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED
        ignoredEventClasses.forEach {
            skipEvent = skipEvent || accessibilityEvent.className.contains(it)
        }
        if (skipEvent) { /*log("skip...");*/ return }

        lastPackage = accessibilityEvent.packageName.toString()

        // decide whether we want to grayscale or color the screen
        val grayscaleApp = (
                prefs.grayscaleEnabled().get() &&
                        !prefs.grayscaleExceptions().get().contains(accessibilityEvent.packageName)
                )
        DetoxUtil.setActive(this, prefs.isRunning.get(), grayscale = grayscaleApp)
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        // TODO: implement pause feature here?
        log("onKeyEvent=$event")
        return super.onKeyEvent(event)
    }

    override fun onInterrupt() {}

    public fun reloadTimeRules() {
        timeRules.clear()
        prefs.timeRules().get().forEach {
            timeRules.add(TimeRule.fromString(it))
        }
    }

    private fun handleScrollEvent(accessibilityEvent: AccessibilityEvent) {
        val maxY = when {
            accessibilityEvent.itemCount > 0 -> accessibilityEvent.itemCount
            else -> -1 // accessibilityEvent.maxScrollY + 200.toPx() // add some pixels for the benefit of the doubt
        }

        // dispose scroll events that contain too less information
        if (accessibilityEvent.source == null || maxY == -1 || accessibilityEvent.className == null) {
            return
        }

        // Generate an id from the scroll view
        // we will not use accessibilityEvent.source.hashCode(), because that generates new ids
        // for e.g. different Twitter profile feeds - we want to treat different Twitter profile
        // feeds as one though
        val scrollViewId = (
                accessibilityEvent.className.hashCode() +
                        accessibilityEvent.packageName.hashCode() +
                        accessibilityEvent.source.getBoundsInScreen(Rect()).hashCode() +
                        (accessibilityEvent.source.viewIdResourceName?.hashCode() ?: 0)
                )
//        log("${accessibilityEvent.source}")

        // if the user did not navigate over that scroll view recently, let's generate an info about
        // how much elements it initially contained
        if (!activeScrollViews.containsKey(scrollViewId)) {
            activeScrollViews[scrollViewId] = ScrollViewInfo(maxY)
        } else {
            val scrollViewInfo = activeScrollViews[scrollViewId]!!
            // determine whether this scroll view behaves like an infinite scroll view (i.e. it grows)
            var isInfinite = false
            if (maxY > scrollViewInfo.maxY) {
                scrollViewInfo.timesGrown++
                scrollViewInfo.maxY = maxY
                if (scrollViewInfo.timesGrown >= 3) {
                    isInfinite = true
                }
            }

            // calculate how long the user was active on that scroll view
            val scrollingTime =
                TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - scrollViewInfo.added)
            if (isInfinite && scrollingTime >= prefs.timeUntilDoomScrollingWarning().get()) {
                // if the scroll view is seemingly infinite and the user has been too long on it, fire the warning
                DoomScrollingBottomSheetDialog(this).show()
                activeScrollViews.clear()
            }
//            log("$accessibilityEvent")
            log("scrollingTime=$scrollingTime, maxY=$maxY, $scrollViewId → $scrollViewInfo")
        }
    }

    /**
     * BottomSheetDialog that warns the user if he has been supposedly caught in a "doom scrolling"
     * behavior
     */
    class DoomScrollingBottomSheetDialog(context: Context) : BottomSheetDialog(
        ContextThemeWrapper(
            context,
            R.style.AppTheme
        )
    ) {
        init {
            setContentView(R.layout.fragment_infinite_scroll_warning)
            window!!.setType(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            )
            setCanceledOnTouchOutside(false)
            setCancelable(false)
            findViewById<TextView>(R.id.btnClose)!!.setOnClickListener {
                dismiss()
            }
            findViewById<TextView>(R.id.btnExit)!!.setOnClickListener {
                context.startActivity(
                    Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_HOME)
                        .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                )
                dismiss()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}