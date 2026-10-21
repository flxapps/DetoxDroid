package com.flx_apps.digitaldetox.ui.screens.rule_groups

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import com.flx_apps.digitaldetox.feature_types.NeedsPermissionsFeature
import com.flx_apps.digitaldetox.features.BreakDoomScrollingFeature
import com.flx_apps.digitaldetox.rules.GroupId
import com.flx_apps.digitaldetox.rules.RuleGroup
import com.flx_apps.digitaldetox.rules.RuleGroups
import com.flx_apps.digitaldetox.rules.Step
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.olshevski.navigation.reimagined.hilt.hiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** The wait a group starts out with when the step is switched on. */
val DefaultWaitMs: Long = TimeUnit.SECONDS.toMillis(10)

/**
 * Edits one group. Every change goes to the group at once and through [RuleGroups.onChanged], so
 * the service and the other screens follow; [version] lets the screen redraw after each.
 */
@HiltViewModel
class RuleGroupViewModel @Inject constructor(
    application: Application, savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    companion object {
        @Composable
        fun withGroupId(groupId: GroupId): RuleGroupViewModel =
            hiltViewModel(defaultArguments = bundleOf("groupId" to groupId))
    }

    val groupId: GroupId = savedStateHandle["groupId"]!!

    /** The group, or null once it has been deleted. */
    val group: RuleGroup? get() = RuleGroups.get(groupId)

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    fun setEnabled(enabled: Boolean) = edit { isEnabled = enabled }

    fun rename(name: String) = edit { this.name = name.trim() }

    fun delete() {
        RuleGroups.delete(groupId)
        _version.update { it + 1 }
    }

    /**
     * Switches [step] on or off. A step switched on whose feature is missing a permission goes on
     * anyway and is reported, so the screen can offer the permission; it takes hold once granted.
     * @return the feature that is missing a permission, or null
     */
    fun setStep(step: Step, on: Boolean): NeedsPermissionsFeature? {
        edit {
            when (step) {
                Step.WAIT -> waitBeforeOpeningMs = if (on) DefaultWaitMs else 0L
                Step.GRAY -> grayAfterMs = if (on) defaultGrayAfterMs() else RuleGroup.STEP_OFF
                Step.SCROLL_BREAK -> scrollBreakAfterMs = if (on) {
                    BreakDoomScrollingFeature.DefaultTimeUntilWarningMs
                } else {
                    RuleGroup.STEP_OFF
                }

                Step.LOCK -> locks = on
            }
        }
        if (!on) return null
        val feature = step.feature as? NeedsPermissionsFeature ?: return null
        return feature.takeIf { !it.hasPermissions(getApplication()) }
    }

    private fun RuleGroup.defaultGrayAfterMs(): Long = RuleGroup.defaultGrayAfterMs(budgetMs)

    fun setWaitMs(ms: Long) = edit { waitBeforeOpeningMs = ms }

    fun setGrayAfterMs(ms: Long) = edit { grayAfterMs = ms }

    fun setScrollBreakAfterMs(ms: Long) = edit { scrollBreakAfterMs = ms }

    private fun edit(change: RuleGroup.() -> Unit) {
        val group = group ?: return
        group.change()
        group.normalize()
        RuleGroups.onChanged()
        _version.update { it + 1 }
    }
}
