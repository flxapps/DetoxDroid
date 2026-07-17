package com.flx_apps.digitaldetox.ui.screens.app_exceptions

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.flx_apps.digitaldetox.data.repository.ApplicationInfoData
import com.flx_apps.digitaldetox.data.repository.ApplicationInfoRepository
import com.flx_apps.digitaldetox.feature_types.AppExceptionListType
import com.flx_apps.digitaldetox.feature_types.SupportsAppExceptionsFeature
import com.flx_apps.digitaldetox.features.DisableAppsFeature
import com.flx_apps.digitaldetox.features.FeaturesProvider
import com.flx_apps.digitaldetox.premium.PremiumSheetController
import com.flx_apps.digitaldetox.ui.screens.feature.FeatureViewModel
import com.flx_apps.digitaldetox.ui.screens.feature.FeatureViewModelFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * Once a user has configured this many app exceptions for a feature they are clearly a power user, so
 * we let the premium sheet surface a (capped) support nudge. See [PremiumSheetController].
 */
private const val POWER_USE_APP_EXCEPTIONS_THRESHOLD = 8

/**
 * Represents an app that is installed on the device.
 * @param appInfo The [ApplicationInfoData] of the app.
 * @param isException Whether the app is an exception for the current feature.
 */
data class AppExceptionItem(
    val appInfo: ApplicationInfoData, val isException: Boolean
)

/**
 * A feature whose app selection can be copied into the current one ("Copy from…").
 */
data class ExceptionsCopySource(
    val featureId: String, @StringRes val titleRes: Int, val appCount: Int
)

/**
 * The view model for the [ManageAppExceptionsScreen]. It contains all installed apps and provides
 * methods to filter and toggle the exception state of apps.
 */
@HiltViewModel
class AppExceptionsViewModel @Inject constructor(
    application: Application, savedStateHandle: androidx.lifecycle.SavedStateHandle
) : FeatureViewModel(application, savedStateHandle) {
    companion object : FeatureViewModelFactory()

    /**
     * Feature cast to [SupportsAppExceptionsFeature].
     */
    private val appExceptionsFeature = feature as SupportsAppExceptionsFeature

    /**
     * Package name of DetoxDroid itself.
     */
    private val appPackageName = application.packageName

    /**
     * Allowed list types for the current feature.
     *
     * Some features (e.g. [DisableAppsFeature]) only support [AppExceptionListType.ONLY_LIST].
     */
    private val allowedListTypes = appExceptionsFeature.listTypes

    /**
     * Whether the current app should be hidden from the selection list.
     *
     * For [DisableAppsFeature], DetoxDroid must never deactivate itself.
     */
    private val shouldExcludeCurrentApp = feature == DisableAppsFeature

    /**
     * Initial list type shown in the UI.
     *
     * Stored values from older versions can be invalid for features with restricted list types. In
     * that case we immediately normalize to the first allowed type and persist it.
     */
    private val initialExceptionListType = appExceptionsFeature.appExceptionListType.takeIf {
        allowedListTypes.contains(it)
    } ?: allowedListTypes.first().also {
        appExceptionsFeature.appExceptionListType = it
    }

    /**
     * Holds the list of all installed apps.
     */
    private lateinit var _appExceptionItems: List<AppExceptionItem>

    /**
     * Holds the current query to filter the apps by.
     */
    val query = mutableStateOf("")

    /**
     * Holds the list of all available app categories.
     */
    private val _selectedAppCategories = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val selectedAppCategories = _selectedAppCategories.asStateFlow()

    /**
     * Whether system apps should be shown.
     */
    private val _showSystemApps = MutableStateFlow(false)
    val showSystemApps = _showSystemApps.asStateFlow()

    /**
     * Whether user apps should be shown.
     */
    private val _showUserApps = MutableStateFlow(true)
    val showUserApps = _showUserApps.asStateFlow()

    /**
     * Holds the list of all installed apps that are currently filtered by the query, the
     * selected app categories and the show system/user apps settings.
     */
    private val _filteredAppExceptionItems = MutableStateFlow<List<AppExceptionItem>?>(null)
    val appExceptionItems = _filteredAppExceptionItems.asStateFlow()

    private val _exceptionListType = MutableStateFlow(initialExceptionListType)
    val exceptionListType = _exceptionListType.asStateFlow()

    /**
     * Whether the bottom sheet to configure the list should be shown.
     */
    private val _showListSettingsSheet = MutableStateFlow(false)
    val showListSettingsSheet = _showListSettingsSheet.asStateFlow()

    /**
     * Holds the number of currently toggled items (i.e. items that are selected in the list).
     */
    private val _toggledItemsSize = MutableStateFlow<Int>(0)
    val toggledItemsSize = _toggledItemsSize.asStateFlow()

    /**
     * Loads all installed apps when the view model is created in a coroutine.
     */
    init {
        viewModelScope.launch(Dispatchers.IO) {
            loadInstalledApps()
        }
    }

    /**
     * Loads all installed apps and sets the [_appExceptionItems] live data.
     * This method is called when the view model is created.
     * It also maps the installed apps to [AppExceptionItem] objects and detects, whether an app is
     * already an exception for the current feature.
     */
    private fun loadInstalledApps() {
        // Ensure stale persisted self-entry cannot survive in Disable Apps mode.
        if (shouldExcludeCurrentApp && appExceptionsFeature.appExceptions.contains(appPackageName)) {
            appExceptionsFeature.appExceptions -= appPackageName
        }
        val appCategories = mutableSetOf<String>()
        val apps = ApplicationInfoRepository.getInstalledApps()
            .filterNot { shouldExcludeCurrentApp && it.packageName == appPackageName }
            .map {
                val isException = appExceptionsFeature.appExceptions.contains(it.packageName)
                appCategories += it.appCategory
                AppExceptionItem(it, isException)
            }
            .sortedBy { it.appInfo.appName.lowercase(Locale.getDefault()) }
        _toggledItemsSize.value = apps.count { it.isException }
        _appExceptionItems = apps
        _selectedAppCategories.value = appCategories.associateWith {
            false
        }
        filterApps()
    }

    /**
     * Filters the [_appExceptionItems] live data by the given query.
     *
     * Selected apps are only filtered by the search query — the type/category filters must never
     * hide the user's own selection (same behavior as the widget configurator's list).
     * @param query The query to filter the apps by.
     */
    fun filterApps(query: String = this.query.value) {
        this.query.value = query
        if (this::_appExceptionItems.isInitialized.not()) return // apps not loaded yet
        val showAllCategories = _selectedAppCategories.value.values.all { !it }
        val filteredApps = _appExceptionItems.filter { item ->
            val appNameContainsQuery = query.isBlank() || item.appInfo.appName.contains(
                query, ignoreCase = true
            )
            if (!appNameContainsQuery) return@filter false
            if (item.isException) return@filter true
            val appTypeShouldBeShown =
                item.appInfo.isSystemApp && _showSystemApps.value || !item.appInfo.isSystemApp && _showUserApps.value
            val appCategoryShouldBeShown =
                showAllCategories || _selectedAppCategories.value[item.appInfo.appCategory] == true
            appTypeShouldBeShown && appCategoryShouldBeShown
        }
        _filteredAppExceptionItems.value = filteredApps
    }

    /**
     * Toggles the exception state of the given app. The master list is rebuilt with the toggled
     * item and re-filtered, so the UI regroups it into the right section immediately.
     * @param packageName The package name of the app.
     */
    fun toggleAppException(packageName: String) {
        if (this::_appExceptionItems.isInitialized.not()) return
        val index =
            _appExceptionItems.indexOfFirst { it.appInfo.packageName == packageName }
        if (index == -1) return
        val toggled = _appExceptionItems[index].let { it.copy(isException = !it.isException) }
        if (toggled.isException) appExceptionsFeature.appExceptions += packageName
        else appExceptionsFeature.appExceptions -= packageName
        _appExceptionItems = _appExceptionItems.toMutableList().also { it[index] = toggled }
        _toggledItemsSize.value = appExceptionsFeature.appExceptions.size
        if (toggled.isException &&
            appExceptionsFeature.appExceptions.size >= POWER_USE_APP_EXCEPTIONS_THRESHOLD
        ) {
            PremiumSheetController.notifyPowerUse()
        }
        filterApps()
    }

    /**
     * The other features this feature's selection can be copied from: every feature with app
     * exceptions that has at least one app selected.
     */
    fun copySources(): List<ExceptionsCopySource> {
        return FeaturesProvider.featureList
            .filter { it.id != feature.id && it is SupportsAppExceptionsFeature }
            .mapNotNull { source ->
                val exceptions = (source as SupportsAppExceptionsFeature).appExceptions
                if (exceptions.isEmpty()) null
                else ExceptionsCopySource(source.id, source.texts.title, exceptions.size)
            }
    }

    /**
     * Replaces this feature's selection with the app selection of [featureId]. The master list is
     * rebuilt so the UI regroups immediately.
     */
    fun copyExceptionsFrom(featureId: String) {
        val source = FeaturesProvider.getFeatureById(featureId) as? SupportsAppExceptionsFeature
            ?: return
        var newExceptions = source.appExceptions
        if (shouldExcludeCurrentApp) newExceptions = newExceptions - appPackageName
        appExceptionsFeature.appExceptions = newExceptions
        _toggledItemsSize.value = newExceptions.size
        if (this::_appExceptionItems.isInitialized) {
            _appExceptionItems = _appExceptionItems.map {
                it.copy(isException = newExceptions.contains(it.appInfo.packageName))
            }
            filterApps()
        }
    }

    /**
     * Toggles whether the given app category should be shown.
     * @param category The category to toggle.
     */
    fun toggleAppCategory(category: String) {
        _selectedAppCategories.value = _selectedAppCategories.value.toMutableMap().apply {
            this[category] = !this[category]!!
        }
        filterApps()
    }

    /**
     * Toggles whether system apps should be shown.
     */
    fun toggleShowSystemApps() {
        _showSystemApps.value = !_showSystemApps.value
        filterApps()
    }

    /**
     * Toggles whether user apps should be shown.
     */
    fun toggleShowUserApps() {
        _showUserApps.value = !_showUserApps.value
        filterApps()
    }

    /**
     * Sets the type of the app exception list.
     * @see AppExceptionListType
     */
    fun setExceptionListType(type: AppExceptionListType) {
        if (!allowedListTypes.contains(type)) return
        appExceptionsFeature.appExceptionListType = type
        _exceptionListType.value = type
    }

    /**
     * Sets whether the bottom sheet to configure the list should be shown.
     */
    fun setShowListSettingsSheet(show: Boolean) {
        _showListSettingsSheet.value = show
    }
}