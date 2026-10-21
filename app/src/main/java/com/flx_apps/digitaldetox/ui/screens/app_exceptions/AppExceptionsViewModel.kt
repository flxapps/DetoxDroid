package com.flx_apps.digitaldetox.ui.screens.app_exceptions

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.flx_apps.digitaldetox.data.repository.ApplicationInfoData
import com.flx_apps.digitaldetox.data.repository.ApplicationInfoRepository
import com.flx_apps.digitaldetox.feature_types.AppExceptionListType
import com.flx_apps.digitaldetox.feature_types.SupportsAppExceptionsFeature
import com.flx_apps.digitaldetox.premium.PremiumSheetController
import com.flx_apps.digitaldetox.rules.RuleGroups
import com.flx_apps.digitaldetox.rules.RuleOwners
import com.flx_apps.digitaldetox.ui.screens.feature.FeatureViewModelFactory
import com.flx_apps.digitaldetox.websites.InstalledBrowsers
import com.flx_apps.digitaldetox.websites.WebsiteCollection
import com.flx_apps.digitaldetox.websites.WebsiteCollections
import com.flx_apps.digitaldetox.websites.Websites
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
 * A group whose app selection can be copied into the current one ("Copy from…").
 */
data class ExceptionsCopySource(
    val featureId: String, val title: String, val appCount: Int, val websiteCount: Int
)

/**
 * A site the user added to the list, see [Websites.siteEntry].
 * @param checked Whether it is in the list. One unchecked during this visit stays on screen, so a
 * slip of the finger can be taken back without typing the site again.
 */
data class SiteItem(val entry: String, val name: String, val checked: Boolean)

/**
 * One of the [WebsiteCollection]s that come with the app.
 * @param sample The first few sites of a curated collection, empty for a large one.
 * @param size How many sites it has, 0 until it is loaded.
 */
data class CollectionItem(
    val collection: WebsiteCollection,
    val checked: Boolean,
    val sample: List<String>,
    val size: Int,
)

/**
 * The view model for the [ManageAppExceptionsScreen]. It contains all installed apps and provides
 * methods to filter and toggle the exception state of apps. The list belongs to the owner whose
 * id the screen was opened with, a group as a rule, see [RuleOwners].
 */
@HiltViewModel
class AppExceptionsViewModel @Inject constructor(
    application: Application, savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    companion object : FeatureViewModelFactory()

    /** The id of the group (or feature) whose list this is. */
    val ownerId: String = savedStateHandle["featureId"]!!

    /** The list under [ownerId]. */
    val scope: SupportsAppExceptionsFeature = RuleOwners.appScope(ownerId)!!

    private val appExceptionsFeature = scope

    /**
     * Package name of DetoxDroid itself.
     */
    private val appPackageName = application.packageName

    /** The list types the owner offers for its apps. */
    private val allowedListTypes = appExceptionsFeature.listTypes

    /**
     * DetoxDroid is never in a list. A group can lock what it names, and a locked DetoxDroid
     * would have no way back in.
     */
    private val shouldExcludeCurrentApp = true

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

    /** The website list types the feature offers, see [SupportsAppExceptionsFeature]. */
    val allowedWebsiteListTypes = appExceptionsFeature.websiteListTypes

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
     * Whether the apps with a launcher icon are shown, user and preinstalled alike. The camera, the
     * browser and the video app that came with the phone are system apps, and they are what a
     * group is made for.
     */
    private val _showLaunchableApps = MutableStateFlow(true)
    val showLaunchableApps = _showLaunchableApps.asStateFlow()

    /**
     * Whether the packages without a launcher icon are shown: the system's own parts, services and
     * providers, which nobody opens and so nobody has to wait for or turn gray. Off by default,
     * since they outnumber the apps several times over.
     */
    private val _showBackgroundApps = MutableStateFlow(false)
    val showBackgroundApps = _showBackgroundApps.asStateFlow()

    /**
     * Holds the list of all installed apps that are currently filtered by the query, the
     * selected app categories and the show system/user apps settings.
     */
    private val _filteredAppExceptionItems = MutableStateFlow<List<AppExceptionItem>?>(null)
    val appExceptionItems = _filteredAppExceptionItems.asStateFlow()

    private val _exceptionListType = MutableStateFlow(initialExceptionListType)
    val exceptionListType = _exceptionListType.asStateFlow()

    /** Whether the listed websites are the only ones covered or the ones spared. */
    private val _websiteListType = MutableStateFlow(appExceptionsFeature.websiteListType)
    val websiteListType = _websiteListType.asStateFlow()

    /**
     * Whether the bottom sheet to configure the list should be shown.
     */
    private val _showListSettingsSheet = MutableStateFlow(false)
    val showListSettingsSheet = _showListSettingsSheet.asStateFlow()

    /**
     * How many apps are selected, of those that are really there. The stored selection outlives
     * the apps it names, and an app that is gone is one this feature no longer acts on.
     */
    private val _selectedAppCount = MutableStateFlow(0)
    val selectedAppCount = _selectedAppCount.asStateFlow()

    /** The sites under "Your websites", see [SiteItem]. */
    private val _sites = MutableStateFlow<List<SiteItem>>(emptyList())
    val sites = _sites.asStateFlow()

    /** The collections under "Ready-made lists", see [CollectionItem]. */
    private val _collections = MutableStateFlow<List<CollectionItem>>(emptyList())
    val collections = _collections.asStateFlow()

    /** The browsers on this phone and whether DetoxDroid can read them, null until known. */
    private val _browsers = MutableStateFlow<InstalledBrowsers?>(null)
    val browsers = _browsers.asStateFlow()

    /** How many websites and collections are in the list. */
    private val _selectedWebsiteCount = MutableStateFlow(0)
    val selectedWebsiteCount = _selectedWebsiteCount.asStateFlow()

    /** How many of the [selectedWebsiteCount] entries are collections. */
    private val _selectedCollectionCount = MutableStateFlow(0)
    val selectedCollectionCount = _selectedCollectionCount.asStateFlow()

    /**
     * The readable browsers the feature covers as apps, by name. While only the listed websites
     * count, every website in these counts anyway, through the browser.
     */
    private val _browsersCoveredAsApps = MutableStateFlow<List<String>>(emptyList())
    val browsersCoveredAsApps = _browsersCoveredAsApps.asStateFlow()

    /** The browsers DetoxDroid cannot read that the feature does not cover as apps either. */
    private val _uncoveredUnreadableBrowsers = MutableStateFlow<List<String>>(emptyList())
    val uncoveredUnreadableBrowsers = _uncoveredUnreadableBrowsers.asStateFlow()

    /**
     * The other features this feature's selection can be copied from, filled once the app list is
     * in. Reading the package manager for every source is too slow for composition.
     */
    private val _copySources = MutableStateFlow<List<ExceptionsCopySource>>(emptyList())
    val copySources = _copySources.asStateFlow()

    /**
     * Loads all installed apps when the view model is created in a coroutine.
     */
    init {
        refreshWebsites(initial = true)
        viewModelScope.launch(Dispatchers.IO) {
            loadInstalledApps()
        }
        viewModelScope.launch(Dispatchers.IO) {
            _browsers.value = InstalledBrowsers.find(getApplication())
            updateSelectionCounts()
        }
        viewModelScope.launch(Dispatchers.IO) {
            // also warms the service's copy: both run in the same process
            WebsiteCollection.entries.forEach { WebsiteCollections.load(getApplication(), it) }
            refreshWebsites()
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
        _appExceptionItems = apps
        updateSelectionCounts()
        _selectedAppCategories.value = appCategories.associateWith {
            false
        }
        _copySources.value = loadCopySources()
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
            val appTypeShouldBeShown = if (item.appInfo.hasLaunchIntent) {
                _showLaunchableApps.value
            } else {
                _showBackgroundApps.value
            }
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
        updateSelectionCounts()
        RuleOwners.onChanged(ownerId)
        if (toggled.isException &&
            appExceptionsFeature.appExceptions.size >= POWER_USE_APP_EXCEPTIONS_THRESHOLD
        ) {
            PremiumSheetController.notifyPowerUse()
        }
        filterApps()
    }

    /**
     * The other groups this selection can be copied from: every group that has at least one app
     * or website selected.
     */
    private fun loadCopySources(): List<ExceptionsCopySource> {
        return RuleGroups.all
            .filter { it.id != ownerId }
            .mapNotNull { source ->
                val exceptions = source.appExceptions
                val appCount = ApplicationInfoRepository.countInstalled(Websites.appsOf(exceptions))
                val websiteCount = Websites.websitesOf(exceptions).size
                if (appCount + websiteCount == 0) null
                else ExceptionsCopySource(source.id, source.name, appCount, websiteCount)
            }
    }

    /**
     * Adds the site the user typed, in whatever form it was typed or pasted. Returns false when it
     * names no site, so the field can say so.
     */
    fun addWebsite(input: String): Boolean {
        val entry = Websites.siteEntry(input) ?: return false
        if (entry !in appExceptionsFeature.appExceptions) {
            appExceptionsFeature.appExceptions += entry
        }
        // a site typed again moves to the top, where the user looks for it
        _sites.value = listOf(SiteItem(entry, Websites.displayName(getApplication(), entry), true)) +
                _sites.value.filter { it.entry != entry }
        refreshWebsites()
        RuleOwners.onChanged(ownerId)
        return true
    }

    /** Puts the site or collection [entry] into the list or takes it out. */
    fun toggleWebsite(entry: String) {
        if (entry in appExceptionsFeature.appExceptions) {
            appExceptionsFeature.appExceptions -= entry
        } else {
            appExceptionsFeature.appExceptions += entry
        }
        refreshWebsites()
        RuleOwners.onChanged(ownerId)
    }

    /**
     * Rebuilds the website part of the screen from the stored list. The sites keep the order they
     * are shown in, and one taken out during this visit keeps its row, unchecked.
     */
    private fun refreshWebsites(initial: Boolean = false) {
        val entries = appExceptionsFeature.appExceptions
        val listedSites = Websites.websitesOf(entries).filter { Websites.collectionOf(it) == null }
        val shown = if (initial) {
            listedSites.sortedBy { it.lowercase(Locale.ROOT) }
        } else {
            _sites.value.map { it.entry } + listedSites.filter { site -> _sites.value.none { it.entry == site } }
        }
        _sites.value = shown.map { SiteItem(it, Websites.displayName(getApplication(), it), it in entries) }
        _collections.value = WebsiteCollection.entries.map { collection ->
            val domains = WebsiteCollections.loadedOrNull(collection)
            CollectionItem(
                collection = collection,
                checked = Websites.collectionEntry(collection) in entries,
                sample = if (collection.named) domains?.sample.orEmpty() else emptyList(),
                size = domains?.size ?: 0,
            )
        }
        updateSelectionCounts()
    }

    /**
     * Counts the selection the way the screen shows it: the apps that are really there (the stored
     * selection outlives the apps it names) and every website entry. Also works out which browsers
     * the apps cover, which both change with.
     */
    private fun updateSelectionCounts() {
        val websites = Websites.websitesOf(appExceptionsFeature.appExceptions)
        _selectedWebsiteCount.value = websites.size
        _selectedCollectionCount.value = websites.count { Websites.collectionOf(it) != null }
        _selectedAppCount.value =
            if (this::_appExceptionItems.isInitialized) _appExceptionItems.count { it.isException }
            else 0
        _browsersCoveredAsApps.value = _browsers.value?.readable.orEmpty()
            .filter { appExceptionsFeature.appliesTo(it.packageName) }
            .map { it.label }
        _uncoveredUnreadableBrowsers.value = _browsers.value?.unreadable.orEmpty()
            .filterNot { appExceptionsFeature.appliesTo(it.packageName) }
            .map { it.label }
    }

    /**
     * Replaces this feature's selection with the app selection of [featureId]. The master list is
     * rebuilt so the UI regroups immediately.
     */
    fun copyExceptionsFrom(featureId: String) {
        val source = RuleOwners.appScope(featureId) ?: return
        var newExceptions = source.appExceptions
        if (shouldExcludeCurrentApp) newExceptions = newExceptions - appPackageName
        replaceExceptions(newExceptions)
        RuleOwners.onChanged(ownerId)
    }

    /**
     * Replaces this feature's selection with [newExceptions] and rebuilds the master list so the
     * UI regroups immediately.
     */
    private fun replaceExceptions(newExceptions: Set<String>) {
        appExceptionsFeature.appExceptions = newExceptions
        if (this::_appExceptionItems.isInitialized) {
            _appExceptionItems = _appExceptionItems.map {
                it.copy(isException = newExceptions.contains(it.appInfo.packageName))
            }
            filterApps()
        }
        refreshWebsites()
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
     * Toggles whether the apps with a launcher icon are shown.
     */
    fun toggleShowLaunchableApps() {
        _showLaunchableApps.value = !_showLaunchableApps.value
        filterApps()
    }

    /**
     * Toggles whether the packages without a launcher icon are shown.
     */
    fun toggleShowBackgroundApps() {
        _showBackgroundApps.value = !_showBackgroundApps.value
        filterApps()
    }

    /**
     * Sets the type of the app exception list. The selection is kept unless [clearSelection] is set:
     * switching the type turns the selected apps from the only ones affected into the only ones
     * spared, or the other way round, which is rarely what a non-empty list was built for. The
     * websites have a type of their own and stay as they are.
     * @see AppExceptionListType
     */
    fun setExceptionListType(type: AppExceptionListType, clearSelection: Boolean = false) {
        if (!allowedListTypes.contains(type)) return
        if (clearSelection) {
            replaceExceptions(Websites.websitesOf(appExceptionsFeature.appExceptions))
        } else if (this::_appExceptionItems.isInitialized) {
            // the stored selection can name apps that are gone. They were not in the count the
            // user was asked about, and under the other type they would come back meaning the
            // opposite of what they were picked for once reinstalled.
            val installed = _appExceptionItems.mapTo(mutableSetOf()) { it.appInfo.packageName }
            val kept = appExceptionsFeature.appExceptions.filterTo(mutableSetOf()) {
                it in installed || Websites.isWebsite(it)
            }
            if (kept.size != appExceptionsFeature.appExceptions.size) replaceExceptions(kept)
        }
        appExceptionsFeature.appExceptionListType = type
        _exceptionListType.value = type
        updateSelectionCounts()
        RuleOwners.onChanged(ownerId)
    }

    /**
     * Sets the type of the website list, the way [setExceptionListType] does for the apps. With
     * [clearSelection] the websites are taken out and the apps stay.
     */
    fun setWebsiteListType(type: AppExceptionListType, clearSelection: Boolean = false) {
        if (!allowedWebsiteListTypes.contains(type)) return
        if (clearSelection) replaceExceptions(Websites.appsOf(appExceptionsFeature.appExceptions))
        appExceptionsFeature.websiteListType = type
        _websiteListType.value = type
        RuleOwners.onChanged(ownerId)
    }

    /**
     * Sets whether the bottom sheet to configure the list should be shown.
     */
    fun setShowListSettingsSheet(show: Boolean) {
        _showListSettingsSheet.value = show
    }
}