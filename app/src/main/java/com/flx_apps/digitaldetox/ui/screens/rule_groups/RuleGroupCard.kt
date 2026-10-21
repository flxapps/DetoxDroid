package com.flx_apps.digitaldetox.ui.screens.rule_groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.features.EmergencyBrakeFeature
import com.flx_apps.digitaldetox.rules.GroupBudgets
import com.flx_apps.digitaldetox.rules.RuleGroup
import com.flx_apps.digitaldetox.rules.RuleGroups
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavigationRoutes
import com.flx_apps.digitaldetox.ui.screens.schedule.rememberCurrentMinute
import com.flx_apps.digitaldetox.ui.widgets.AppIcon
import com.flx_apps.digitaldetox.util.toHrMinString
import com.flx_apps.digitaldetox.websites.Websites
import kotlin.time.Duration.Companion.milliseconds

/** How many app icons a card shows before the rest become "+3". */
private const val CARD_APP_ICONS = 4

/**
 * The home screen's groups: a heading with the way to a new group, then a card per group with
 * what it is doing right now. Without any group it says what one is for.
 */
@Composable
fun RuleGroupsSection(navViewModel: NavViewModel = NavViewModel.navViewModel()) {
    val groupsChanged by RuleGroups.changes.collectAsState()
    val groups = remember(groupsChanged) { RuleGroups.all }
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp)
        ) {
            Text(
                text = stringResource(id = R.string.ruleGroups_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )
            TextButton(onClick = { navViewModel.openRoute(NavigationRoutes.NewRuleGroup) }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(text = stringResource(id = R.string.ruleGroups_new))
            }
        }
        if (groups.isEmpty()) {
            EmptyGroupsCard(onNewGroup = { navViewModel.openRoute(NavigationRoutes.NewRuleGroup) })
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                groups.forEach { group ->
                    // a group is one instance edited in place, so the card is keyed on the
                    // change count as well, or an edit would leave it as it was
                    key(group.id, groupsChanged) {
                        RuleGroupCard(
                            group = group,
                            onClick = { navViewModel.openRoute(NavigationRoutes.RuleGroup(group.id)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyGroupsCard(onNewGroup: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(id = R.string.ruleGroups_empty_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(id = R.string.ruleGroups_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
            )
            Button(onClick = onNewGroup) {
                Text(text = stringResource(id = R.string.ruleGroups_new))
            }
        }
    }
}

/**
 * One group on the home screen: its name and what it is doing now, the apps and websites it
 * covers, and, with a budget, how much of it is used.
 */
@Composable
fun RuleGroupCard(group: RuleGroup, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now = rememberCurrentMinute()
    val brakeToken by EmergencyBrakeFeature.stateToken.collectAsState()
    val status = remember(group, now, brakeToken) { GroupStatus.of(context, group, now) }
    val apps = remember(group.appExceptions) { Websites.appsOf(group.appExceptions).sorted() }
    val websites = remember(group.appExceptions) { Websites.websitesOf(group.appExceptions).sorted() }
    val scheduleText = remember(group.scheduleRules) {
        GroupTexts.scheduleShort(context, group.scheduleRules)
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (group.isEnabled) 1f else 0.6f)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = GroupTexts.name(context, group),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                StatusChip(status)
            }
            ScopeIcons(apps = apps, websites = websites, allApps = !group.canHoldBackApps)
            if (group.hasBudget) {
                val usedMs = remember(group, now) { GroupBudgets.usedMs(group) }
                BudgetBar(fraction = (usedMs.toFloat() / group.budgetMs).coerceIn(0f, 1f))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(
                            id = R.string.ruleGroups_card_usedOf,
                            usedMs.coerceAtMost(group.budgetMs).milliseconds.toHrMinString(context),
                            group.budgetMs.milliseconds.toHrMinString(context)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = scheduleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = scheduleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusChip(status: GroupStatus) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = if (status.strong) colors.primaryContainer else colors.surfaceContainerHighest,
        contentColor = if (status.strong) colors.onPrimaryContainer else colors.onSurfaceVariant,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            status.icon?.let {
                Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(text = status.text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/**
 * The apps and websites of a group in one row: the first app icons, one chip for the websites,
 * and how many more there are. A group of all apps but a few shows what it leaves out, marked
 * as such.
 */
@Composable
private fun ScopeIcons(apps: List<String>, websites: List<String>, allApps: Boolean) {
    val context = LocalContext.current
    val shownApps = apps.take(CARD_APP_ICONS)
    val more = apps.size - shownApps.size
    if (apps.isEmpty() && websites.isEmpty()) {
        Text(
            text = stringResource(
                id = if (allApps) R.string.ruleGroups_scope_allApps else R.string.ruleGroups_scope_nothingYet
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (allApps) {
            SmallChip(text = stringResource(id = R.string.ruleGroups_card_allExcept), icon = Icons.Outlined.Apps)
        }
        shownApps.forEach { packageName ->
            AppIcon(
                packageName = packageName,
                size = 30.dp,
                modifier = Modifier.clip(RoundedCornerShape(9.dp))
            )
        }
        if (websites.isNotEmpty()) {
            SmallChip(
                text = if (websites.size == 1) {
                    Websites.displayName(context, websites.first())
                } else {
                    websites.size.toString()
                },
                icon = Icons.Default.Language
            )
        }
        if (more > 0) SmallChip(text = "+$more")
    }
}

@Composable
private fun SmallChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.height(30.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 9.dp)
        ) {
            icon?.let {
                Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** A thin rounded bar filled to [fraction]. */
@Composable
fun BudgetBar(fraction: Float, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 8.dp) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}
