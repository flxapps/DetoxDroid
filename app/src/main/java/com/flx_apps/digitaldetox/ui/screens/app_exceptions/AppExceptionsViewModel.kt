package com.flx_apps.digitaldetox.ui.screens.app_exceptions

import ManageAppExceptionsScreen
import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.flx_apps.digitaldetox.feature_types.AppExceptionListType
import com.flx_apps.digitaldetox.feature_types.SupportsAppExceptionsFeature
import com.flx_apps.digitaldetox.ui.screens.feature.FeatureViewModel
import com.flx_apps.digitaldetox.ui.screens.feature.FeatureViewModelFactory
import com.flx_apps.digitaldetox.util.getAppCategoryTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Represents an app that is installed on the device.
 * @property packageName The package name of the app.
 * @property appName The name of the app.
 * @property appCategory The category of the app (if available, see [ApplicationInfo.category]).
 * @property isSystemApp Whether the app is a system app.
 * @property isException Whether the app is already an exception for the current feature.
 */
data class AppExceptionItem(
    val packageName: String,
    val appName: String,
    val appCategory: String,
    val isSystemApp: Boolean,
    var isException: Boolean
)

/**
 * The view model for the [ManageAppExceptionsScreen]. It contains all installed apps and provides
 * methods to filter and toggle the exception state of apps.
 */
@HiltViewModel
class AppExceptionsViewModel @Inject constructor(
    private val application: Application,
    private val savedStateHandle: androidx.lifecycle.SavedStateHandle
) : FeatureViewModel(application, savedStateHandle) {
    companion object : FeatureViewModelFactory()

    /**
     * Feature cast to [SupportsAppExceptionsFeature].
     */
    private val appExceptionsFeature = feature as SupportsAppExceptionsFeature

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
    private val _showSystemApps = MutableStateFlow(true)
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

    private val _exceptionListType = MutableStateFlow(appExceptionsFeature.appExceptionListType)
    val exceptionListType = _exceptionListType.asStateFlow()

    /**
     * Whether the bottom sheet to configure the list should be shown.
     */
    private val _showListSettingsSheet = MutableStateFlow(false)
    val showListSettingsSheet = _showListSettingsSheet.asStateFlow()

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
        val packageManager: PackageManager = application.packageManager
        val appCategories = mutableSetOf<String>()
        val apps =
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA).map { appInfo ->
                val appCategory = appInfo.getAppCategoryTitle(application)
                appCategories.add(appCategory)
                AppExceptionItem(
                    packageName = appInfo.packageName,
                    appName = appInfo.loadLabel(packageManager).toString(),
                    appCategory = appInfo.getAppCategoryTitle(application),
                    isSystemApp = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    isException = appExceptionsFeature.appExceptions.contains(
                        appInfo.packageName
                    )
                )
            }
        _appExceptionItems = apps
        _selectedAppCategories.value = appCategories.associateWith {
            false
        }
        _filteredAppExceptionItems.value = apps
    }

    /**
     * Filters the [_appExceptionItems] live data by the given query.
     * @param query The query to filter the apps by.
     */
    fun filterApps(query: String = this.query.value) {
        this.query.value = query
        val showAllCategories = _selectedAppCategories.value.values.all { !it }
        // filter apps by query, system/user apps and app categories
        val filteredApps = _appExceptionItems.filter { app ->
            val appNameContainsQuery = query.isBlank() || app.appName.contains(
                query, ignoreCase = true
            )
            val appTypeShouldBeShown =
                app.isSystemApp && _showSystemApps.value || !app.isSystemApp && _showUserApps.value
            val appCategoryShouldBeShown =
                showAllCategories || _selectedAppCategories.value[app.appCategory]!!
            appNameContainsQuery && appTypeShouldBeShown && appCategoryShouldBeShown
        }
        _filteredAppExceptionItems.value = filteredApps
    }

    /**
     * Toggles the exception state of the given app.
     * @param packageName The package name of the app.
     * @return The new exception state of the app or null if the app was not found.
     */
    fun toggleAppException(packageName: String): Boolean? {
        _filteredAppExceptionItems.value?.find { app -> app.packageName == packageName }?.apply {
            isException = !isException
            if (isException) appExceptionsFeature.appExceptions += packageName
            else appExceptionsFeature.appExceptions -= packageName
            return isException
        }
        return null
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