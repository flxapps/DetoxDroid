package com.flx_apps.digitaldetox

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.Context
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import org.androidannotations.annotations.EService
import org.androidannotations.annotations.SystemService
import org.androidannotations.annotations.sharedpreferences.Pref


/**
 * Creation Date: 10/14/20
 *
 * @author felix
 */
@EService
open class DetoxAccessibilityService : AccessibilityService() {
    @Pref
    lateinit var prefs: Prefs_

    @SystemService
    lateinit var notificationManager: NotificationManager

    private var isGrayscale = false
    private var lastPackage = ""
    private var isPausing = false
    private var ignoredEventClasses = mutableSetOf(
        "android.inputmethodservice.SoftInputWindow",
        "com.android.systemui.volume"
    )
    private var ignoredPackages = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()

        // add all known keyboard packages to list of apps where we will not interfere with grayscale / color settings
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).enabledInputMethodList.forEach {
            log(it.packageName)
            ignoredPackages.add(it.packageName)
        }
    }

    override fun onAccessibilityEvent(accessibilityEvent: AccessibilityEvent) {
//        log("event=$accessibilityEvent")
        log("package=${accessibilityEvent.packageName}, class=${accessibilityEvent.className}, event=${accessibilityEvent.eventType}")

        val now = System.currentTimeMillis()
        isPausing = now < prefs.pauseUntil().get()

        // TYPE_ASSIST_READING_CONTEXT happens when the home button is long-pressed
        if (accessibilityEvent.eventType == AccessibilityEvent.TYPE_ASSIST_READING_CONTEXT) {
            isPausing = DetoxUtil.togglePause(baseContext)
            isGrayscale = !isPausing
        }

        // decide whether we want to handle this event or not
        var skipEvent = isPausing
                || ignoredPackages.contains(accessibilityEvent.packageName)
                || accessibilityEvent.packageName == lastPackage
                || accessibilityEvent.text.isNullOrEmpty()
                || accessibilityEvent.contentChangeTypes != AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED
        ignoredEventClasses.forEach {
            skipEvent = skipEvent || accessibilityEvent.className.contains(it)
        }
        if (skipEvent) { log("skip..."); return }

        lastPackage = accessibilityEvent.packageName.toString()

        // forcefully enable DoNotDisturb
        DetoxUtil.setZenMode(applicationContext, true)

        // decide whether we want to grayscale or color the screen
        val grayscale = prefs.grayscaleEnabled().get()
                && !prefs.grayscaleExceptions().get().contains(accessibilityEvent.packageName)
        if (grayscale != isGrayscale) { // wantedState != currentState
            DetoxUtil.setGrayscale(applicationContext, grayscale)
            isGrayscale = grayscale
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        // TODO: implement pause feature here?
        log("onKeyEvent=$event")
        return super.onKeyEvent(event)
    }

    override fun onInterrupt() {}
}