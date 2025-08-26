package com.flx_apps.digitaldetox.ui.screens.home

import StatusIndicator
import android.annotation.SuppressLint
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.yml.charts.common.components.Legends
import co.yml.charts.common.model.AccessibilityConfig
import co.yml.charts.common.model.PlotType
import co.yml.charts.common.utils.DataUtils
import co.yml.charts.ui.piechart.charts.DonutPieChart
import co.yml.charts.ui.piechart.models.PieChartConfig
import co.yml.charts.ui.piechart.models.PieChartData
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.feature_types.Feature
import com.flx_apps.digitaldetox.features.FeaturesProvider
import com.flx_apps.digitaldetox.features.PauseButtonFeature
import com.flx_apps.digitaldetox.system_integration.DetoxDroidDeviceAdminReceiver
import com.flx_apps.digitaldetox.system_integration.DetoxDroidState
import com.flx_apps.digitaldetox.system_integration.UsageStatsProvider
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavigationRoutes
import com.flx_apps.digitaldetox.ui.widgets.InfoCard
import com.flx_apps.digitaldetox.ui.widgets.SimpleListTile
import com.flx_apps.digitaldetox.util.NavigationUtil
import com.flx_apps.digitaldetox.util.observeAsState
import com.flx_apps.digitaldetox.util.toHrMinString
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
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
        // show snackbar to request the write secure settings permission
        LaunchedEffect(key1 = "", block = {
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.action_requestPermissions),
                actionLabel = context.getString(R.string.action_go),
            )
            if (result == SnackbarResult.ActionPerformed) {
                homeViewModel.setSnackbarState(HomeScreenSnackbarState.Hidden)
                NavigationUtil.openAccessibilitySettings(context)
            }
        })
    }
}

/**
 * The top app bar of the home screen. It displays the current state of DetoxDroid.
 * @see DetoxDroidState
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AppBar(detoxDroidState: DetoxDroidState) {
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
                                LocalDateTime.now().plus(
                                    PauseButtonFeature.pauseDuration, ChronoUnit.MILLIS
                                ).format(
                                    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                                )
                            } else ""
                        ), style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
    )
}

/**
 * A [ExtendedFloatingActionButton] that starts or stops DetoxDroid when clicked.
 */
@Composable
private fun StartStopActionButton(
    detoxDroidState: DetoxDroidState, homeViewModel: HomeViewModel = viewModel()
) {
    ExtendedFloatingActionButton(
        text = { Text(text = stringResource(id = if (detoxDroidState != DetoxDroidState.Inactive) R.string.home_stop else R.string.home_start)) },
        icon = {
            Icon(
                painter = if (detoxDroidState != DetoxDroidState.Inactive) painterResource(
                    id = R.drawable.ic_stop
                ) else painterResource(
                    id = R.drawable.ic_start
                ), contentDescription = "Run/Stop DetoxDroid"
            )
        },
        onClick = {
            homeViewModel.toggleDetoxDroidIsRunning()
        })
}

/**
 * The content of the home screen. It displays the screen time chart and a list of all features.
 */
@Composable
private fun HomeScreenContent(it: PaddingValues, viewModel: HomeViewModel = viewModel()) {
    LazyColumn(
        modifier = Modifier.padding(it),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ScreenTimeChart()
            InfoCard(infoText = stringResource(id = R.string.home_hint))
        }
        items(FeaturesProvider.featureList) { feature ->
            OpenFeatureTile(feature = feature)
        }
        item {
            UninstallDetoxDroidTile()
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
            Row(verticalAlignment = Alignment.CenterVertically) {
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

    val showAreYouSureDialog = remember { MutableStateFlow(false) }
    if (showAreYouSureDialog.collectAsState().value) {
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
            id = R.string.home_uninstall_hint
        ),
        onClick = {
            showAreYouSureDialog.value = true
        })
    Divider()
}

/**
 * A [DonutPieChart] that displays the screen time of the current day and the apps that were used
 * today.
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ScreenTimeChart(navViewModel: NavViewModel = NavViewModel.navViewModel()) {
    // TODO simple workaround to re-render the chart when the lifecycle state changes; it would
    //  perhaps be better to use rememberLauncherForActivityResult to request the usage stats
    @Suppress("unused", "UnusedVariable") val lifecycleState =
        LocalLifecycleOwner.current.lifecycle.observeAsState().value

    val context = LocalContext.current
    val stats = UsageStatsProvider.getUpdatedUsageStatsToday()
    val selectedSlice = remember { mutableStateOf<PieChartData.Slice?>(null) }


    // only show the top 5 apps in the chart
    val chartStats = stats.values.sortedByDescending { it.totalTimeInForeground }.take(5)
    val screenTime = stats.values.sumOf { it.totalTimeInForeground }
    val donutChartConfig = PieChartConfig(
        strokeWidth = 32f,
        activeSliceAlpha = .9f,
        isAnimationEnable = true,
        backgroundColor = Color.Transparent,
        isSumVisible = false,
        accessibilityConfig = AccessibilityConfig(
            chartDescription = "Screen time today: ${screenTime / 1000 / 60} minutes"
        ),
    )
    val colors = listOf(
        colorResource(id = R.color.pink),
        colorResource(id = R.color.orange),
        colorResource(id = R.color.yellow),
        colorResource(id = R.color.green),
        colorResource(id = R.color.blue),
        colorResource(id = R.color.purple),
    )
    val packageManager = LocalContext.current.packageManager
    val otherTime =
        (screenTime - chartStats.sumOf { it.totalTimeInForeground }.toFloat()).coerceAtLeast(1f);
    val donutChartData = PieChartData(
        slices = chartStats.mapIndexed { index, appStats ->
            PieChartData.Slice(
                packageManager.getApplicationInfo(appStats.packageName, 0).loadLabel(packageManager)
                    .toString(),
                appStats.totalTimeInForeground.toFloat(),
                color = colors[index % colors.size]
            )
        }.plus(
            PieChartData.Slice(
                "Other", otherTime, color = colors[colors.size - 1]
            )
        ), plotType = PlotType.Donut
    )
    Column {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center
        ) {
            DonutPieChart(
                modifier = Modifier
                    .fillMaxSize(fraction = 0.75f)
                    .padding(16.dp),
                pieChartData = donutChartData,
                pieChartConfig = donutChartConfig,
                onSliceClick = {
                    if (selectedSlice.value == it) selectedSlice.value = null
                    else selectedSlice.value = it
                })
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (stats.isNotEmpty()) {
                    if (selectedSlice.value != null) {
                        val selectedIndex = donutChartData.slices.indexOf(selectedSlice.value)
                        if (selectedIndex >= 0 && selectedIndex < chartStats.count()) {
                            val selectedStat = chartStats[selectedIndex]
                            Text(
                                text = selectedStat.totalTimeInForeground.milliseconds.toHrMinString(),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = packageManager.getApplicationInfo(
                                    selectedStat.packageName,
                                    0
                                ).loadLabel(packageManager).toString(),
                                style = MaterialTheme.typography.labelSmall
                            )
                        } else {
                            Text(
                                text = otherTime.toLong().milliseconds.toHrMinString(),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Other", style = MaterialTheme.typography.labelSmall
                            )
                        }
                    } else {
                        Text(
                            text = screenTime.milliseconds.toHrMinString(),
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
        if (chartStats.isNotEmpty()) {
            Legends(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .heightIn(min = 100.dp, max = 200.dp),
                legendsConfig = DataUtils.getLegendsConfigFromPieChartData(donutChartData, 3)
            )
        }
    }
}