package com.flx_apps.digitaldetox.widgets.minimal_launcher

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.util.SizeF
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.lifecycleScope
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.features.DisableAppsFeature
import com.flx_apps.digitaldetox.system_integration.ScreenTimeSurfaces
import com.flx_apps.digitaldetox.ui.theme.DetoxDroidTheme
import com.flx_apps.digitaldetox.ui.widgets.apps.AppFilterSheet
import com.flx_apps.digitaldetox.ui.widgets.apps.AppListSectionHeader
import com.flx_apps.digitaldetox.ui.widgets.apps.AppSelectionListItem
import com.flx_apps.digitaldetox.ui.widgets.apps.AppSelectionTopBar
import com.flx_apps.digitaldetox.widgets.WidgetSettingsStore
import com.flx_apps.digitaldetox.widgets.WidgetStyle
import com.flx_apps.digitaldetox.widgets.WidgetStyleScreen
import com.flx_apps.digitaldetox.widgets.configuredAppWidgetId
import com.flx_apps.digitaldetox.widgets.setConfiguredResult
import com.flx_apps.digitaldetox.widgets.sizeDpOf
import com.flx_apps.digitaldetox.widgets.widgetStyleSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class AppLoadState(
    val isLoading: Boolean,
    val apps: List<LaunchableAppInfo>,
)

private class SavedConfig(
    val selectedApps: List<WidgetSelectedApp>,
    val style: WidgetStyle,
    val parts: Set<MinimalLauncherWidgetPart>,
)

private data class SelectedAppWithInfo(
    val selectedApp: WidgetSelectedApp,
    val appInfo: LaunchableAppInfo,
)

/** Two cells square, the size the widget is placed at. */
private val PREVIEW_SIZE_UNKNOWN = SizeF(180f, 180f)

class MinimalLauncherWidgetConfigureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val appWidgetId = configuredAppWidgetId() ?: return finish()

        enableEdgeToEdge()
        setContent {
            DetoxDroidTheme {
                // the persisted selection, style and parts are loaded off the main thread; the
                // screen shows its (already async) app-list loading state until they are ready
                val savedConfig by produceState<SavedConfig?>(initialValue = null, appWidgetId) {
                    value = SavedConfig(
                        MinimalLauncherWidgetConfigRepository.getSelectedAppsAsync(appWidgetId),
                        WidgetSettingsStore.loadStyle(
                            appWidgetId, MinimalLauncherWidgetProvider.defaultStyle
                        ),
                        MinimalLauncherWidgetProvider.loadParts(appWidgetId),
                    )
                }
                savedConfig?.let { config ->
                    MinimalLauncherWidgetConfigureScreen(
                        appWidgetId = appWidgetId,
                        initiallySelectedApps = config.selectedApps,
                        initialStyle = config.style,
                        initialParts = config.parts,
                        onCancel = { finish() },
                        onSave = { selectedApps, newStyle, parts ->
                            saveAndFinish(appWidgetId, selectedApps, newStyle, parts)
                        }
                    )
                }
            }
        }
    }

    private fun saveAndFinish(
        appWidgetId: Int,
        selectedApps: List<WidgetSelectedApp>,
        style: WidgetStyle,
        parts: Set<MinimalLauncherWidgetPart>,
    ) {
        lifecycleScope.launch {
            MinimalLauncherWidgetConfigRepository.saveSelectedAppsAsync(appWidgetId, selectedApps)
            WidgetSettingsStore.saveStyle(appWidgetId, style)
            WidgetSettingsStore.saveParts(appWidgetId, parts)
            MinimalLauncherWidgetProvider.update(applicationContext, intArrayOf(appWidgetId))
            setConfiguredResult(appWidgetId)
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MinimalLauncherWidgetConfigureScreen(
    appWidgetId: Int,
    initiallySelectedApps: List<WidgetSelectedApp>,
    initialStyle: WidgetStyle,
    initialParts: Set<MinimalLauncherWidgetPart>,
    onCancel: () -> Unit,
    onSave: (List<WidgetSelectedApp>, WidgetStyle, Set<MinimalLauncherWidgetPart>) -> Unit,
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
    val selectedCategories = remember { mutableStateListOf<String>() }

    var renameTargetPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }

    var style by remember(initialStyle) { mutableStateOf(initialStyle) }
    var parts by remember(initialParts) { mutableStateOf(initialParts) }
    var showStyleScreen by rememberSaveable { mutableStateOf(false) }

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

    if (showStyleScreen) {
        val previewSize = remember(appWidgetId) {
            AppWidgetManager.getInstance(context).sizeDpOf(appWidgetId) ?: PREVIEW_SIZE_UNKNOWN
        }
        val usedMs by produceState(emptyMap<String, Long>()) {
            value = ScreenTimeSurfaces.current(context)?.screenTimeByPackage.orEmpty()
        }
        val blocked = remember { DisableAppsFeature.blockedApps() }
        // the selection as it stands, unsaved changes included, the way the widget will list it
        val previewItems = selectedApps.mapNotNull { selectedApp ->
            val appInfo = appByPackage[selectedApp.packageName] ?: return@mapNotNull null
            MinimalLauncherWidgetItem(
                packageName = appInfo.packageName,
                label = selectedApp.customLabel?.ifBlank { null } ?: appInfo.appName,
                usedMs = if (MinimalLauncherWidgetPart.TimeToday in parts) {
                    usedMs[appInfo.packageName] ?: 0L
                } else null,
                blocked = MinimalLauncherWidgetPart.DimBlocked in parts &&
                        appInfo.packageName in blocked,
            )
        }
        WidgetStyleScreen(
            style = style,
            onStyleChange = { style = it },
            parts = MinimalLauncherWidgetPart.entries,
            shownParts = parts,
            onShownPartsChange = { parts = it },
            partsDescription = stringResource(R.string.widget_minimalLauncher_parts_description),
            preview = remember(previewItems, style) {
                MinimalLauncherWidgetProvider.buildViews(
                    context, appWidgetId, previewItems, previewSize, style, interactive = false
                )
            },
            previewSize = previewSize,
            confirmLabel = stringResource(id = R.string.action_done),
            onConfirm = { showStyleScreen = false },
            onBack = { showStyleScreen = false },
        )
        return
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
            FloatingActionButton(onClick = { onSave(selectedApps.toList(), style, parts) }) {
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
                    if (!showSearchBar) {
                        item {
                            ListItem(
                                headlineContent = { Text(stringResource(id = R.string.widget_style_title)) },
                                supportingContent = {
                                    Text(
                                        widgetStyleSummary(
                                            style,
                                            MinimalLauncherWidgetPart.entries.filter { it in parts }
                                        )
                                    )
                                },
                                leadingContent = { Icon(Icons.Default.Palette, contentDescription = null) },
                                trailingContent = {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                                },
                                modifier = Modifier.clickable { showStyleScreen = true }
                            )
                        }
                    }

                    item {
                        AppListSectionHeader(stringResource(id = R.string.appList_section_selected))
                    }

                    if (selectedSectionItems.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(id = R.string.appList_selected_empty),
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
                        AppListSectionHeader(stringResource(id = R.string.appList_section_available))
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
                                // every app here has a launcher icon, so none is a system part
                                isBackgroundApp = false,
                                checked = false,
                                modifier = Modifier.animateItem(),
                                onCheckedChange = { checked -> updateSelected(app.packageName, checked) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFiltersSheet) {
        AppFilterSheet(
            appTypes = null,
            categories = availableCategories.associateWith { selectedCategories.contains(it) },
            onToggleCategory = { category ->
                if (!selectedCategories.remove(category)) selectedCategories.add(category)
            },
            onDismiss = { showFiltersSheet = false },
        )
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
            .animateItem()
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
