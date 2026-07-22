package com.flx_apps.digitaldetox.ui.screens.home

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flx_apps.digitaldetox.BuildConfig
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.feature_types.Feature
import com.flx_apps.digitaldetox.features.CommitmentPasswordFeature
import com.flx_apps.digitaldetox.features.FeaturesProvider
import com.flx_apps.digitaldetox.features.PauseButtonFeature
import com.flx_apps.digitaldetox.system_integration.DetoxDroidDeviceAdminReceiver
import com.flx_apps.digitaldetox.system_integration.DetoxDroidState
import com.flx_apps.digitaldetox.system_integration.UsageStatsProvider
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavigationRoutes
import com.flx_apps.digitaldetox.ui.widgets.InfoCard
import com.flx_apps.digitaldetox.ui.widgets.SimpleListTile
import com.flx_apps.digitaldetox.ui.widgets.StatusIndicator
import com.flx_apps.digitaldetox.util.NavigationUtil
import com.flx_apps.digitaldetox.util.observeAsState
import com.flx_apps.digitaldetox.util.toHrMinString
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.atan2
import kotlin.time.Duration.Companion.milliseconds

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
    Scaffold(snackbarHost = {
        SnackbarHost(hostState = snackbarHostState)
    }, topBar = {
        AppBar(detoxDroidState)
    }, floatingActionButton = {
        StartStopActionButton(detoxDroidState)
    }) {
        HomeScreenContent(it)
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
    TopAppBar(
        title = {
            Column {
                Text(text = stringResource(id = R.string.app_name_))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusIndicator(
                        indicatorColor = colorResource(
                            id = when (detoxDroidState) {
                                DetoxDroidState.Active -> R.color.green
                                DetoxDroidState.Inactive -> R.color.gray
                                DetoxDroidState.Paused -> R.color.yellow
                            }
                        )
                    )
                    Text(
                        text = stringResource(
                            id = when (detoxDroidState) {
                                DetoxDroidState.Active -> R.string.home_state_active
                                DetoxDroidState.Inactive -> R.string.home_state_inactive
                                DetoxDroidState.Paused -> R.string.home_state_paused
                            }, if (detoxDroidState == DetoxDroidState.Paused) {
                                // show the actual end of the running pause, not "now + duration"
                                Instant.ofEpochMilli(PauseButtonFeature.pauseUntil)
                                    .atZone(ZoneId.systemDefault()).toLocalTime().format(
                                        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                                    )
                            } else ""
                        ), style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
        actions = {
            if (BuildConfig.DEBUG) {
                IconButton(onClick = { navViewModel.openRoute(NavigationRoutes.LogViewer) }) {
                    Icon(Icons.Default.FilterList, contentDescription = "View Logs")
                }
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
        onClick = { homeViewModel.toggleDetoxDroidIsRunning() }
    )
}

/**
 * The content of the home screen. It displays the screen time chart and a list of all features.
 */
@Composable
private fun HomeScreenContent(it: PaddingValues) {
    LazyColumn(
        modifier = Modifier.padding(it),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ScreenTimeChart()
            InfoCard(infoText = stringResource(id = R.string.home_hint))
        }
        item {
            FinishSetupCard()
        }
        items(FeaturesProvider.featureList) { feature ->
            OpenFeatureTile(feature = feature)
        }
        item {
            UninstallDetoxDroidTile()
            OpenAboutTile()
            // bottom logo
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "Logo",
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(76.dp)
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
    androidx.compose.material3.ListItem(
        headlineContent = { Text(stringResource(id = feature.texts.title)) },
        supportingContent = {
            Text(
                stringResource(
                    id = feature.texts.subtitle
                )
            )
        },
        leadingContent = {
            Icon(
                painter = painterResource(id = feature.iconRes),
                contentDescription = "Feature Icon",
            )
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxHeight()
            ) {
                if (feature.isActivated) {
                    StatusIndicator(indicatorColor = colorResource(id = R.color.green))
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = "Open feature settings"
                )
            }
        },
        modifier = Modifier.clickable {
            navViewModel.openRoute(
                NavigationRoutes.ManageFeature(featureId = feature.id)
            )
        })
    return
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

    Divider()
    SimpleListTile(
        leadingIcon = Icons.Default.DeleteForever,
        titleText = stringResource(id = R.string.home_uninstall),
        subtitleText = stringResource(
            id = if (uninstallBlocked) R.string.home_uninstall_blocked_hint else R.string.home_uninstall_hint
        ),
        enabled = !uninstallBlocked,
        onClick = {
            showAreYouSureDialog.value = true
        })
    Divider()
}

@Composable
fun OpenAboutTile(navViewModel: NavViewModel = NavViewModel.navViewModel()) {
    SimpleListTile(
        leadingIcon = Icons.Default.Info,
        titleText = stringResource(id = R.string.navigation_about),
        subtitleText = stringResource(id = R.string.about_tile_subtitle),
        onClick = { navViewModel.openRoute(NavigationRoutes.About) }
    )
}

/**
 * A [DonutPieChart] that displays the screen time of the current day and the apps that were used
 * today.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScreenTimeChart(navViewModel: NavViewModel = NavViewModel.navViewModel()) {
    // observed so the chart re-renders when the user returns to the screen
    val lifecycleState = LocalLifecycleOwner.current.lifecycle.observeAsState().value

    val context = LocalContext.current
    // keyed on the lifecycle state: refresh when the user returns to the screen, but never
    // re-query the (blocking) UsageStatsManager on ordinary recompositions such as slice taps
    val stats = remember(lifecycleState) { UsageStatsProvider.getUpdatedUsageStatsToday() }
    val selectedIndex = remember { mutableStateOf(-1) }

    val chartStats = stats.values.sortedByDescending { it.totalTimeInForeground }.take(5)
    val screenTime = stats.values.sumOf { it.totalTimeInForeground }
    val colors = listOf(
        colorResource(id = R.color.pink),
        colorResource(id = R.color.orange),
        colorResource(id = R.color.yellow),
        colorResource(id = R.color.green),
        colorResource(id = R.color.blue),
        colorResource(id = R.color.purple),
    )
    val packageManager = LocalContext.current.packageManager
    val otherLabel = stringResource(id = R.string.usage_stats_other)
    val otherTime =
        (screenTime - chartStats.sumOf { it.totalTimeInForeground }.toFloat()).coerceAtLeast(1f)

    // apps can disappear from PackageManager while still being present in today's usage stats
    val slices = chartStats.map { appStats ->
        val label = runCatching {
            packageManager.getApplicationInfo(appStats.packageName, 0)
                .loadLabel(packageManager).toString()
        }.getOrDefault(appStats.packageName)
        label to appStats.totalTimeInForeground.toFloat()
    }.plus(otherLabel to otherTime)

    val totalValue = slices.fold(0f) { acc, pair -> acc + pair.second }.coerceAtLeast(1f)

    val sweepAngles = slices.map { (it.second / totalValue) * 360f }

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (stats.isNotEmpty()) {
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
                    var startAngle = -90f
                    slices.forEachIndexed { index, _ ->
                        val sweepAngle = sweepAngles[index]
                        drawArc(
                            color = colors[index % colors.size].let { color ->
                                if (selectedIndex.value == index) color.copy(alpha = 0.6f) else color
                            },
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        startAngle = startAngle + sweepAngle
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (stats.isNotEmpty()) {
                    val idx = selectedIndex.value
                    if (idx >= 0 && idx < chartStats.count()) {
                        val selectedStat = chartStats[idx]
                        Text(
                            text = selectedStat.totalTimeInForeground.milliseconds.toHrMinString(context),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = slices[idx].first,
                            style = MaterialTheme.typography.labelSmall
                        )
                    } else if (idx == chartStats.count()) {
                        Text(
                            text = otherTime.toLong().milliseconds.toHrMinString(context),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = otherLabel, style = MaterialTheme.typography.labelSmall
                        )
                    } else {
                        Text(
                            text = screenTime.milliseconds.toHrMinString(context),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = stringResource(id = R.string.home_screenTime_today),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                } else {
                    Text(
                        text = stringResource(id = R.string.home_screenTime_unavailable),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    OutlinedButton(onClick = { NavigationUtil.openUsageAccessSettings(context) }) {
                        Text(text = stringResource(id = R.string.action_grantPermission))
                    }
                }
            }
        }
        if (slices.isNotEmpty()) {
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
        }
        if (stats.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = {
                    navViewModel.openRoute(NavigationRoutes.UsageStats)
                }) {
                    Text(stringResource(id = R.string.usage_stats_more))
                    Icon(
                        Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}