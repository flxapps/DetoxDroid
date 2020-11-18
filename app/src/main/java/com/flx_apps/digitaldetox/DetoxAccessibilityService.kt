package com.flx_apps.digitaldetox

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
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

    override fun onAccessibilityEvent(accessibilityEvent: AccessibilityEvent) {
        val now = System.currentTimeMillis()
        isPausing = now < prefs.pauseUntil().get()

        if (accessibilityEvent.eventType == AccessibilityEvent.TYPE_ASSIST_READING_CONTEXT) {
            isPausing = DetoxUtil.togglePause(baseContext)
            isGrayscale = !isPausing
        }

        val exceptions = setOf(
            "android.inputmethodservice.SoftInputWindow",
            "com.android.systemui.volume.VolumeDialogImpl\$CustomDialog"
        )
        if (isPausing || accessibilityEvent.packageName == lastPackage || accessibilityEvent.text.isNullOrEmpty() || accessibilityEvent.contentChangeTypes != AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED || exceptions.contains(
                accessibilityEvent.className
            )) return
        lastPackage = accessibilityEvent.packageName.toString()

        log(accessibilityEvent.toString())
        DetoxUtil.setZenMode(applicationContext, true)

        val grayscale = prefs.grayscaleEnabled().get() && !prefs.grayscaleExceptions().get().contains(
            accessibilityEvent.packageName
        )
        if (grayscale != isGrayscale) {
            DetoxUtil.setGrayscale(applicationContext, grayscale)
            isGrayscale = grayscale
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        // TODO: implement pause feature here?
        return super.onKeyEvent(event)
    }

    override fun onInterrupt() {}
}