package com.flx_apps.digitaldetox.ui.screens.schedule

import android.app.Application
import com.flx_apps.digitaldetox.feature_types.FeatureScheduleRule
import com.flx_apps.digitaldetox.feature_types.SupportsScheduleFeature
import com.flx_apps.digitaldetox.features.FeaturesProvider
import com.flx_apps.digitaldetox.ui.screens.feature.FeatureViewModel
import com.flx_apps.digitaldetox.ui.screens.feature.FeatureViewModelFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject

/**
 * The ID of a rule. The ID is generated as the hash code of the rule and has no meaning beyond
 * the scope of the schedule screen and view model (i.e. it is not stored in the data store).
 */
typealias ScheduleRuleId = Int

/**
 * In the ScheduleViewModel, a rule is represented by a pair of its ID and the rule itself. We need
 * the id in order to be able to edit and delete rules.
 *
 * @see ScheduleRuleId
 */
typealias ScheduleRuleItem = Pair<ScheduleRuleId, FeatureScheduleRule>

/**
 * The view model for the schedule screen. It contains the rules for when the feature should be
 * active or inactive (@see FeatureScheduleRule) and provides methods to add, edit and delete rules.
 */
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val application: Application,
    private val savedStateHandle: androidx.lifecycle.SavedStateHandle
) : FeatureViewModel(application, savedStateHandle) {
    companion object : FeatureViewModelFactory()

    private val scheduleFeature = feature as SupportsScheduleFeature

    private val _rules =
        MutableStateFlow(scheduleFeature.scheduleRules.associateBy { featureScheduleRule ->
            featureScheduleRule.hashCode()
        })
    val rules: StateFlow<Map<ScheduleRuleId, FeatureScheduleRule>> = _rules

    private val _dialogRule = MutableStateFlow<ScheduleRuleItem?>(null)
    val dialogRule: SharedFlow<ScheduleRuleItem?> = _dialogRule

    /**
     * Shows the bottom sheet for adding/editing a rule.
     */
    fun showBottomSheet(
        rule: ScheduleRuleItem = -1 to FeatureScheduleRule(
            listOf(), LocalTime.of(0, 0), LocalTime.of(0, 0)
        )
    ) {
        _dialogRule.value = rule.first to rule.second
    }

    /**
     * Updates the rule that is currently shown in the bottom sheet with the given values.
     */
    fun updateBottomSheet(
        daysOfWeek: List<DayOfWeek>? = null, start: LocalTime? = null, end: LocalTime? = null
    ) {
        _dialogRule.value = _dialogRule.value!!.first to _dialogRule.value!!.second.copyWith(
            daysOfWeek = daysOfWeek, start = start, end = end
        )
    }

    /**
     * Hides the bottom sheet.
     */
    fun hideBottomSheet() {
        _dialogRule.tryEmit(null)
    }

    /**
     * Saves the rule that is currently shown in the bottom sheet. If the rule has no ID yet, it is
     * a new rule and will be added to the list of rules. If it already has an ID, it is an existing
     * rule and will be updated.
     */
    fun onSaveClick() {
        val rule = _dialogRule.value!!
        if (rule.first == -1) {
            _rules.value = _rules.value + (rule.second.hashCode() to rule.second)
        } else {
            _rules.value = _rules.value + rule
        }
        scheduleFeature.scheduleRules = _rules.value.values.toSet()
        hideBottomSheet()
        FeaturesProvider.startOrStopFeature(feature)
    }

    /**
     * Deletes the rule that is currently shown in the bottom sheet.
     */
    fun onDeleteClick() {
        val ruleId = _dialogRule.value!!.first
        _rules.value = _rules.value.filterKeys { it != ruleId }
        scheduleFeature.scheduleRules = _rules.value.values.toSet()
        hideBottomSheet()
        FeaturesProvider.startOrStopFeature(feature)
    }
}