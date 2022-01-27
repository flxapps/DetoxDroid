package com.flx_apps.digitaldetox.prefs

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentContainerView
import androidx.preference.MultiSelectListPreference
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.log
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.takisoft.preferencex.TimePickerPreference
import org.androidannotations.annotations.*
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.*

@EFragment
open class TimeRulePreferenceDialogFragment : BottomSheetDialogFragment() {
    companion object {
        fun showDialog(context: Context, timeRuleId: Int = -1) {
            TimeRulePreferenceDialogFragment_.builder().timeRuleId(timeRuleId).build()
                .show((context as AppCompatActivity).supportFragmentManager, "TimeRulePreferenceDialog")
        }
    }

    /**
     * not a real ID, but rather the position in the parent list of rules / the
     * underlying set ; can change over time
     */
    @JvmField
    @FragmentArg
    var timeRuleId: Int = -1

    val viewId = View.generateViewId()
    lateinit var saveButton: MaterialButton
    lateinit var deleteButton: MaterialButton

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            id = viewId
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // init close button for the dialog
        saveButton = MaterialButton(view.context, null, R.attr.borderlessButtonStyle).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT).apply { weight = 1f }
            text = getText(R.string.action_save)
            isEnabled = false
            setOnClickListener { onSaveClicked() }
        }
        deleteButton = MaterialButton(view.context, null, R.attr.borderlessButtonStyle).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT).apply { weight = 1f }
            text = getText(R.string.action_delete)
            setOnClickListener { onDeleteClicked() }
            visibility = if (timeRuleId >= 0) View.VISIBLE else View.GONE
        }
        val buttonContainer = LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            id = View.generateViewId()
            addView(saveButton)
            addView(deleteButton)
        }

        (dialog as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_EXPANDED
        childFragmentManager
            .beginTransaction()
            .add(viewId, TimeRulePreferenceDialogFragment_.DialogContentFragment_.builder().timeRuleId(timeRuleId).build())
            .runOnCommit { (view as LinearLayout).addView(buttonContainer) }
            .commit()
    }

    fun onSaveClicked() {
        val prefs = Prefs_(context)
        val fragment = childFragmentManager.findFragmentById(viewId) as DialogContentFragment
        val timeRule = TimeRule(
            fragment.weekdaysPreference.values.map { s -> DayOfWeek.of(s.toInt()) }.toSet(),
            LocalTime.of(fragment.fromTimePreference.hourOfDay, fragment.fromTimePreference.minute),
            LocalTime.of(fragment.toTimePreference.hourOfDay, fragment.toTimePreference.minute)
        )
        var set = prefs.timeRules().get()
        if (timeRuleId < 0) {
            set = set.plus(timeRule.toString())
            timeRuleId = prefs.timeRules().get().size
        }
        else {
            set = set.toMutableList().apply { this[timeRuleId] =  timeRule.toString()}.toSet()
        }

        prefs.edit().timeRules().put(set).apply()
        dialog!!.dismiss()
        refreshParentTimeRulesList()
    }

    fun onDeleteClicked() {
        val prefs = Prefs_(context)
        prefs.edit().timeRules().put(
            prefs.timeRules().get().toMutableList().apply { removeAt(timeRuleId) }.toSet()
        ).apply()
        dialog!!.dismiss()
        refreshParentTimeRulesList()
    }

    fun refreshParentTimeRulesList() {
        (context as AppCompatActivity)
            .findViewById<FragmentContainerView>(R.id.timeRulesPreferences)
            .getFragment<PreferenceFragment.TimeRulesListPreferencesFragment>()
            .refreshList()
    }

    @EFragment
    @PreferenceScreen(R.xml.preferences_time_rules_dialog)
    open class DialogContentFragment : PreferenceFragment() {
        @JvmField
        @FragmentArg
        var timeRuleId: Int = -1

        @PreferenceByKey(R.string.home_timeRules_weekdays)
        lateinit var weekdaysPreference: MultiSelectListPreference

        @PreferenceByKey(R.string.home_timeRules_from)
        lateinit var fromTimePreference: TimePickerPreference

        @PreferenceByKey(R.string.home_timeRules_to)
        lateinit var toTimePreference: TimePickerPreference

        @AfterPreferences
        fun init() {
            var timeRule = TimeRule(emptySet(), LocalTime.of(0, 0), LocalTime.of(0, 0))
            if (timeRuleId >= 0) {
                timeRule = TimeRule.getById(requireContext(), timeRuleId)
            }

            fromTimePreference.setTime(timeRule.fromTime.hour, timeRule.fromTime.minute)
            toTimePreference.setTime(timeRule.toTime.hour, timeRule.toTime.minute)

            weekdaysPreference.entries = DayOfWeek.values().map { dayOfWeek ->
                dayOfWeek.getDisplayName(
                    TextStyle.FULL,
                    Locale.getDefault()
                )
            }.toTypedArray()
            weekdaysPreference.entryValues =
                DayOfWeek.values().map { dayOfWeek -> dayOfWeek.value.toString() }.toTypedArray()

            weekdaysPreference.setOnPreferenceChangeListener { preference, newValue ->
                log("newValue=$newValue")
                (parentFragment as TimeRulePreferenceDialogFragment).saveButton.isEnabled = (newValue as Set<*>).isNotEmpty()
                true
            }

            weekdaysPreference.setSummaryProvider { preference ->
                (preference as MultiSelectListPreference).values
                    .map { v -> DayOfWeek.of(v.toInt()) }
                    .joinToString { dayOfWeek ->
                        dayOfWeek.getDisplayName(
                            TextStyle.SHORT,
                            Locale.getDefault()
                        )
                    }
            }

            weekdaysPreference.values =
                timeRule.days.map { dayOfWeek -> dayOfWeek.value.toString() }.toSet()
            weekdaysPreference.callChangeListener(weekdaysPreference.values)
        }
    }
}