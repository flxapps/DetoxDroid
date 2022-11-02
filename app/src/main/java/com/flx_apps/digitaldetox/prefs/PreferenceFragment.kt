package com.flx_apps.digitaldetox.prefs

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.fragment.app.DialogFragment
import androidx.preference.*
import com.flx_apps.digitaldetox.DetoxAccessibilityService
import com.flx_apps.digitaldetox.DetoxUtil
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.log
import com.takisoft.preferencex.PreferenceFragmentCompat
import nl.invissvenska.numberpickerpreference.NumberDialogPreference
import nl.invissvenska.numberpickerpreference.NumberPickerPreferenceDialogFragment
import org.androidannotations.annotations.AfterPreferences
import org.androidannotations.annotations.EFragment
import org.androidannotations.annotations.PreferenceByKey
import org.androidannotations.annotations.PreferenceScreen


/**
 * Creation Date: 1/30/21
 * @author felix
 */
@EFragment
open class PreferenceFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferencesFix(savedInstanceState: Bundle?, rootKey: String?) {}

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

    @EFragment
    @PreferenceScreen(R.xml.preferences_deactivate_apps)
    open class DeactivateAppsPreferencesFragment : PreferenceFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {}
    }

    @EFragment
    @PreferenceScreen(R.xml.preferences_time_rules)
    open class TimeRulesListPreferencesFragment : PreferenceFragment() {
        @PreferenceByKey(R.string.home_timeRules)
        lateinit var timeRulesCategory: PreferenceCategory

        @PreferenceByKey(R.string.home_timeRules_add)
        lateinit var btnAddRule: Preference

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {}

        @AfterPreferences
        fun init() {
            refreshList()
            btnAddRule.setOnPreferenceClickListener {
                TimeRulePreferenceDialogFragment.showDialog(btnAddRule.context)
                true
            }
        }

        fun refreshList() {
            timeRulesCategory.removeAll()
            Prefs_(context).timeRules().getOr(emptySet()).forEachIndexed { i, s ->
                log("i=$i, timeRule=$s")
                timeRulesCategory.addPreference(TimeRulePreference(requireContext()).apply {
                    timeRule = TimeRule.fromString(s)
                    key = i.toString()
                })
            }
            timeRulesCategory.title =
                if (timeRulesCategory.isEmpty()) getString(R.string.home_timeRules_alwaysActive)
                else ""
            DetoxAccessibilityService.instance?.reloadTimeRules()
        }
    }
}