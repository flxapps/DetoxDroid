package com.flx_apps.digitaldetox.widgets.minimal_launcher

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.ui.theme.DetoxDroidTheme
import com.flx_apps.digitaldetox.ui.widgets.apps.AppSelectionListItem
import com.flx_apps.digitaldetox.ui.widgets.apps.AppSelectionTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class AppLoadState(
    val isLoading: Boolean,
    val apps: List<LaunchableAppInfo>,
)

private data class SelectedAppWithInfo(
    val selectedApp: WidgetSelectedApp,
    val appInfo: LaunchableAppInfo,
)

class MinimalLauncherWidgetConfigureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val initiallySelectedApps =
            MinimalLauncherWidgetConfigRepository.getSelectedApps(appWidgetId)

        setContent {
            DetoxDroidTheme {
                MinimalLauncherWidgetConfigureScreen(
                    initiallySelectedApps = initiallySelectedApps,
                    onCancel = { finish() },
                    onSave = { selectedApps ->
                        MinimalLauncherWidgetConfigRepository.saveSelectedApps(
                            appWidgetId = appWidgetId,
                            selectedApps = selectedApps
                        )
                        val appWidgetManager = AppWidgetManager.getInstance(this)
                        MinimalLauncherWidgetProvider.updateAppWidget(
                            context = this,
                            appWidgetManager = appWidgetManager,
                            appWidgetId = appWidgetId
                        )
                        setResult(
                            RESULT_OK,
                            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        )
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MinimalLauncherWidgetConfigureScreen(
    initiallySelectedApps: List<WidgetSelectedApp>,
    onCancel: () -> Unit,
    onSave: (List<WidgetSelectedApp>) -> Unit,
) {
    val context = LocalContext.current
    val appLoadState by produceState(initialValue = AppLoadState(isLoading = true, apps = emptyList())) {
        value = AppLoadState(
            isLoading = false,
            apps = withContext(Dispatchers.IO) {
                MinimalLauncherWidgetAppRepository.getLaunchableApps(context)
            }
        )
    }

    val selectedApps = remember(initiallySelectedApps) {
        mutableStateListOf<WidgetSelectedApp>().apply { addAll(initiallySelectedApps) }
    }

    var showSearchBar by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showFiltersSheet by rememberSaveable { mutableStateOf(false) }
    var showSystemApps by rememberSaveable { mutableStateOf(false) }
    var showUserApps by rememberSaveable { mutableStateOf(true) }
    val selectedCategories = remember { mutableStateListOf<String>() }

    var renameTargetPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }

    val availableCategories = remember(appLoadState.apps) {
        appLoadState.apps.map { it.appCategory }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    LaunchedEffect(availableCategories) {
        if (selectedCategories.isEmpty()) {
            selectedCategories.addAll(availableCategories)
        }
    }

    fun selectedIndexOf(packageName: String): Int {
        return selectedApps.indexOfFirst { it.packageName == packageName }
    }

    fun updateSelected(packageName: String, selected: Boolean) {
        val currentIndex = selectedIndexOf(packageName)
        if (selected && currentIndex == -1) {
            selectedApps.add(WidgetSelectedApp(packageName = packageName))
        } else if (!selected && currentIndex != -1) {
            selectedApps.removeAt(currentIndex)
        }
    }

    fun moveSelected(packageName: String, delta: Int) {
        val currentIndex = selectedIndexOf(packageName)
        if (currentIndex == -1) return
        val targetIndex = (currentIndex + delta).coerceIn(0, selectedApps.lastIndex)
        if (targetIndex == currentIndex) return
        val movedItem = selectedApps.removeAt(currentIndex)
        selectedApps.add(targetIndex, movedItem)
    }

    val selectedByPackage = selectedApps.associateBy { it.packageName }
    val appByPackage = appLoadState.apps.associateBy { it.packageName }

    fun matchesSearch(
        appInfo: LaunchableAppInfo,
        displayLabel: String? = null,
    ): Boolean {
        val query = searchQuery.trim()
        if (query.isBlank()) return true

        return appInfo.appName.contains(query, ignoreCase = true) ||
            appInfo.packageName.contains(query, ignoreCase = true) ||
            appInfo.appCategory.contains(query, ignoreCase = true) ||
            displayLabel?.contains(query, ignoreCase = true) == true
    }

    fun matchesAvailableFilters(
        appInfo: LaunchableAppInfo,
    ): Boolean {
        val isSystemApp = appInfo.isSystemApp
        val showByType = (showSystemApps && isSystemApp) || (showUserApps && !isSystemApp)
        if (!showByType) return false

        val matchesCategory = appInfo.appCategory.isBlank() || selectedCategories.contains(appInfo.appCategory)
        if (!matchesCategory) return false

        return matchesSearch(appInfo)
    }

    val selectedSectionItems = selectedApps.mapNotNull { selectedApp ->
        val appInfo = appByPackage[selectedApp.packageName] ?: return@mapNotNull null
        val displayLabel = selectedApp.customLabel?.ifBlank { null } ?: appInfo.appName
        if (!matchesSearch(appInfo, displayLabel)) {
            return@mapNotNull null
        }
        SelectedAppWithInfo(selectedApp = selectedApp, appInfo = appInfo)
    }

    val unselectedSectionItems = appLoadState.apps.filter { appInfo ->
        if (selectedByPackage.containsKey(appInfo.packageName)) return@filter false
        matchesAvailableFilters(appInfo)
    }

    Scaffold(
        topBar = {
            AppSelectionTopBar(
                title = stringResource(id = R.string.widget_minimalLauncher_configure_title),
                showSearchBar = showSearchBar,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onToggleSearch = {
                    showSearchBar = !showSearchBar
                    if (!showSearchBar) searchQuery = ""
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.action_close)
                        )
                    }
                },
                searchContentDescription = stringResource(id = R.string.action_search),
                closeSearchContentDescription = stringResource(id = R.string.widget_minimalLauncher_search_clear),
                onOpenFilters = { showFiltersSheet = true },
                filtersContentDescription = stringResource(id = R.string.action_filter),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onSave(selectedApps.toList()) }) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(id = R.string.action_save)
                )
            }
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            if (appLoadState.isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Text(
                            text = stringResource(id = R.string.widget_minimalLauncher_section_selected),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    if (selectedSectionItems.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(id = R.string.widget_minimalLauncher_selected_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(selectedSectionItems, key = { it.selectedApp.packageName }) { item ->
                            val displayLabel =
                                item.selectedApp.customLabel?.ifBlank { null } ?: item.appInfo.appName
                            val hasCustomLabel = !item.selectedApp.customLabel.isNullOrBlank()
                            SelectedAppListItem(
                                item = item,
                                displayLabel = displayLabel,
                                hasCustomLabel = hasCustomLabel,
                                onUnchecked = { updateSelected(item.selectedApp.packageName, false) },
                                onOpenRenameDialog = {
                                    renameTargetPackage = item.selectedApp.packageName
                                    renameText = item.selectedApp.customLabel.orEmpty()
                                },
                                onMove = { delta -> moveSelected(item.selectedApp.packageName, delta) }
                            )
                        }
                    }

                    item {
                        Text(
                            text = stringResource(id = R.string.widget_minimalLauncher_section_available),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    if (unselectedSectionItems.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(id = R.string.widget_minimalLauncher_available_emptyFiltered),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(unselectedSectionItems, key = { it.packageName }) { app ->
                            AppSelectionListItem(
                                packageName = app.packageName,
                                appName = app.appName,
                                appCategory = app.appCategory,
                                isSystemApp = app.isSystemApp,
                                checked = false,
                                onCheckedChange = { checked -> updateSelected(app.packageName, checked) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFiltersSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFiltersSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(id = R.string.feature_settings_exceptions_filterByAppType)) },
                    supportingContent = {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = showSystemApps,
                                onClick = { showSystemApps = !showSystemApps },
                                label = { Text(stringResource(id = R.string.exceptionsList_filter_systemApps)) }
                            )
                            FilterChip(
                                selected = showUserApps,
                                onClick = { showUserApps = !showUserApps },
                                label = { Text(stringResource(id = R.string.exceptionsList_filter_userApps)) }
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )

                ListItem(
                    headlineContent = { Text(stringResource(id = R.string.feature_settings_exceptions_filterByCategory)) },
                    supportingContent = {
                        if (availableCategories.isEmpty()) {
                            Text(
                                text = stringResource(id = R.string.widget_minimalLauncher_filter_category_empty),
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                availableCategories.forEach { category ->
                                    FilterChip(
                                        selected = selectedCategories.contains(category),
                                        onClick = {
                                            if (selectedCategories.contains(category)) {
                                                selectedCategories.remove(category)
                                            } else {
                                                selectedCategories.add(category)
                                            }
                                        },
                                        label = { Text(category) }
                                    )
                                }
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }

    if (renameTargetPackage != null) {
        AlertDialog(
            onDismissRequest = { renameTargetPackage = null },
            title = { Text(stringResource(id = R.string.widget_minimalLauncher_label_edit)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text(stringResource(id = R.string.widget_minimalLauncher_label_display)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(id = R.string.widget_minimalLauncher_label_display_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val packageName = renameTargetPackage ?: return@TextButton
                        val selectedIndex = selectedIndexOf(packageName)
                        if (selectedIndex != -1) {
                            selectedApps[selectedIndex] = selectedApps[selectedIndex].copy(
                                customLabel = renameText.trim().ifBlank { null }
                            )
                        }
                        renameTargetPackage = null
                    }
                ) {
                    Text(stringResource(id = R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetPackage = null }) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LazyItemScope.SelectedAppListItem(
    item: SelectedAppWithInfo,
    displayLabel: String,
    hasCustomLabel: Boolean,
    onUnchecked: () -> Unit,
    onOpenRenameDialog: () -> Unit,
    onMove: (Int) -> Unit,
) {
    var dragDistanceY by remember(item.selectedApp.packageName) { mutableFloatStateOf(0f) }
    var isDragging by remember(item.selectedApp.packageName) { mutableStateOf(false) }
    val moveThresholdPx = with(LocalDensity.current) { 42.dp.toPx() }
    val hapticFeedback = LocalHapticFeedback.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .animateContentSize(),
        color = if (isDragging) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        },
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = stringResource(id = R.string.widget_minimalLauncher_reorder_drag),
                modifier = Modifier
                    .padding(end = 6.dp)
                    .pointerInput(item.selectedApp.packageName) {
                        detectDragGestures(
                            onDragStart = {
                                isDragging = true
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd = {
                                isDragging = false
                                dragDistanceY = 0f
                            },
                            onDragCancel = {
                                isDragging = false
                                dragDistanceY = 0f
                            },
                            onDrag = { _, dragAmount ->
                                dragDistanceY += dragAmount.y
                                if (dragDistanceY >= moveThresholdPx) {
                                    onMove(1)
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    dragDistanceY = 0f
                                } else if (dragDistanceY <= -moveThresholdPx) {
                                    onMove(-1)
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    dragDistanceY = 0f
                                }
                            }
                        )
                    }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenRenameDialog),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(
                        onClick = onOpenRenameDialog,
                        modifier = Modifier
                            .padding(start = 2.dp)
                            .size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            tint = if (hasCustomLabel) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            contentDescription = stringResource(id = R.string.widget_minimalLauncher_label_edit),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                if (hasCustomLabel) {
                    Text(
                        text = stringResource(
                            id = R.string.widget_minimalLauncher_label_original,
                            item.appInfo.appName
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Checkbox(
                checked = true,
                onCheckedChange = { checked ->
                    if (!checked) onUnchecked()
                }
            )
        }
    }
}
