package com.flx_apps.digitaldetox.ui.screens.onboarding

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavigationRoutes
import com.flx_apps.digitaldetox.ui.widgets.IconCard
import com.flx_apps.digitaldetox.ui.widgets.NumberPickerDialog
import com.flx_apps.digitaldetox.util.NavigationUtil
import com.flx_apps.digitaldetox.util.NotificationHelper
import com.flx_apps.digitaldetox.util.observeAsState
import com.flx_apps.digitaldetox.util.toHrMinString
import com.flx_apps.digitaldetox.ui.widgets.apps.AppSelectionListItem
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Onboarding step: ask for the usage-access special permission so the app-selection step can rank
 * apps by real usage. Grant state is re-checked whenever the user returns from system settings.
 */
@Composable
internal fun UsageAccessStep(viewModel: OnboardingViewModel) {
    val context = LocalContext.current
    val hasUsageAccess by viewModel.hasUsageAccess.collectAsState()
    val lifecycleState = LocalLifecycleOwner.current.lifecycle.observeAsState().value
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.Event.ON_RESUME) viewModel.refreshUsageAccess()
    }

    OnboardingStepColumn(
        title = stringResource(id = R.string.onboarding_usageAccess_title),
        message = stringResource(id = R.string.onboarding_usageAccess_message)
    ) {
        if (hasUsageAccess) {
            IconCard(icon = Icons.Default.CheckCircle) {
                Text(
                    stringResource(id = R.string.onboarding_usageAccess_granted),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            Button(
                onClick = { NavigationUtil.openUsageAccessSettings(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(id = R.string.onboarding_usageAccess_grant))
            }
            Text(
                text = stringResource(id = R.string.onboarding_usageAccess_skipHint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

/**
 * Onboarding step: choose an intensity preset and (for Balanced/Strict) the daily budget for the
 * selected apps, pre-set to a realistic goal derived from actual usage.
 */
@Composable
internal fun PresetStep(viewModel: OnboardingViewModel) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.refreshBudgetSuggestion() }
    val preset by viewModel.preset.collectAsState()
    val budgetMinutes by viewModel.budgetMinutes.collectAsState()
    val budgetIsSuggested by viewModel.budgetIsSuggested.collectAsState()
    var showBudgetDialog by remember { mutableStateOf(false) }

    if (showBudgetDialog) {
        NumberPickerDialog(
            titleText = stringResource(id = R.string.onboarding_preset_budget),
            initialValue = budgetMinutes,
            onValueSelected = { viewModel.setBudgetMinutes(it) },
            onDismissRequest = { showBudgetDialog = false },
            range = 15..180 step 15,
            label = { it.minutes.toHrMinString(context) })
    }

    OnboardingStepColumn(
        title = stringResource(id = R.string.onboarding_preset_title),
        message = stringResource(id = R.string.onboarding_preset_message)
    ) {
        Column(modifier = Modifier.selectableGroup()) {
            PresetCard(
                title = stringResource(id = R.string.onboarding_preset_gentle),
                description = stringResource(id = R.string.onboarding_preset_gentle_description),
                selected = preset == OnboardingPreset.GENTLE,
                onClick = { viewModel.selectPreset(OnboardingPreset.GENTLE) })
            PresetCard(
                title = stringResource(id = R.string.onboarding_preset_balanced),
                description = stringResource(id = R.string.onboarding_preset_balanced_description),
                selected = preset == OnboardingPreset.BALANCED,
                badgeText = stringResource(id = R.string.onboarding_preset_balanced_badge),
                onClick = { viewModel.selectPreset(OnboardingPreset.BALANCED) })
            PresetCard(
                title = stringResource(id = R.string.onboarding_preset_strict),
                description = stringResource(id = R.string.onboarding_preset_strict_description),
                selected = preset == OnboardingPreset.STRICT,
                onClick = { viewModel.selectPreset(OnboardingPreset.STRICT) })
        }
        if (preset != OnboardingPreset.GENTLE) {
            OnboardingTile(
                icon = Icons.Default.HourglassBottom,
                title = stringResource(id = R.string.onboarding_preset_budget),
                subtitle = if (budgetIsSuggested) {
                    stringResource(id = R.string.onboarding_preset_budget_suggested)
                } else {
                    stringResource(id = R.string.onboarding_preset_budget_description)
                },
                trailing = {
                    Text(
                        text = stringResource(id = R.string.time__minutes, budgetMinutes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = { showBudgetDialog = true })
            GrayscalePreviewTile(viewModel)
        }
    }
}

/**
 * Lets the user feel the grayscale effect for a few seconds. Only shown when the required
 * permission is already there — during a first onboarding run it usually is not, but re-runs and
 * adb/root users get the live demo.
 */
@Composable
private fun GrayscalePreviewTile(viewModel: OnboardingViewModel) {
    val writeSecureSettingsGranted by viewModel.writeSecureSettingsGranted.collectAsState()
    if (!writeSecureSettingsGranted) return
    val isPreviewing by viewModel.isPreviewingGrayscale.collectAsState()
    OnboardingTile(
        icon = Icons.Default.InvertColors,
        title = stringResource(id = R.string.onboarding_preset_preview),
        subtitle = stringResource(id = R.string.onboarding_preset_preview_description),
        enabled = !isPreviewing,
        onClick = { viewModel.previewGrayscale() })
}

@Composable
private fun PresetCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    badgeText: String? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            CardDefaults.outlinedCardBorder()
        },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = null)
            Column(modifier = Modifier.padding(start = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (badgeText != null) {
                        Badge(modifier = Modifier.padding(start = 8.dp)) { Text(badgeText) }
                    }
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/**
 * Onboarding step: a dynamic permissions checklist based on the chosen preset. Nothing here
 * blocks continuing — missing permissions are re-requested by the feature screens later.
 */
@Composable
internal fun PermissionsStep(
    viewModel: OnboardingViewModel,
    navViewModel: NavViewModel = NavViewModel.navViewModel()
) {
    val context = LocalContext.current
    val lifecycleState = LocalLifecycleOwner.current.lifecycle.observeAsState().value
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissionStates()
    }
    val preset by viewModel.preset.collectAsState()
    val overlayGranted by viewModel.overlayGranted.collectAsState()
    val notificationsGranted by viewModel.notificationsGranted.collectAsState()
    val writeSecureSettingsGranted by viewModel.writeSecureSettingsGranted.collectAsState()
    val accessibilityEnabled by viewModel.accessibilityEnabled.collectAsState()
    val canOneTapGrant by viewModel.canOneTapGrant.collectAsState()

    val activity = LocalActivity.current
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshPermissionStates()
        // after a permanent denial the system dismisses the request instantly and silently —
        // route to the app's notification settings instead of leaving a dead button
        if (!granted && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(
                activity, NotificationHelper.getNotificationPermission()
            )
        ) {
            NavigationUtil.openAppNotificationSettings(context)
        }
    }

    OnboardingStepColumn(
        title = stringResource(id = R.string.onboarding_permissions_title),
        message = stringResource(id = R.string.onboarding_permissions_message)
    ) {
        PermissionTile(
            icon = Icons.Default.Layers,
            title = stringResource(id = R.string.onboarding_permissions_overlay),
            description = stringResource(id = R.string.onboarding_permissions_overlay_description),
            granted = overlayGranted,
            onClick = { NavigationUtil.openOverlayPermissionsSettings(context) })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionTile(
                icon = Icons.Default.Notifications,
                title = stringResource(id = R.string.onboarding_permissions_notifications),
                description = stringResource(id = R.string.onboarding_permissions_notifications_description),
                granted = notificationsGranted,
                onClick = {
                    notificationLauncher.launch(NotificationHelper.getNotificationPermission())
                })
        }
        if (preset != OnboardingPreset.GENTLE) {
            PermissionTile(
                icon = Icons.Default.InvertColors,
                title = stringResource(id = R.string.onboarding_permissions_secureSettings),
                description = stringResource(id = R.string.onboarding_permissions_secureSettings_description) + " " + when {
                    writeSecureSettingsGranted -> ""
                    canOneTapGrant -> stringResource(id = R.string.onboarding_permissions_secureSettings_oneTap)
                    else -> stringResource(id = R.string.onboarding_permissions_secureSettings_later)
                },
                granted = writeSecureSettingsGranted,
                onClick = {
                    if (canOneTapGrant) {
                        viewModel.grantWriteSecureSettings()
                    } else {
                        navViewModel.openRoute(NavigationRoutes.ShizukuSetup)
                    }
                })
        }
        PermissionTile(
            icon = Icons.Default.Accessibility,
            title = stringResource(id = R.string.onboarding_permissions_accessibility),
            description = stringResource(id = R.string.onboarding_permissions_accessibility_description) + " " + when {
                accessibilityEnabled -> ""
                writeSecureSettingsGranted -> stringResource(id = R.string.onboarding_permissions_accessibility_auto)
                else -> stringResource(id = R.string.onboarding_permissions_accessibility_manual)
            },
            granted = accessibilityEnabled,
            onClick = {
                if (writeSecureSettingsGranted) {
                    viewModel.enableAccessibilityService()
                } else {
                    NavigationUtil.openAccessibilitySettings(context)
                }
            })
    }
}

@Composable
private fun PermissionTile(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    OnboardingTile(
        icon = icon,
        title = title,
        subtitle = description.trim(),
        highlighted = granted,
        onClick = if (granted) null else onClick,
        trailing = {
            if (granted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(id = R.string.onboarding_permissions_state_granted),
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

/**
 * A card-styled tile for the onboarding steps: a leading icon in a tinted circle, a title and a
 * subtitle, and optional trailing content. Deliberately shaped like [PresetCard] so the permission
 * checklist and the budget/preview rows read as one coherent set of cards (rather than differently
 * indented list rows). [highlighted] gives a "done"/selected look; a null [onClick] makes it inert.
 */
@Composable
private fun OnboardingTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .alpha(if (enabled) 1f else 0.6f)
            .then(
                if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier
            ),
        border = if (highlighted) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            CardDefaults.outlinedCardBorder()
        },
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            trailing?.invoke()
        }
    }
}

/**
 * Onboarding step: the ranked distracting-apps list with the heuristic's pre-selection.
 */
@Composable
internal fun PickAppsStep(viewModel: OnboardingViewModel) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.loadAppsIfNeeded() }
    val isLoading by viewModel.isLoadingApps.collectAsState()
    val appRows by viewModel.appRows.collectAsState()
    val hasUsageData by viewModel.hasUsageData.collectAsState()
    val selectedCount = appRows.count { it.checked }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 16.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            OnboardingStepHeader(
                title = stringResource(id = R.string.onboarding_apps_title),
                message = stringResource(id = R.string.onboarding_apps_message)
            )
            Text(
                text = pluralStringResource(
                    id = R.plurals.onboarding_apps_selectedCount, selectedCount, selectedCount
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        when {
            isLoading -> PickAppsPlaceholder {
                CircularProgressIndicator()
                Text(
                    text = stringResource(id = R.string.onboarding_apps_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            appRows.isEmpty() -> PickAppsPlaceholder {
                Icon(
                    imageVector = Icons.Default.SearchOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(id = R.string.onboarding_apps_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(appRows, key = { it.packageName }) { row ->
                    AppSelectionListItem(
                        packageName = row.packageName,
                        appName = row.appName,
                        appCategory = row.appCategory,
                        isSystemApp = row.isSystemApp,
                        checked = row.checked,
                        supportingText = if (hasUsageData && row.avgDailyUsageMs > 0) {
                            stringResource(
                                id = R.string.onboarding_apps_avgUsage,
                                row.avgDailyUsageMs.milliseconds.toHrMinString(context)
                            )
                        } else null,
                        onCheckedChange = { viewModel.toggleApp(row.packageName) }
                    )
                }
            }
        }
    }
}

/**
 * Centered placeholder area for the app list's loading and empty states.
 */
@Composable
private fun PickAppsPlaceholder(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp), contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            content()
        }
    }
}
