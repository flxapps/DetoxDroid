package com.flx_apps.digitaldetox

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import org.androidannotations.annotations.EService
import org.androidannotations.annotations.sharedpreferences.Pref
import java.util.concurrent.TimeUnit


/**
 * Creation Date: 10/14/20
 *
 * @author felix
 */
@EService
open class DigitalDetoxAccessibilityService : AccessibilityService() {
    @Pref
    lateinit var prefs: Prefs_

    private var isGrayscale = false
    private var lastPackage = ""
    private var isPausing = false

    override fun onAccessibilityEvent(accessibilityEvent: AccessibilityEvent) {
        val now = System.currentTimeMillis()
        isPausing = now < prefs.pauseUntil().get()

        if (accessibilityEvent.eventType == AccessibilityEvent.TYPE_ASSIST_READING_CONTEXT) {
            prefs.edit().pauseUntil().put(if (isPausing) -1 else now + TimeUnit.MINUTES.toMillis(1)).apply()
            AccessibilityUtil.setGrayscale(applicationContext, isPausing)
            isGrayscale = isPausing
        }

        val exceptions = setOf("android.inputmethodservice.SoftInputWindow", "com.android.systemui.volume.VolumeDialogImpl\$CustomDialog")
        if (isPausing || accessibilityEvent.packageName == lastPackage || accessibilityEvent.text.isNullOrEmpty() || accessibilityEvent.contentChangeTypes != AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED || exceptions.contains(accessibilityEvent.className)) return
        lastPackage = accessibilityEvent.packageName.toString()

        log(accessibilityEvent.toString())

        val grayscale = prefs.grayscaleEnabled().get() && !prefs.grayscaleExceptions().get().contains(accessibilityEvent.packageName)
        if (grayscale != isGrayscale) {
            AccessibilityUtil.setGrayscale(applicationContext, grayscale)
            isGrayscale = grayscale
        }
    }

    override fun onInterrupt() {}
}