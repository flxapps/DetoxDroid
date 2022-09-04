package com.flx_apps.digitaldetox.prefs

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.SpannableStringBuilder
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.ImageView
import androidx.core.text.bold
import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceViewHolder
import com.flx_apps.digitaldetox.DetoxUtil
import com.flx_apps.digitaldetox.R

/**
 * Allows the user to
 * - toggle 'Extra Dim' on and off
 *   - if the preference is toggled when the service is active, it will take effect immediately
 *   - if the preference is toggled when the service is not active, it will take effect when
 *     the service is started (either manually or via a rule)
 * - long-press to go to the 'Reduce Device Brightness' system
 *   settings to adjust the intensity of the dimness.
 * - if the service is stopped, the extra dim state will be deactivated immediately (but the
 *   preference will be preserved for the next time the service is activated)
 */
class ExtraDimPreference(
    context: Context,
    attrs: AttributeSet? = null
) : CheckBoxPreference(context, attrs) {
    private var deviceIsCurrentlyExtraDim: Boolean = false

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent?): Boolean {
                performClick()
                return true
            }

            override fun onLongPress(e: MotionEvent?) {
                if (!deviceIsCurrentlyExtraDim) {
                    return
                }

                // open device settings for 'reduce bright colors' intensity
                val intent = Intent("android.settings.REDUCE_BRIGHT_COLORS_SETTINGS")
                context.startActivity(intent)
            }
        }
    )

    init {
        isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        isCopyingEnabled = false

        if (isVisible) {
            PreferenceManager.getDefaultSharedPreferences(context)
                .registerOnSharedPreferenceChangeListener { _, key -> update(key) }

            update()
        }
    }

    private fun update(key: String? = null) {
        val prefs = Prefs_(context)
        when (key) {
            // just the default init of the extra dim state of the device
            null -> {
                // continue
            }
            // one of the shared preferences that may affect the extra dim state of the device
            prefs.isRunning.key(),
            prefs.grayscaleEnabled().key(),
            prefs.grayscaleExtraDim().key() -> {
                // continue
            }
            // other shared preferences that are irrelevant to the extra dim state of the device
            else -> return
        }

        val serviceRunning = prefs.isRunning.get()
        val newValue = prefs.grayscaleExtraDim().get() && prefs.grayscaleEnabled().get()

        if (serviceRunning) {
            DetoxUtil.setExtraDim(context, newValue)
        }

        deviceIsCurrentlyExtraDim = serviceRunning && newValue
        updateSummary()
    }

    private fun updateSummary() {
        summary = SpannableStringBuilder()
            .apply {
                if (deviceIsCurrentlyExtraDim) {
                    append(context.getString(R.string.home_grayscale_extraDim_currently_enabled_description_1))

                    append("\n")

                    bold { append(context.getString(R.string.home_grayscale_extraDim_currently_enabled_description_2)) }
                } else {
                    append(context.getString(R.string.home_grayscale_extraDim_currently_disabled_description))
                }
            }
    }

    @SuppressLint("ClickableViewAccessibility") // we perform the click in the gesture detector
    override fun onBindViewHolder(holder: PreferenceViewHolder?) {
        super.onBindViewHolder(holder)
        val icon = holder?.findViewById(android.R.id.icon) as? ImageView
        val titleView = holder?.findViewById(android.R.id.title)
        val summaryView = holder?.findViewById(android.R.id.summary)

        listOfNotNull(titleView, summaryView, icon).forEach {
            it.setOnTouchListener { _, motionEvent ->
                gestureDetector.onTouchEvent(motionEvent)
                true
            }
        }
    }
}