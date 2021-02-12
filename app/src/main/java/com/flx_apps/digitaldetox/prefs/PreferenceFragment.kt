package com.flx_apps.digitaldetox.prefs

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.flx_apps.digitaldetox.R
import nl.invissvenska.numberpickerpreference.NumberDialogPreference
import nl.invissvenska.numberpickerpreference.NumberPickerPreferenceDialogFragment
import org.androidannotations.annotations.EFragment
import org.androidannotations.annotations.PreferenceScreen


/**
 * Creation Date: 1/30/21
 * @author felix
 */
@EFragment
open class PreferenceFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {}

    override fun onDisplayPreferenceDialog(preference: Preference?) {
        if (preference is NumberDialogPreference) {
            val dialogFragment: DialogFragment = NumberPickerPreferenceDialogFragment
                .newInstance(
                    preference.key,
                    preference.minValue,
                    preference.maxValue,
                    preference.getStepValue(),
                    preference.unitText
                )
            dialogFragment.setTargetFragment(this, 0)
            dialogFragment.show(parentFragmentManager, "NumberPickerPreference")
            return
        }

        super.onDisplayPreferenceDialog(preference)
    }

    @EFragment
    @PreferenceScreen(R.xml.preferences_zen_mode)
    open class ZenModePreferencesFragment : PreferenceFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {}
        override fun onPreferenceTreeClick(preference: Preference?): Boolean {
            startActivity(Intent("android.settings.ZEN_MODE_PRIORITY_SETTINGS"))
            return true
        }
    }

    @EFragment
    @PreferenceScreen(R.xml.preferences_doom_scrolling)
    open class DoomScrollingPreferencesFragment : PreferenceFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {}
    }

    @EFragment
    @PreferenceScreen(R.xml.preferences_grayscale)
    open class GrayscalePreferencesFragment : PreferenceFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {}
    }

    @EFragment
    @PreferenceScreen(R.xml.preferences_pause_button)
    open class PauseButtonPreferencesFragment : PreferenceFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {}
    }
}