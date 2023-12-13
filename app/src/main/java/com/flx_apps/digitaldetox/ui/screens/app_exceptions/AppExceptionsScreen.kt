import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FilterChip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.feature_types.AppExceptionListType
import com.flx_apps.digitaldetox.feature_types.FeatureId
import com.flx_apps.digitaldetox.feature_types.SupportsAppExceptionsFeature
import com.flx_apps.digitaldetox.ui.screens.app_exceptions.AppExceptionItem
import com.flx_apps.digitaldetox.ui.screens.app_exceptions.AppExceptionsViewModel
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.ui.widgets.Center
import com.flx_apps.digitaldetox.ui.widgets.InfoCard
import kotlinx.coroutines.runBlocking

/**
 * This screen allows the user to manage the app exceptions for a feature.
 *
 * It displays a list of all installed apps that are either not affected or only affected by the
 * feature (depending on whether the user sets the exceptions as "›blocklisted" or "allowlisted").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAppExceptionsScreen(
    featureId: FeatureId,
    navViewModel: NavViewModel = NavViewModel.navViewModel(),
    appExceptionsViewModel: AppExceptionsViewModel = AppExceptionsViewModel.withFeatureId(featureId),
) {
    var showSearchBar by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        TopAppBar(navigationIcon = {
            IconButton(onClick = {
                navViewModel.onBackPress()
            }) {
                Icon(
                    imageVector = Icons.Default.ArrowBack, contentDescription = "Back"
                )
            }
        }, title = {
            AnimatedContent(targetState = showSearchBar, label = "ToggleSearchBar") {
                AnimatedVisibility(visible = it) {
                    SearchBar(query = appExceptionsViewModel.query.value ?: "",
                        onQueryChange = { query ->
                            appExceptionsViewModel.filterApps(query)
                        },
                        onSearch = {},
                        active = false,
                        onActiveChange = {},
                        trailingIcon = {
                            IconButton(onClick = {
                                showSearchBar = !showSearchBar
                                if (!showSearchBar) {
                                    appExceptionsViewModel.filterApps("")
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Close, contentDescription = "Close"
                                )
                            }
                        }) {}
                }
                AnimatedVisibility(visible = !it) {
                    Text(stringResource(id = R.string.feature_settings_exceptions))
                }
            }
        }, actions = {
            AnimatedContent(targetState = showSearchBar, label = "ToggleSearchBar") {
                AnimatedVisibility(visible = !it) {
                    IconButton(onClick = {
                        showSearchBar = !showSearchBar
                    }) {
                        Icon(
                            imageVector = Icons.Default.Search, contentDescription = "Search"
                        )
                    }
                }
            }
            IconButton(onClick = { appExceptionsViewModel.setShowListSettingsSheet(true) }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Open Exception List Settings"
                )
            }
        })
    }) {
        if (appExceptionsViewModel.showListSettingsSheet.collectAsState().value) {
            AppExceptionsListSettingsSheet()
        }
        Box(modifier = Modifier.padding(paddingValues = it)) {
            // Content of the screen
            InstalledAppsList()
        }
    }
}

/**
 * Displays a list of all installed apps that are either not affected or only affected by the
 * feature (depending on whether the user sets the exceptions as "blocklisted" or "allowlisted").
 */
@Composable
fun InstalledAppsList(
    appExceptionsViewModel: AppExceptionsViewModel = viewModel()
) {
    val appExceptions = appExceptionsViewModel.appExceptionItems.collectAsState().value
    if (appExceptions.isNullOrEmpty()) {
        Center {
            if (appExceptions == null) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
            } else {
                Text(stringResource(id = R.string.feature_settings_exceptions_empty))
            }
        }
    } else {
        LazyColumn {
            item {
                InfoCard(infoText = stringResource(id = R.string.feature_settings_exceptions_description))
            }
            items(appExceptions) { appException ->
                AppExceptionListItem(appException)
            }
        }
    }
}

/**
 * Displays an app exception item. It contains the app icon, name and a switch to toggle the
 * exception state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppExceptionListItem(
    item: AppExceptionItem, appExceptionsViewModel: AppExceptionsViewModel = viewModel()
) {
    // load app icon
    val packageManager = LocalContext.current.packageManager

    val appIcon = try {
        packageManager.getApplicationIcon(item.packageName)
    } catch (e: PackageManager.NameNotFoundException) {
        // icon could not be loaded
        // this is usually the case for apps that are managed by a work profile
        null // we return null here, so that no icon is displayed
    }
    val checkedState = remember {
        mutableStateOf(item.isException)
    }
    androidx.compose.material3.ListItem(headlineContent = { Text(item.appName) },
        supportingContent = {
            // The supporting content contains the app category and whether the app is a system app
            // or a user app.
            Row {
                if (item.isSystemApp) {
                    Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                        Text(text = "System", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                        Text(text = "User", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (item.appCategory.isNotBlank()) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(
                            text = item.appCategory,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        trailingContent = {
            Checkbox(checked = checkedState.value, onCheckedChange = {
                runBlocking {
                    checkedState.value = appExceptionsViewModel.toggleAppException(item.packageName)
                        ?: checkedState.value
                }
            })
        },
        leadingContent = {
            if (appIcon != null) {
                Image(
                    bitmap = appIcon.toBitmap(128, 128).asImageBitmap(),
                    contentDescription = "App Icon",
                    modifier = Modifier.size(48.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = "App Icon Placeholder",
                    modifier = Modifier.size(48.dp)
                )
            }
        })
}

/**
 * The [AppExceptionsListSettingsSheet] provides methods to filter the list by system/user apps and
 * by the app category. It also allows the user to select whether the list should be treated as
 * "blocklist" or "allowlist".
 */
@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class, ExperimentalLayoutApi::class
)
@Composable
fun AppExceptionsListSettingsSheet(
    viewModel: AppExceptionsViewModel = viewModel()
) {
    ModalBottomSheet(onDismissRequest = {
        viewModel.setShowListSettingsSheet(false)
    }, sheetState = rememberModalBottomSheetState()) {
        Column {
            androidx.compose.material3.ListItem(headlineContent = { Text(stringResource(id = R.string.feature_settings_exceptions_filterByAppType)) },
                supportingContent = {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(selected = viewModel.showSystemApps.collectAsState().value,
                            onClick = { viewModel.toggleShowSystemApps() }) {
                            Text(text = "System apps")
                        }
                        FilterChip(selected = viewModel.showUserApps.collectAsState().value,
                            onClick = { viewModel.toggleShowUserApps() }) {
                            Text(text = "User apps")
                        }
                    }
                })
            androidx.compose.material3.ListItem(headlineContent = { Text(stringResource(id = R.string.feature_settings_exceptions_filterByCategory)) },
                supportingContent = {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        viewModel.selectedAppCategories.collectAsState().value.forEach {
                            FilterChip(
                                onClick = { viewModel.toggleAppCategory(it.key) },
                                selected = it.value
                            ) {
                                Text(text = it.key)
                            }
                        }
                    }
                })
            val listTypeMap = mapOf(
                R.string.feature_settings_exceptions_listType_notList to AppExceptionListType.NOT_LIST,
                R.string.feature_settings_exceptions_listType_onlyList to AppExceptionListType.ONLY_LIST
            ).filter { (viewModel.feature as SupportsAppExceptionsFeature).listTypes.contains(it.value) }
            if (listTypeMap.size > 1) {
                androidx.compose.material3.ListItem(headlineContent = { Text(stringResource(id = R.string.feature_settings_exceptions_listType)) },
                    supportingContent = {
                        Column {
                            Text(text = stringResource(id = R.string.feature_settings_exceptions_listType_description))
                            OptionsRow(options = listTypeMap,
                                selectedOption = viewModel.exceptionListType.collectAsState().value,
                                onOptionSelected = { viewModel.setExceptionListType(it as AppExceptionListType) })
                        }
                    })
            }
            // FIXME there should be a better way to do this, e.g. using Modifier.navigationBarsPadding(),
            //  but I couldn't get it to work for some reason
            Box(modifier = Modifier.height(48.dp))
        }
    }
}
