package com.flx_apps.digitaldetox.ui.screens.onboarding

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.data.repository.ApplicationInfoRepository
import com.flx_apps.digitaldetox.feature_types.AppExceptionListType
import com.flx_apps.digitaldetox.feature_types.Feature
import com.flx_apps.digitaldetox.feature_types.NeedsPermissionsFeature
import com.flx_apps.digitaldetox.features.BreakDoomScrollingFeature
import com.flx_apps.digitaldetox.features.DISPLAY_DALTONIZER
import com.flx_apps.digitaldetox.features.DISPLAY_DALTONIZER_ENABLED
import com.flx_apps.digitaldetox.features.DisableAppsFeature
import com.flx_apps.digitaldetox.features.FeaturesProvider
import com.flx_apps.digitaldetox.features.GrayscaleAppsFeature
import com.flx_apps.digitaldetox.system_integration.AccessibilityServiceController
import com.flx_apps.digitaldetox.system_integration.DetoxDroidAccessibilityService
import com.flx_apps.digitaldetox.system_integration.DetoxDroidState
import com.flx_apps.digitaldetox.system_integration.UsageStatsProvider
import com.flx_apps.digitaldetox.util.DistractingAppsHeuristic
import com.flx_apps.digitaldetox.util.DistractionCandidate
import com.flx_apps.digitaldetox.util.NotificationHelper
import com.flx_apps.digitaldetox.util.ShizukuUtils
import com.flx_apps.digitaldetox.util.UsageAccessUtil
import com.flx_apps.digitaldetox.util.knownCategoryOf
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * The steps of the onboarding flow, in order.
 */
enum class OnboardingStep {
    WELCOME, USAGE_ACCESS, PICK_APPS, PRESET, PERMISSIONS, DONE
}

/**
 * The intensity presets the user can choose from. Each level includes the previous one:
 * [GENTLE] = doom-scrolling breaks, [BALANCED] = + grayscale after the daily budget,
 * [STRICT] = + blocking after the daily budget (with grayscale already warning at a fraction
 * of it, see [DistractingAppsHeuristic.strictGrayscaleBudgetMs]).
 */
enum class OnboardingPreset {
    GENTLE, BALANCED, STRICT
}

/**
 * A row in the distracting-apps selection list.
 */
data class OnboardingAppRow(
    val packageName: String,
    val appName: String,
    val appCategory: String,
    val isSystemApp: Boolean,
    val avgDailyUsageMs: Long,
    val checked: Boolean
)

/**
 * Drives the onboarding flow: step navigation, the distracting-apps selection, the chosen
 * intensity preset and the final feature configuration. Nothing is written to the features until
 * [completeOnboarding] — abandoning the flow leaves the app untouched.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val application: Application
) : AndroidViewModel(application) {
    companion object {
        /** Usage is averaged over the last week (zero days included). */
        const val USAGE_DAYS = 7

        /** System apps without a known category are hidden below this average daily usage. */
        val MIN_SYSTEM_APP_USAGE_MS: Long = TimeUnit.MINUTES.toMillis(5)

        /** How long the grayscale preview stays on screen. */
        const val GRAYSCALE_PREVIEW_DURATION_MS = 2500L
    }

    /**
     * Whether onboarding had already been completed when this flow was started (i.e. the user is
     * re-running it from the About screen). Completing it again replaces the affected features'
     * app lists, which the summary step points out.
     */
    val isRerun: Boolean = OnboardingState.isOnboardingCompleted

    private val _step = MutableStateFlow(OnboardingStep.WELCOME)
    val step: StateFlow<OnboardingStep> = _step

    private val _hasUsageAccess = MutableStateFlow(UsageAccessUtil.hasUsageAccess(application))
    val hasUsageAccess: StateFlow<Boolean> = _hasUsageAccess

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps

    private val _appRows = MutableStateFlow<List<OnboardingAppRow>>(emptyList())
    val appRows: StateFlow<List<OnboardingAppRow>> = _appRows

    /** Whether the ranked list is backed by real usage data (false: category heuristics only). */
    private val _hasUsageData = MutableStateFlow(false)
    val hasUsageData: StateFlow<Boolean> = _hasUsageData

    /** Tracks under which usage-access state the app list was built, to reload after a grant. */
    private var appsLoadedWithUsageAccess: Boolean? = null

    fun nextStep() {
        val values = OnboardingStep.entries
        _step.value = values[(values.indexOf(_step.value) + 1).coerceAtMost(values.size - 1)]
    }

    fun previousStep() {
        val values = OnboardingStep.entries
        _step.value = values[(values.indexOf(_step.value) - 1).coerceAtLeast(0)]
    }

    /**
     * Re-checks the usage-access permission, e.g. when the user returns from the system settings.
     */
    fun refreshUsageAccess() {
        _hasUsageAccess.value = UsageAccessUtil.hasUsageAccess(application)
    }

    /**
     * Builds the ranked distracting-apps list (once per usage-access state — granting access on
     * the way back re-ranks with real data).
     */
    fun loadAppsIfNeeded() {
        val hasUsageAccess = _hasUsageAccess.value
        if (_isLoadingApps.value || appsLoadedWithUsageAccess == hasUsageAccess) return
        _isLoadingApps.value = true
        viewModelScope.launch(Dispatchers.IO) {
            _appRows.value = buildAppRows(hasUsageAccess)
            appsLoadedWithUsageAccess = hasUsageAccess
            _isLoadingApps.value = false
        }
    }

    private fun buildAppRows(hasUsageAccess: Boolean): List<OnboardingAppRow> {
        val avgUsageByApp: Map<String, Long> = if (hasUsageAccess) {
            val totals = HashMap<String, Long>()
            UsageStatsProvider.queryDailyUsage(USAGE_DAYS).forEach { (_, statsByApp) ->
                statsByApp.forEach { (packageName, stats) ->
                    totals.merge(packageName, stats.totalTimeInForeground, Long::plus)
                }
            }
            totals.mapValues { it.value / USAGE_DAYS }
        } else {
            emptyMap()
        }
        val installedApps = ApplicationInfoRepository.getInstalledApps()
        val candidates = installedApps.filter { app ->
            app.packageName != application.packageName && app.hasLaunchIntent && (!app.isSystemApp || knownCategoryOf(
                app.packageName
            ) != null || (avgUsageByApp[app.packageName] ?: 0L) >= MIN_SYSTEM_APP_USAGE_MS)
        }.map { app ->
            DistractionCandidate(
                packageName = app.packageName,
                knownCategory = knownCategoryOf(app.packageName),
                osCategory = app.osCategory,
                avgDailyUsageMs = avgUsageByApp[app.packageName] ?: 0L
            )
        }
        val appsByPackage = installedApps.associateBy { it.packageName }
        val previouslyChecked = _appRows.value.filter { it.checked }.map { it.packageName }.toSet()
        _hasUsageData.value = hasUsageAccess && avgUsageByApp.isNotEmpty()
        return DistractingAppsHeuristic.rankApps(candidates).map { rankedApp ->
            val appInfo = appsByPackage.getValue(rankedApp.packageName)
            OnboardingAppRow(
                packageName = rankedApp.packageName,
                appName = appInfo.appName,
                appCategory = appInfo.appCategory,
                isSystemApp = appInfo.isSystemApp,
                avgDailyUsageMs = rankedApp.avgDailyUsageMs,
                // keep the user's manual selection when re-ranking
                checked = if (appsLoadedWithUsageAccess != null) {
                    rankedApp.packageName in previouslyChecked
                } else {
                    rankedApp.preChecked
                }
            )
        }
    }

    fun toggleApp(packageName: String) {
        _appRows.value = _appRows.value.map {
            if (it.packageName == packageName) it.copy(checked = !it.checked) else it
        }
    }

    private val _preset = MutableStateFlow(OnboardingPreset.BALANCED)
    val preset: StateFlow<OnboardingPreset> = _preset

    private val _budgetMinutes =
        MutableStateFlow(TimeUnit.MILLISECONDS.toMinutes(DistractingAppsHeuristic.DEFAULT_BUDGET_MS).toInt())
    val budgetMinutes: StateFlow<Int> = _budgetMinutes

    /** Whether the current budget value was derived from the user's actual usage. */
    private val _budgetIsSuggested = MutableStateFlow(false)
    val budgetIsSuggested: StateFlow<Boolean> = _budgetIsSuggested

    private var userAdjustedBudget = false

    fun selectPreset(preset: OnboardingPreset) {
        _preset.value = preset
    }

    fun setBudgetMinutes(minutes: Int) {
        userAdjustedBudget = true
        _budgetIsSuggested.value = false
        _budgetMinutes.value = minutes
    }

    /**
     * (Re)computes the suggested daily budget from the selected apps' average usage. Called when
     * the preset step is shown; a manual adjustment is never overwritten.
     */
    fun refreshBudgetSuggestion() {
        if (userAdjustedBudget) return
        val avgSelectedUsageMs = _appRows.value.filter { it.checked }.sumOf { it.avgDailyUsageMs }
        _budgetMinutes.value = TimeUnit.MILLISECONDS.toMinutes(
            DistractingAppsHeuristic.suggestedDailyBudgetMs(avgSelectedUsageMs)
        ).toInt()
        _budgetIsSuggested.value = avgSelectedUsageMs > 0
    }

    // region permission states
    private val _overlayGranted = MutableStateFlow(Settings.canDrawOverlays(application))
    val overlayGranted: StateFlow<Boolean> = _overlayGranted

    private val _notificationsGranted =
        MutableStateFlow(NotificationHelper.hasNotificationPermission(application))
    val notificationsGranted: StateFlow<Boolean> = _notificationsGranted

    private val _writeSecureSettingsGranted = MutableStateFlow(hasWriteSecureSettings())
    val writeSecureSettingsGranted: StateFlow<Boolean> = _writeSecureSettingsGranted

    /**
     * Mirrors the live service state, so the checklist ticks the moment the service actually
     * connects (it binds asynchronously after [enableAccessibilityService]).
     */
    val accessibilityEnabled: StateFlow<Boolean> =
        DetoxDroidAccessibilityService.state.map { it != DetoxDroidState.Inactive }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            DetoxDroidAccessibilityService.state.value != DetoxDroidState.Inactive
        )

    /** True when WRITE_SECURE_SETTINGS can be granted right here via root or Shizuku. */
    private val _canOneTapGrant = MutableStateFlow(false)
    val canOneTapGrant: StateFlow<Boolean> = _canOneTapGrant

    private var isRootAvailable = false
    private var rootChecked = false

    private fun hasWriteSecureSettings(): Boolean =
        application.checkCallingOrSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /**
     * Re-checks all permission states, e.g. when the user returns from the system settings.
     * Shizuku availability is cheap and can change while the user hops to the wizard and back, so
     * it is probed every time; the root probe spawns an `su` process and can stall, so it runs
     * only once.
     */
    fun refreshPermissionStates() {
        refreshUsageAccess()
        _overlayGranted.value = Settings.canDrawOverlays(application)
        _notificationsGranted.value = NotificationHelper.hasNotificationPermission(application)
        _writeSecureSettingsGranted.value = hasWriteSecureSettings()
        viewModelScope.launch(Dispatchers.IO) {
            val isShizukuAvailable = ShizukuUtils.isShizukuAvailable()
            // publish the fast probe first so a stalling root check cannot delay the one-tap offer
            if (isShizukuAvailable) _canOneTapGrant.value = true
            if (!rootChecked) {
                rootChecked = true
                isRootAvailable = runCatching { Shell.getShell().isRoot }.getOrDefault(false)
            }
            _canOneTapGrant.value = isRootAvailable || isShizukuAvailable
        }
    }

    /**
     * Grants WRITE_SECURE_SETTINGS through root or Shizuku (whichever is available). Shizuku may
     * first pop its own permission dialog; [ShizukuUtils.executeCommand] retries after the grant.
     */
    fun grantWriteSecureSettings() {
        val command =
            application.getString(R.string.rootCommand_grantWriteSecuritySettingsPermission)
        viewModelScope.launch(Dispatchers.IO) {
            if (isRootAvailable) {
                Shell.cmd(command).exec()
                refreshPermissionStates()
            } else {
                ShizukuUtils.executeCommand(command) { _, _ -> refreshPermissionStates() }
            }
        }
    }

    /**
     * Enables the accessibility service programmatically (works only with WRITE_SECURE_SETTINGS);
     * [accessibilityEnabled] flips as soon as the service connects.
     */
    fun enableAccessibilityService() {
        runCatching { AccessibilityServiceController.activate(application) }
    }

    private val _isPreviewingGrayscale = MutableStateFlow(false)
    val isPreviewingGrayscale: StateFlow<Boolean> = _isPreviewingGrayscale

    /**
     * Turns the whole screen grayscale for a few seconds so the user can feel what the Balanced
     * and Strict presets will do. Only possible with WRITE_SECURE_SETTINGS; the previous
     * daltonizer state (e.g. a color-blindness filter) is restored afterwards.
     */
    fun previewGrayscale() {
        if (!_writeSecureSettingsGranted.value) return
        if (!_isPreviewingGrayscale.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch(Dispatchers.IO) {
            val contentResolver = application.contentResolver
            val previousEnabled = runCatching {
                Settings.Secure.getInt(contentResolver, DISPLAY_DALTONIZER_ENABLED, 0)
            }.getOrDefault(0)
            val previousDaltonizer = runCatching {
                Settings.Secure.getInt(contentResolver, DISPLAY_DALTONIZER, -1)
            }.getOrDefault(-1)
            try {
                Settings.Secure.putInt(contentResolver, DISPLAY_DALTONIZER_ENABLED, 1)
                Settings.Secure.putInt(contentResolver, DISPLAY_DALTONIZER, 0)
                delay(GRAYSCALE_PREVIEW_DURATION_MS)
            } finally {
                // never strand the user in gray: restore even when the view model is torn down
                // mid-preview (leaving onboarding cancels this scope)
                withContext(NonCancellable) {
                    runCatching {
                        Settings.Secure.putInt(
                            contentResolver, DISPLAY_DALTONIZER_ENABLED, previousEnabled
                        )
                        Settings.Secure.putInt(
                            contentResolver, DISPLAY_DALTONIZER, previousDaltonizer
                        )
                    }
                    _isPreviewingGrayscale.value = false
                }
            }
        }
    }
    // endregion

    /**
     * Marks onboarding as completed without configuring anything ("skip setup").
     */
    fun skipOnboarding() {
        OnboardingState.isOnboardingCompleted = true
    }

    private var completed = false

    /**
     * Applies the chosen preset to the features and marks onboarding as completed. Grayscale is
     * only activated when WRITE_SECURE_SETTINGS is present — otherwise it would be a permanent
     * silent no-op; instead it is fully configured and flagged as pending, and the home screen
     * offers to finish the setup.
     * @return false when onboarding was already completed by an earlier call (double-tap guard) —
     * the caller must not navigate again in that case
     */
    fun completeOnboarding(): Boolean {
        if (completed) return false
        completed = true
        val selectedApps = _appRows.value.filter { it.checked }.map { it.packageName }.toSet()
        val budgetMs = TimeUnit.MINUTES.toMillis(_budgetMinutes.value.toLong())
        // features that were configured but could not be activated yet because a required
        // permission is still missing — the home screen finishes these once the permission arrives
        val pending = mutableSetOf<String>()
        if (selectedApps.isNotEmpty()) {
            BreakDoomScrollingFeature.appExceptionListType = AppExceptionListType.ONLY_LIST
            BreakDoomScrollingFeature.appExceptions = selectedApps
            activateOrDefer(BreakDoomScrollingFeature, pending)
            if (_preset.value != OnboardingPreset.GENTLE) {
                GrayscaleAppsFeature.appExceptionListType = AppExceptionListType.ONLY_LIST
                GrayscaleAppsFeature.appExceptions = selectedApps
                // in the Strict preset grayscale warns before the block kicks in — with identical
                // thresholds the block would always win and the grayscale phase would never show
                GrayscaleAppsFeature.allowedDailyColorScreenTime =
                    if (_preset.value == OnboardingPreset.STRICT) {
                        DistractingAppsHeuristic.strictGrayscaleBudgetMs(budgetMs)
                    } else {
                        budgetMs
                    }
                activateOrDefer(GrayscaleAppsFeature, pending)
            }
            if (_preset.value == OnboardingPreset.STRICT) {
                DisableAppsFeature.appExceptionListType = AppExceptionListType.ONLY_LIST
                DisableAppsFeature.appExceptions = selectedApps
                DisableAppsFeature.allowedDailyScreenTime = budgetMs
                activateOrDefer(DisableAppsFeature, pending)
            }
        }
        // (re)assign the pending set in every path so re-running onboarding cannot leave a stale
        // reminder behind
        OnboardingState.pendingFeatureActivations = pending
        // start DetoxDroid itself if possible (throws without WRITE_SECURE_SETTINGS)
        runCatching { AccessibilityServiceController.activate(application) }
        OnboardingState.isOnboardingCompleted = true
        return true
    }

    /**
     * Activates [feature] when its required permissions are present, otherwise leaves it configured
     * but deactivated and records it in [pending] — a feature that cannot work must not appear as
     * "activated". A missing permission always wins over a previously activated state (e.g. a
     * re-run after the permission was revoked), so the feature is explicitly stopped in that case.
     */
    private fun activateOrDefer(feature: Feature, pending: MutableSet<String>) {
        val hasPermissions =
            feature !is NeedsPermissionsFeature || feature.hasPermissions(application)
        feature.isActivated = hasPermissions
        if (!hasPermissions) pending += feature.id
        FeaturesProvider.startOrStopFeature(feature)
    }
}
