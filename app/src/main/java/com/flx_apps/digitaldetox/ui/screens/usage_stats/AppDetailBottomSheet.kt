package com.flx_apps.digitaldetox.ui.screens.usage_stats

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.feature_types.SupportsAppExceptionsFeature
import com.flx_apps.digitaldetox.features.BreakDoomScrollingFeature
import com.flx_apps.digitaldetox.features.FeaturesProvider
import com.flx_apps.digitaldetox.features.DisableAppsFeature
import com.flx_apps.digitaldetox.features.GrayscaleAppsFeature
import com.flx_apps.digitaldetox.util.toHrMinString
import com.flx_apps.digitaldetox.util.toShortDurationString
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
    val pm = context.packageManager

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        val icon = runCatching { pm.getApplicationIcon(app.packageName) }.getOrNull()
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Image(
                    bitmap = icon.toBitmap().asImageBitmap(),
                    contentDescription = app.label,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
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
            stringResource(R.string.usage_stats_app_details),
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
                        text = stringResource(R.string.usage_stats_opens_short),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (app.sessionCount > 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = app.sessionCount.toString(),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.usage_stats_sessions_short),
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
                        text = stringResource(R.string.usage_stats_avg_session),
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
                                    R.string.usage_stats_scroll_intensity_detail,
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
                                contentDescription = stringResource(R.string.usage_stats_scroll_intensity_info),
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.usage_stats_scroll_intensity_score),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (trendData.size >= 2) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.usage_stats_trend_7d),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            AppTrendSparkline(
                dailyUsage = trendData, modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            stringResource(R.string.usage_stats_quick_actions),
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
    val grayscaleActive = GrayscaleAppsFeature.isActivated
    val doomScrollingActive = BreakDoomScrollingFeature.isActivated
    val disableAppsActive = DisableAppsFeature.isActivated

    if (!grayscaleActive && !doomScrollingActive && !disableAppsActive) {
        Text(
            stringResource(R.string.usage_stats_no_active_features),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        key(version) {
            if (grayscaleActive) {
                val isInGrayscale = GrayscaleAppsFeature.appliesTo(app.packageName)
                FeatureActionChip(
                    label = if (isInGrayscale) stringResource(R.string.usage_stats_remove_grayscale)
                    else stringResource(R.string.usage_stats_add_grayscale),
                    color = colorResource(id = R.color.purple),
                    isActive = isInGrayscale,
                    onClick = {
                        scope.toggleAppException(app.packageName, GrayscaleAppsFeature, onToggled)
                    })
            }

            if (doomScrollingActive) {
                val isInDoomScroll = BreakDoomScrollingFeature.appliesTo(app.packageName)
                FeatureActionChip(
                    label = if (isInDoomScroll) stringResource(R.string.usage_stats_remove_scroll_guard)
                    else stringResource(R.string.usage_stats_add_scroll_guard),
                    color = colorResource(id = R.color.orange),
                    isActive = isInDoomScroll,
                    onClick = {
                        scope.toggleAppException(app.packageName, BreakDoomScrollingFeature, onToggled)
                    })
            }

            if (disableAppsActive) {
                val isDisabled = DisableAppsFeature.disableableApps.contains(app.packageName)
                FeatureActionChip(
                    label = if (isDisabled) stringResource(R.string.usage_stats_enable_app)
                    else stringResource(R.string.usage_stats_disable_app),
                    color = colorResource(id = R.color.pink),
                    isActive = isDisabled,
                    onClick = {
                        scope.toggleAppException(app.packageName, DisableAppsFeature, onToggled)
                    })
            }
        }
    }
}

/**
 * Toggles [packageName] in the feature's exception list. The underlying [DataStoreProperty] write
 * is blocking, so it is dispatched off the main thread.
 */
private fun CoroutineScope.toggleAppException(
    packageName: String,
    feature: SupportsAppExceptionsFeature,
    onComplete: () -> Unit = {}
) {
    launch(Dispatchers.IO) {
        if (feature.appExceptions.contains(packageName)) {
            feature.appExceptions -= packageName
        } else {
            feature.appExceptions += packageName
        }
        FeaturesProvider.reloadActiveFeatures()
        onComplete()
    }
}

@Composable
private fun FeatureActionChip(
    label: String, color: Color, isActive: Boolean, onClick: () -> Unit
) {
    FilterChip(
        modifier = Modifier.height(32.dp),
        selected = isActive,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color.copy(alpha = 0.2f), selectedLabelColor = color
        )
    )
}
