package com.flx_apps.digitaldetox.ui.screens.home

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.feature_types.Feature
import com.flx_apps.digitaldetox.features.CommitmentPasswordFeature
import com.flx_apps.digitaldetox.features.EmergencyBrakeFeature
import com.flx_apps.digitaldetox.features.FeaturesProvider
import com.flx_apps.digitaldetox.features.PauseButtonFeature
import com.flx_apps.digitaldetox.system_integration.DetoxDroidDeviceAdminReceiver
import com.flx_apps.digitaldetox.system_integration.DetoxDroidState
import com.flx_apps.digitaldetox.system_integration.TodaySummary
import com.flx_apps.digitaldetox.system_integration.ScreenTimeSurfaces
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavigationRoutes
import com.flx_apps.digitaldetox.ui.widgets.AccessibilityDisclosureSupport
import com.flx_apps.digitaldetox.rules.Step
import com.flx_apps.digitaldetox.ui.screens.rule_groups.RuleGroupsSection
import com.flx_apps.digitaldetox.ui.widgets.SectionHeader
import com.flx_apps.digitaldetox.ui.widgets.SettingsGroup
import com.flx_apps.digitaldetox.ui.widgets.StatusIndicator
import com.flx_apps.digitaldetox.ui.widgets.rememberAccessibilityConsent
import com.flx_apps.digitaldetox.util.NavigationUtil
import com.flx_apps.digitaldetox.util.RestrictedSettingsUtil
import com.flx_apps.digitaldetox.util.UsageAccessUtil
import com.flx_apps.digitaldetox.util.observeAsState
import com.flx_apps.digitaldetox.util.toHrMinString
import com.flx_apps.digitaldetox.util.appLabel
import kotlin.math.atan2
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * The start screen of the app. It lists all features and their current state, offers a button to
 * start/stop DetoxDroid and displays some stats about the screen time of the current day.
 */
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = viewModel()
) {
    val detoxDroidState: DetoxDroidState = homeViewModel.detoxDroidState.collectAsState().value
    val snackbarHostState = remember { SnackbarHostState() }
    val droidState = rememberSleepyDroidState()
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }, topBar = {
            AppBar(detoxDroidState)
        }, floatingActionButton = {
            StartStopActionButton(detoxDroidState)
        }) {
            HomeScreenContent(it, droidState)
        }
        DroidCaughtOverlay(droidState)
    }
    HomeScreenSnackbarContents(snackbarHostState)
}

/**
 * The content of the snackbar. Depending on the current [HomeScreenSnackbarState], it shows a
 * different message and performs a different action when the action button is clicked.
 */
@Composable
private fun HomeScreenSnackbarContents(
    snackbarHostState: SnackbarHostState,
    homeViewModel: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val snackbarState = homeViewModel.snackbarState.collectAsState().value
    if (snackbarState == HomeScreenSnackbarState.ShowStartAcccessibilityServiceManually) {
        LaunchedEffect(key1 = snackbarState, block = {
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.action_requestPermissions),
                actionLabel = context.getString(R.string.action_go),
            )
            // always reset, so the snackbar can be shown again on the next failed attempt
            homeViewModel.setSnackbarState(HomeScreenSnackbarState.Hidden)
            if (result == SnackbarResult.ActionPerformed) {
                NavigationUtil.openAccessibilitySettings(context)
            }
        })
    } else if (snackbarState == HomeScreenSnackbarState.CommitmentPasswordLocked) {
        LaunchedEffect(key1 = snackbarState, block = {
            snackbarHostState.showSnackbar(
                message = context.getString(R.string.feature_commitmentPassword_stop_locked),
            )
            homeViewModel.setSnackbarState(HomeScreenSnackbarState.Hidden)
        })
    } else if (snackbarState == HomeScreenSnackbarState.BrakeRunning) {
        LaunchedEffect(key1 = snackbarState, block = {
            snackbarHostState.showSnackbar(
                message = context.getString(
                    R.string.feature_brake_stop_locked, EmergencyBrakeFeature.endsAtText()
                ),
            )
            homeViewModel.setSnackbarState(HomeScreenSnackbarState.Hidden)
        })
    }
}

/**
 * The top app bar of the home screen. It displays the current state of DetoxDroid.
 * @see DetoxDroidState
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AppBar(
    detoxDroidState: DetoxDroidState, navViewModel: NavViewModel = NavViewModel.navViewModel()
) {
    // observed so the status line follows a brake being pulled, extended or running out; the end
    // is read here as well, since the title lambda below is not recomposed for the token alone
    val brakeStateToken = EmergencyBrakeFeature.stateToken.collectAsState().value
    val brakeEndsAt = remember(brakeStateToken, detoxDroidState) {
        if (detoxDroidState == DetoxDroidState.Active && EmergencyBrakeFeature.isBraking()) {
            EmergencyBrakeFeature.endsAtText()
        } else null
    }
    val braking = brakeEndsAt != null
    TopAppBar(
        title = {
            Column {
                Text(text = stringResource(id = R.string.app_displayName))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusIndicator(
                        indicatorColor = colorResource(
                            id = when (detoxDroidState) {
                                DetoxDroidState.Active -> if (braking) R.color.blue else R.color.green
                                DetoxDroidState.Inactive -> R.color.gray
                                DetoxDroidState.Paused -> R.color.yellow
                            }
                        )
                    )
                    Text(
                        text = stringResource(
                            id = when (detoxDroidState) {
                                DetoxDroidState.Active -> if (braking) {
                                    R.string.home_state_braking
                                } else {
                                    R.string.home_state_active
                                }

                                DetoxDroidState.Inactive -> R.string.home_state_inactive
                                DetoxDroidState.Paused -> R.string.home_state_paused
                            }, if (brakeEndsAt != null) {
                                brakeEndsAt
                            } else if (detoxDroidState == DetoxDroidState.Paused) {
                                // show the actual end of the running pause, not "now + duration"
                                PauseButtonFeature.endsAtText()
                            } else ""
                        ), style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = { navViewModel.openRoute(NavigationRoutes.About) }) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(id = R.string.navigation_about)
                )
            }
        },
    )
}

/**
 * A [ExtendedFloatingActionButton] that starts or stops DetoxDroid when clicked.
 * Shows a lock icon when Commitment Password is active and the session is locked.
 */
@Composable
private fun StartStopActionButton(
    detoxDroidState: DetoxDroidState, homeViewModel: HomeViewModel = viewModel()
) {
    // observed so the lock badge updates when the commitment password is (un)locked
    val commitmentPasswordStateToken = CommitmentPasswordFeature.stateToken.collectAsState().value
    val cpLocked = remember(commitmentPasswordStateToken) {
        CommitmentPasswordFeature.isActivated && !CommitmentPasswordFeature.isSessionUnlocked()
    }
    val accessibilityConsent = rememberAccessibilityConsent()

    ExtendedFloatingActionButton(
        text = {
            Text(
                text = stringResource(
                    id = if (detoxDroidState != DetoxDroidState.Inactive) R.string.home_stop
                    else R.string.home_start
                )
            )
        },
        icon = {
            if (cpLocked && detoxDroidState == DetoxDroidState.Active) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_lock),
                    contentDescription = stringResource(R.string.feature_commitmentPassword_lockedBadge)
                )
            } else {
                Icon(
                    painter = if (detoxDroidState != DetoxDroidState.Inactive) painterResource(
                        id = R.drawable.ic_stop
                    ) else painterResource(
                        id = R.drawable.ic_start
                    ), contentDescription = "Run/Stop DetoxDroid"
                )
            }
        },
        onClick = {
            if (detoxDroidState == DetoxDroidState.Inactive) {
                val justAgreed = !AccessibilityDisclosureSupport.isAccepted
                accessibilityConsent.ask { homeViewModel.toggleDetoxDroidIsRunning(justAgreed) }
            } else {
                homeViewModel.toggleDetoxDroidIsRunning()
            }
        }
    )
}

/**
 * The content of the home screen. It displays the screen time chart and a list of all features.
 */
@Composable
private fun HomeScreenContent(it: PaddingValues, droidState: SleepyDroidState) {
    LazyColumn(
        modifier = Modifier.padding(it),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ScreenTimeChart()
        }
        item {
            ForceStoppedCard()
            FinishSetupCard()
            BatteryOptimizationCard()
        }
        item {
            RuleGroupsSection()
        }
        item {
            SectionHeader(title = stringResource(id = R.string.home_section_more))
            SettingsGroup {
                // the step features are switched on and off by the groups and reached from them
                FeaturesProvider.featureList
                    .filter { feature -> Step.entries.none { it.feature == feature } }
                    .forEach { OpenFeatureTile(feature = it) }
            }
            SettingsGroup {
                UninstallDetoxDroidTile()
                OpenAboutTile()
            }
            // bottom logo, with room below it for the start/stop button to float over
            SleepyDroid(
                state = droidState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 80.dp)
            )
        }
    }
}

/**
 * A [androidx.compose.material3.ListItem] that opens the feature settings screen when clicked.
 * It also displays the current state of the feature (activated or not).
 */
@Composable
fun OpenFeatureTile(
    feature: Feature,
    navViewModel: NavViewModel = viewModel(viewModelStoreOwner = LocalActivity.current as ComponentActivity)
) {
    HomeTile(
        title = stringResource(id = feature.texts.title),
        subtitle = stringResource(id = feature.texts.subtitle),
        icon = painterResource(id = feature.iconRes),
        isActivated = feature.isActivated,
        onClick = {
            navViewModel.openRoute(NavigationRoutes.ManageFeature(featureId = feature.id))
        }
    )
}

/**
 * A row of the home list. Every row carries its icon on a round badge, so all their texts start
 * on the same line.
 */
@Composable
private fun HomeTile(
    title: String,
    subtitle: String,
    icon: Painter,
    onClick: () -> Unit,
    isActivated: Boolean = false,
    enabled: Boolean = true,
) {
    androidx.compose.material3.ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { IconBadge(icon = icon, isActivated = isActivated) },
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, onClick = onClick)
    )
}

/**
 * An icon on a round badge. While the feature it stands for is on, the badge takes the accent
 * color and wears the green dot that also marks DetoxDroid as running.
 */
@Composable
private fun IconBadge(icon: Painter, isActivated: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(modifier = Modifier.size(40.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = if (isActivated) {
                        colors.primaryContainer
                    } else {
                        colors.surfaceContainerHighest
                    },
                    shape = CircleShape
                )
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = if (isActivated) colors.onPrimaryContainer else colors.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
        if (isActivated) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(12.dp)
                    // a ring in the row's color cuts the dot out of the badge
                    .border(2.dp, colors.surfaceContainer, CircleShape)
                    .padding(2.dp)
                    .background(colorResource(id = R.color.green), CircleShape)
            )
        }
    }
}

/**
 * A tile that uninstalls DetoxDroid when clicked. It is only shown if the device admin permission
 * is granted. A confirmation dialog is shown when before revoking the device admin permission.
 */
@Composable
fun UninstallDetoxDroidTile(viewModel: HomeViewModel = viewModel()) {
    // don't show the uninstall tile if the device admin permission is not granted
    if (!DetoxDroidDeviceAdminReceiver.isGranted(LocalContext.current)) return

    val showAreYouSureDialog = remember { mutableStateOf(false) }
    val commitmentPasswordStateToken = CommitmentPasswordFeature.stateToken.collectAsState().value
    val uninstallBlocked = remember(commitmentPasswordStateToken) { CommitmentPasswordFeature.isActivated }

    LaunchedEffect(uninstallBlocked) {
        if (uninstallBlocked) showAreYouSureDialog.value = false
    }

    if (showAreYouSureDialog.value) {
        // confirmation dialog
        AlertDialog(title = {
            Text(text = stringResource(id = R.string.home_uninstall_dialog_title))
        }, text = {
            Text(text = stringResource(id = R.string.home_uninstall_dialog_message))
        }, onDismissRequest = {
            showAreYouSureDialog.value = false
        }, confirmButton = {
            TextButton(onClick = {
                viewModel.uninstallDetoxDroid()
                showAreYouSureDialog.value = false
            }) {
                Text(text = stringResource(id = R.string.home_uninstall))
            }
        })
    }

    HomeTile(
        title = stringResource(id = R.string.home_uninstall),
        subtitle = stringResource(
            id = if (uninstallBlocked) R.string.home_uninstall_blocked_hint else R.string.home_uninstall_hint
        ),
        icon = rememberVectorPainter(Icons.Default.DeleteForever),
        enabled = !uninstallBlocked,
        onClick = {
            showAreYouSureDialog.value = true
        })
}

@Composable
fun OpenAboutTile(navViewModel: NavViewModel = NavViewModel.navViewModel()) {
    HomeTile(
        title = stringResource(id = R.string.navigation_about),
        subtitle = stringResource(id = R.string.about_tile_subtitle),
        icon = rememberVectorPainter(Icons.Default.Settings),
        onClick = { navViewModel.openRoute(NavigationRoutes.About) }
    )
}

/**
 * Today's screen time on the home screen: a [ScreenTimeDonut] of the most used apps, or a
 * [ScreenTimeHint] where there is nothing to draw. The numbers are those of the usage screen and
 * the widget, apps left out there included.
 */
@Composable
fun ScreenTimeChart(navViewModel: NavViewModel = NavViewModel.navViewModel()) {
    // read again whenever the user returns to the screen, and only then: a lifecycle event on
    // the way out (pausing, stopping) is no reason to read the records
    val lifecycleState = LocalLifecycleOwner.current.lifecycle.observeAsState().value
    var resumes by remember { mutableIntStateOf(0) }
    LaunchedEffect(lifecycleState) { if (lifecycleState == Lifecycle.Event.ON_RESUME) resumes++ }

    val context = LocalContext.current
    // Starts from what the widget and the notification read last, so the screen below does not
    // jump once the numbers are in, then reads afresh off the main thread, never on ordinary
    // recompositions such as slice taps.
    val cached = remember { ScreenTimeSurfaces.today?.let { TodayDonut.of(context, it) } }
    val today by produceState(cached, resumes) {
        value = withContext(Dispatchers.IO) { TodayDonut.read(context) } ?: value
    }
    // nothing read yet: better nothing than a hint that may turn out wrong a moment later
    val current = today ?: return
    if (current.apps.isEmpty()) {
        ScreenTimeHint(current, lifecycleState, navViewModel)
    } else {
        ScreenTimeDonut(current, navViewModel)
    }
}

/** The ring of today's most used apps, with the total in its middle. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScreenTimeDonut(today: TodayDonut, navViewModel: NavViewModel) {
    val context = LocalContext.current
    val selectedIndex = remember { mutableStateOf(-1) }

    val chartStats = today.apps
    val screenTime = today.screenTimeMs
    val colors = listOf(
        colorResource(id = R.color.pink),
        colorResource(id = R.color.orange),
        colorResource(id = R.color.yellow),
        colorResource(id = R.color.green),
        colorResource(id = R.color.blue),
        colorResource(id = R.color.purple),
    )
    val otherLabel = stringResource(id = R.string.appCategories_other)
    val otherTime =
        (screenTime - chartStats.sumOf { it.second }).toFloat().coerceAtLeast(1f)
    val slices = chartStats.map { (label, timeMs) -> label to timeMs.toFloat() }
        .plus(otherLabel to otherTime)

    val totalValue = slices.fold(0f) { acc, pair -> acc + pair.second }.coerceAtLeast(1f)

    val sweepAngles = slices.map { (it.second / totalValue) * 360f }

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .size(200.dp)
                    // keyed on the slice angles: tap detection must not keep using a stale
                    // capture after the stats refresh
                    .pointerInput(sweepAngles) {
                        detectTapGestures { offset ->
                            val canvasSize = this.size
                            val centerX = canvasSize.width / 2f
                            val centerY = canvasSize.height / 2f
                            val dx = offset.x - centerX
                            val dy = offset.y - centerY
                            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                            val strokePx = 32.dp.toPx()
                            val minDim = if (canvasSize.width < canvasSize.height) canvasSize.width else canvasSize.height
                            val outerRadius = (minDim - strokePx) / 2f
                            val innerRadius = outerRadius - strokePx
                            if (dist >= innerRadius && dist <= outerRadius) {
                                var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                                if (angle < 0f) angle += 360f
                                var cumAngle = 0f
                                var found = -1
                                for (i in sweepAngles.indices) {
                                    cumAngle += sweepAngles[i]
                                    if (angle < cumAngle) {
                                        found = i
                                        break
                                    }
                                }
                                selectedIndex.value = if (found == selectedIndex.value) -1 else found
                            } else {
                                selectedIndex.value = -1
                            }
                        }
                    }
            ) {
                val strokeWidth = 32.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                val topLeft = Offset(
                    (size.width - radius * 2) / 2,
                    (size.height - radius * 2) / 2
                )
                // the slices are set apart by a gap a few dp wide, as an angle on the ring
                val gapAngle = Math.toDegrees(3.dp.toPx() / radius.toDouble()).toFloat()
                var startAngle = -90f
                slices.forEachIndexed { index, _ ->
                    val sweepAngle = sweepAngles[index]
                    drawArc(
                        color = colors[index % colors.size].let { color ->
                            if (selectedIndex.value == index) color.copy(alpha = 0.6f) else color
                        },
                        startAngle = startAngle + gapAngle / 2,
                        sweepAngle = (sweepAngle - gapAngle).coerceAtLeast(0.5f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = strokeWidth)
                    )
                    startAngle = startAngle + sweepAngle
                }
            }
            // no wider than the hole in the ring, so a long time or app name wraps inside it
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.widthIn(max = 128.dp)
            ) {
                val idx = selectedIndex.value
                if (idx >= 0 && idx < chartStats.count()) {
                    Text(
                        text = chartStats[idx].second.milliseconds.toHrMinString(context),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = slices[idx].first,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                } else if (idx == chartStats.count()) {
                    Text(
                        text = otherTime.toLong().milliseconds.toHrMinString(context),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = otherLabel,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = screenTime.milliseconds.toHrMinString(context),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(id = R.string.home_screenTime_today),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            slices.forEachIndexed { index, slice ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { selectedIndex.value = if (selectedIndex.value == index) -1 else index }
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .then(
                                Modifier.drawBehind {
                                    drawCircle(color = colors[index % colors.size])
                                }
                            )
                    )
                    Text(
                        text = slice.first,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = {
                navViewModel.openRoute(NavigationRoutes.UsageStats())
            }) {
                Text(stringResource(id = R.string.usageStats_more))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Stands in for the donut when it has nothing to draw: one quiet line on why, and the one thing
 * that can be done about it, where there is one. It takes about the room the ring takes, so the
 * groups below sit where they always sit, and stays faint: the home screen is about them.
 */
@Composable
private fun ScreenTimeHint(today: TodayDonut, refreshKey: Any?, navViewModel: NavViewModel) {
    val context = LocalContext.current
    // No data is not the same thing as no permission. Offering "Grant Permission" to somebody who
    // granted it long ago sends them to a settings page that already says yes, and leaves them
    // with nothing to try.
    val hasUsageAccess = remember(refreshKey) { UsageAccessUtil.hasUsageAccess(context) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp)
            .padding(horizontal = 40.dp, vertical = 16.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.QueryStats,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .padding(bottom = 12.dp)
                .size(36.dp)
        )
        when {
            // Everything used today is left out. The usage screen is where that is undone,
            // so the way there must not go with the numbers.
            today.leftOutMs > 0L -> {
                HintText(R.string.home_screenTime_allLeftOut)
                HintAction(R.string.home_screenTime_allLeftOut_action) {
                    navViewModel.openRoute(NavigationRoutes.UsageStats(showExclusions = true))
                }
            }

            hasUsageAccess -> HintText(R.string.home_screenTime_unavailable)

            else -> {
                HintText(R.string.usageStats_usageAccessRequired)
                HintAction(R.string.action_grantPermission) {
                    NavigationUtil.openUsageAccessSettings(context)
                }
                if (RestrictedSettingsUtil.mayBlockSpecialAccess(context)) {
                    HintText(
                        R.string.usageStats_usageAccess_restrictedSettingsHint,
                        MaterialTheme.typography.bodySmall
                    )
                    HintAction(R.string.action_openAppInfo) {
                        NavigationUtil.openAppInfoSettings(context)
                    }
                }
            }
        }
    }
}

@Composable
private fun HintText(
    @StringRes textRes: Int,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    Text(
        text = stringResource(id = textRes),
        style = style,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

/** The one thing to do about it, centered under the line like the line itself. */
@Composable
private fun HintAction(@StringRes labelRes: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(text = stringResource(id = labelRes))
    }
}

/** Today's total and its most used apps by name, as the donut shows them. */
private class TodayDonut(
    val screenTimeMs: Long,
    val apps: List<Pair<String, Long>>,
    /** Time of the apps left out on the usage screen. */
    val leftOutMs: Long,
) {
    companion object {
        /** Blocking: reads the usage records and the app names. Null where they can't be read. */
        fun read(context: Context): TodayDonut? = runCatching {
            TodaySummary.read(context, detailed = false)
        }.onFailure {
            Timber.w(it, "Could not read today's screen time")
        }.getOrNull()?.let { of(context, it) }

        /** Looks up the app names: a package manager call per app. */
        fun of(context: Context, summary: TodaySummary) = TodayDonut(
            screenTimeMs = summary.screenTimeMs,
            apps = summary.topApps.map { context.appLabel(it.packageName) to it.screenTimeMs },
            leftOutMs = summary.leftOutMs,
        )
    }
}
