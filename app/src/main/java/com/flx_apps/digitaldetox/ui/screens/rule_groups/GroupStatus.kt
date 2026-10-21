package com.flx_apps.digitaldetox.ui.screens.rule_groups

import android.content.Context
import androidx.annotation.ColorRes
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
import com.flx_apps.digitaldetox.ui.screens.schedule.momentText
import com.flx_apps.digitaldetox.ui.screens.schedule.scheduleStatus
import com.flx_apps.digitaldetox.ui.theme.BrandTone
import java.time.LocalDateTime
import java.util.Locale

/**
 * What a group is doing right now, for the chip on its card: off, off until its window opens,
 * how much is left, or what its steps do while it has no budget.
 * @param strong whether the chip stands out: something is being held back right now
 * @param colorRes the color of what is being held back, see [BrandTone]; null for the theme's own
 */
class GroupStatus(
    val text: String,
    val icon: ImageVector?,
    val strong: Boolean,
    @ColorRes val colorRes: Int? = null,
) {
    companion object {
        fun of(context: Context, group: RuleGroup, now: LocalDateTime = LocalDateTime.now()): GroupStatus {
            if (!group.isEnabled) {
                return GroupStatus(context.getString(R.string.state_off), Icons.Default.PauseCircle, false)
            }
            if (!group.isActiveAt(now)) {
                val until = (scheduleStatus(group.scheduleRules, now) as? ScheduleStatus.InactiveUntil)?.until
                val text = if (until != null) {
                    context.getString(
                        R.string.ruleGroups_status_offUntil, momentText(until, now, Locale.getDefault())
                    )
                } else {
                    context.getString(R.string.state_off)
                }
                return GroupStatus(text, Icons.Default.Schedule, false)
            }
            if (EmergencyBrakeFeature.isBraking() && (group.locks || group.grays)) {
                return GroupStatus(
                    context.getString(R.string.feature_brake_shortcut), Icons.Default.Lock, true, R.color.blue
                )
            }
            if (group.hasBudget) {
                val leftMs = GroupBudgets.leftMs(group) ?: 0L
                if (leftMs > 0L) {
                    val leftMinutes = (leftMs + 59_999L) / 60_000L
                    val grayReached = GroupBudgets.isGrayReached(group)
                    return GroupStatus(
                        context.getString(R.string.today_budget_left, GroupTexts.duration(context, leftMinutes * 60_000L)),
                        if (grayReached) Icons.Default.Contrast else null,
                        strong = grayReached,
                        colorRes = if (grayReached) R.color.purple else null
                    )
                }
                return when {
                    group.locks && group.canHoldBackApps -> GroupStatus(
                        context.getString(R.string.ruleGroups_status_locked), Icons.Default.Lock, true, R.color.pink
                    )

                    group.grays -> GroupStatus(
                        context.getString(R.string.ruleGroups_status_gray), Icons.Default.Contrast, true, R.color.purple
                    )

                    else -> GroupStatus(context.getString(R.string.ruleGroups_status_usedUp), null, false)
                }
            }
            return when {
                group.locks && group.canHoldBackApps -> GroupStatus(
                    context.getString(R.string.ruleGroups_status_locked), Icons.Default.Lock, true, R.color.pink
                )

                group.grays -> GroupStatus(
                    context.getString(R.string.ruleGroups_status_gray), Icons.Default.Contrast, true, R.color.purple
                )

                group.waits -> GroupStatus(
                    context.getString(
                        R.string.ruleGroups_status_wait, GroupTexts.seconds(context, group.waitBeforeOpeningMs)
                    ),
                    Icons.Default.HourglassTop, true, R.color.yellow
                )

                group.breaksScrolling -> GroupStatus(
                    context.getString(R.string.ruleGroups_status_scrollBreaks), Icons.Default.SwipeVertical, true,
                    R.color.orange
                )

                else -> GroupStatus(context.getString(R.string.ruleGroups_status_noSteps), null, false)
            }
        }
    }
}
