package com.flx_apps.digitaldetox.ui.screens.usage_stats

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.feature_types.AppExceptionListType
import com.flx_apps.digitaldetox.features.CommitmentPasswordFeature
import com.flx_apps.digitaldetox.ui.widgets.ChipFlowRow
import com.flx_apps.digitaldetox.features.FeaturesProvider
import com.flx_apps.digitaldetox.features.EmergencyBrakeFeature
import com.flx_apps.digitaldetox.rules.RuleGroup
import com.flx_apps.digitaldetox.rules.RuleGroups
import com.flx_apps.digitaldetox.websites.Websites
import com.flx_apps.digitaldetox.util.toHrMinString
import com.flx_apps.digitaldetox.util.toShortDurationString
import com.flx_apps.digitaldetox.util.formatDistance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun AppDetailContent(
    app: AppUsageStat,
    trendData: List<Pair<java.time.LocalDate, Long>> = emptyList(),
    onFeatureChanged: () -> Unit = {}
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UsageItemIcon(item = app, size = 48.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = app.totalTimeMs.milliseconds.toHrMinString(context),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            stringResource(R.string.usageStats_appDetails),
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (app.launchCount > 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = app.launchCount.toString(),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(
                            if (app.isWebsite) R.string.usageStats_visits_short
                            else R.string.usageStats_opens_short
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // a website's visits are its sessions, already counted next to this
            if (app.sessionCount > 0 && !app.isWebsite) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = app.sessionCount.toString(),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.usageStats_sessions_short),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (app.totalTimeMs > 0 && app.sessionCount > 0) {
                val avgMs = app.totalTimeMs / app.sessionCount
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = avgMs.milliseconds.toShortDurationString(context),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(
                            if (app.isWebsite) R.string.usageStats_avgVisit
                            else R.string.usageStats_avgSession
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (app.scrollIntensityScore > 0f) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = app.scrollIntensityScore.toInt().toString(),
                            style = MaterialTheme.typography.titleMedium
                        )
                        IconButton(
                            onClick = {
                                val scrollInfo = context.getString(
                                    R.string.usageStats_scrollIntensity_detail,
                                    app.scrollEventCount,
                                    app.scrollsPerMinute
                                )
                                Toast.makeText(
                                    context,
                                    if (app.scrollDistanceMeters >= 1.0) {
                                        "$scrollInfo · ${formatDistance(context, app.scrollDistanceMeters)}"
                                    } else scrollInfo,
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            modifier = Modifier.size(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = stringResource(R.string.usageStats_scrollIntensity_info),
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.usageStats_scrollIntensity_score),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (trendData.size >= 2) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.usageStats_trend7d),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            AppTrendSparkline(
                dailyUsage = trendData, modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            stringResource(R.string.usageStats_quickActions),
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(modifier = Modifier.height(8.dp))

        QuickActionsSection(app = app, onFeatureChanged = onFeatureChanged)

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun QuickActionsSection(app: AppUsageStat, onFeatureChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    val onToggled: () -> Unit = remember(app.packageName) {
        { version++; onFeatureChanged() }
    }
    // the chips edit the groups' lists, so a running brake and a commitment password in force
    // lock them like every other setting of the groups
    val brakeToken by EmergencyBrakeFeature.stateToken.collectAsState()
    val passwordToken by CommitmentPasswordFeature.stateToken.collectAsState()
    val groupsChanged by RuleGroups.changes.collectAsState()
    val braking = remember(brakeToken) { EmergencyBrakeFeature.isBraking() }
    val groups = remember(groupsChanged) { RuleGroups.all }
    val locked = remember(brakeToken, passwordToken) {
        FeaturesProvider.areSettingsLocked(RuleGroups.LOCK_ID)
    }

    if (groups.isEmpty() || locked) {
        Text(
            when {
                groups.isEmpty() -> stringResource(R.string.usageStats_noGroups)
                braking -> stringResource(
                    R.string.usageStats_lockedByBrake, EmergencyBrakeFeature.endsAtText()
                )
                else -> stringResource(R.string.usageStats_lockedByPassword)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Text(
        text = stringResource(R.string.usageStats_inGroups),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    ChipFlowRow {
        key(version) {
            groups.forEach { group ->
                val covered = coversApp(group, app)
                FeatureActionChip(
                    label = group.name.ifBlank { stringResource(R.string.ruleGroups_unnamed) },
                    color = MaterialTheme.colorScheme.primary,
                    isActive = covered,
                    onClick = { scope.toggleCoverage(app, group, onToggled) })
            }
        }
    }
}

/** Whether [group] covers [app], which for a website asks the group's website list. */
private fun coversApp(group: RuleGroup, app: AppUsageStat): Boolean {
    val site = Websites.siteOfEntry(app.packageName)
    return if (site != null) Websites.covers(group, site) else group.appliesTo(app.packageName)
}

/**
 * Puts [app] under [group] or takes it out: for a group of selected apps by listing it, for a
 * group of all apps but a few by excepting it. The underlying [DataStoreProperty] write is
 * blocking, so it is dispatched off the main thread.
 */
private fun CoroutineScope.toggleCoverage(
    app: AppUsageStat,
    group: RuleGroup,
    onComplete: () -> Unit = {}
) {
    launch(Dispatchers.IO) {
        val entry = app.packageName
        val onlyListed = if (Websites.isWebsite(entry)) {
            group.websiteListType == AppExceptionListType.ONLY_LIST
        } else {
            group.appExceptionListType == AppExceptionListType.ONLY_LIST
        }
        val wantCovered = !coversApp(group, app)
        if (wantCovered == onlyListed) {
            group.appExceptions += entry
        } else {
            group.appExceptions -= entry
        }
        RuleGroups.onChanged()
        onComplete()
    }
}

@Composable
private fun FeatureActionChip(
    label: String, color: Color, isActive: Boolean, onClick: () -> Unit
) {
    FilterChip(
        selected = isActive,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color.copy(alpha = 0.2f), selectedLabelColor = color
        )
    )
}
