package com.flx_apps.digitaldetox.ui.screens.rule_groups

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwipeVertical
import androidx.compose.ui.graphics.vector.ImageVector
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.features.EmergencyBrakeFeature
import com.flx_apps.digitaldetox.rules.GroupBudgets
import com.flx_apps.digitaldetox.rules.RuleGroup
import com.flx_apps.digitaldetox.ui.screens.schedule.ScheduleStatus
import com.flx_apps.digitaldetox.ui.screens.schedule.scheduleStatus
import com.flx_apps.digitaldetox.util.toHrMinString
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.minutes

/**
 * What a group is doing right now, for the chip on its card: off, off until its window opens,
 * how much is left, or what its steps do while it has no budget.
 * @param strong whether the chip stands out: something is being held back right now
 */
class GroupStatus(val text: String, val icon: ImageVector?, val strong: Boolean) {
    companion object {
        fun of(context: Context, group: RuleGroup, now: LocalDateTime = LocalDateTime.now()): GroupStatus {
            if (!group.isEnabled) {
                return GroupStatus(context.getString(R.string.state_off), Icons.Default.PauseCircle, false)
            }
            if (!group.isActiveAt(now)) {
                val until = (scheduleStatus(group.scheduleRules, now) as? ScheduleStatus.InactiveUntil)?.until
                val text = if (until != null) {
                    context.getString(R.string.ruleGroups_status_offUntil, GroupTexts.time(until.toLocalTime()))
                } else {
                    context.getString(R.string.state_off)
                }
                return GroupStatus(text, Icons.Default.Schedule, false)
            }
            if (EmergencyBrakeFeature.isBraking() && (group.locks || group.grays)) {
                return GroupStatus(context.getString(R.string.feature_brake_shortcut), Icons.Default.Lock, true)
            }
            if (group.hasBudget) {
                val leftMs = GroupBudgets.leftMs(group) ?: 0L
                if (leftMs > 0L) {
                    val leftMinutes = (leftMs + 59_999L) / 60_000L
                    return GroupStatus(
                        context.getString(R.string.today_budget_left, leftMinutes.minutes.toHrMinString(context)),
                        null,
                        strong = GroupBudgets.isGrayReached(group)
                    )
                }
                return when {
                    group.locks && group.canHoldBackApps ->
                        GroupStatus(context.getString(R.string.ruleGroups_status_locked), Icons.Default.Lock, true)

                    group.grays ->
                        GroupStatus(context.getString(R.string.ruleGroups_status_gray), Icons.Default.Contrast, true)

                    else -> GroupStatus(context.getString(R.string.today_budget_usedUp), null, false)
                }
            }
            return when {
                group.locks && group.canHoldBackApps ->
                    GroupStatus(context.getString(R.string.ruleGroups_status_locked), Icons.Default.Lock, true)

                group.grays ->
                    GroupStatus(context.getString(R.string.ruleGroups_status_gray), Icons.Default.Contrast, true)

                group.waits -> GroupStatus(
                    context.getString(
                        R.string.ruleGroups_status_wait, GroupTexts.seconds(context, group.waitBeforeOpeningMs)
                    ),
                    Icons.Default.HourglassTop, true
                )

                group.breaksScrolling -> GroupStatus(
                    context.getString(R.string.ruleGroups_status_scrollBreaks), Icons.Default.SwipeVertical, true
                )

                else -> GroupStatus(context.getString(R.string.ruleGroups_status_noSteps), null, false)
            }
        }
    }
}
