package com.flx_apps.digitaldetox.ui.screens.onboarding

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.feature_types.AppExceptionListType
import com.flx_apps.digitaldetox.features.BreakDoomScrollingFeature
import com.flx_apps.digitaldetox.rules.RuleGroups
import com.flx_apps.digitaldetox.system_integration.AccessibilityServiceController
import com.flx_apps.digitaldetox.system_integration.DetoxDroidAccessibilityService
import com.flx_apps.digitaldetox.system_integration.DetoxDroidState
import com.flx_apps.digitaldetox.system_integration.ReliabilitySettings
import com.flx_apps.digitaldetox.util.BatteryOptimizationHelper
import com.flx_apps.digitaldetox.util.DistractingAppsHeuristic
import com.flx_apps.digitaldetox.util.RankedApps
import com.flx_apps.digitaldetox.util.NotificationHelper
import com.flx_apps.digitaldetox.util.ShizukuUtils
import com.flx_apps.digitaldetox.util.UsageAccessUtil
import com.flx_apps.digitaldetox.util.PrivilegedShell
import com.flx_apps.digitaldetox.util.hasWriteSecureSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * The steps of the onboarding flow, in order.
 */
enum class OnboardingStep {
    WELCOME, USAGE_ACCESS, PICK_APPS, PRESET, PERMISSIONS, RELIABILITY, DONE
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
    /**
     * Whether onboarding had already been completed when this flow was started (i.e. the user is
     * re-running it from the About screen). Completing it again adds another group next to the
     * ones there are, which the summary step points out.
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
        val (ranked, hasUsageData) = RankedApps.load(application, hasUsageAccess)
        val previouslyChecked = _appRows.value.filter { it.checked }.map { it.packageName }.toSet()
        _hasUsageData.value = hasUsageData
        return ranked.map { row ->
            OnboardingAppRow(
                packageName = row.packageName,
                appName = row.appName,
                appCategory = row.appCategory,
                isSystemApp = row.isSystemApp,
                avgDailyUsageMs = row.avgDailyUsageMs,
                // keep the user's manual selection when re-ranking
                checked = if (appsLoadedWithUsageAccess != null) {
                    row.packageName in previouslyChecked
                } else {
                    row.preChecked
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

    private val _writeSecureSettingsGranted = MutableStateFlow(application.hasWriteSecureSettings())
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

    /**
     * Re-checks all permission states, e.g. when the user returns from the system settings.
     * Shizuku availability is cheap and can change while the user hops to the wizard and back, so
     * it is probed every time; the root probe spawns an `su` process and can stall, so only the
     * first one asks (see [PrivilegedShell.isRootAvailable]).
     */
    fun refreshPermissionStates() {
        refreshUsageAccess()
        _overlayGranted.value = Settings.canDrawOverlays(application)
        _notificationsGranted.value = NotificationHelper.hasNotificationPermission(application)
        _writeSecureSettingsGranted.value = application.hasWriteSecureSettings()
        _batteryOptimizationIgnored.value =
            BatteryOptimizationHelper.isIgnoringBatteryOptimizations(application)
        viewModelScope.launch(Dispatchers.IO) {
            val isShizukuAvailable = ShizukuUtils.isShizukuAvailable()
            // publish the fast probe first so a stalling root check cannot delay the one-tap offer
            if (isShizukuAvailable) _canOneTapGrant.value = true
            _canOneTapGrant.value = PrivilegedShell.isRootAvailable() || isShizukuAvailable
        }
    }

    /**
     * Grants WRITE_SECURE_SETTINGS through root or Shizuku (whichever is available). Shizuku may
     * first pop its own permission dialog; [ShizukuUtils.executeCommand] retries after the grant.
     */
    fun grantWriteSecureSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            PrivilegedShell.grantWriteSecureSettings(application) { refreshPermissionStates() }
        }
    }

    /**
     * Enables the accessibility service programmatically (works only with WRITE_SECURE_SETTINGS);
     * [accessibilityEnabled] flips as soon as the service connects.
     */
    fun enableAccessibilityService() {
        runCatching { AccessibilityServiceController.activate(application) }
    }

    // endregion

    // region reliability (optional)
    private val _keepServiceAliveEnabled =
        MutableStateFlow(ReliabilitySettings.keepServiceAliveEnabled)
    val keepServiceAliveEnabled: StateFlow<Boolean> = _keepServiceAliveEnabled

    private val _batteryOptimizationIgnored =
        MutableStateFlow(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(application))
    val batteryOptimizationIgnored: StateFlow<Boolean> = _batteryOptimizationIgnored

    /** Enables/disables the foreground-service keepalive and refreshes the running service. */
    fun setKeepServiceAlive(enabled: Boolean) {
        ReliabilitySettings.keepServiceAliveEnabled = enabled
        _keepServiceAliveEnabled.value = enabled
        DetoxDroidAccessibilityService.instance?.updateForegroundNotification()
    }

    fun openBatteryOptimizationSettings() =
        BatteryOptimizationHelper.openBatteryOptimizationSettings(application)
    // endregion

    /**
     * Marks onboarding as completed without configuring anything ("skip setup").
     */
    fun skipOnboarding() {
        OnboardingState.isOnboardingCompleted = true
    }

    private var completed = false

    /**
     * Turns the selection and the preset into one group and marks onboarding as completed. A step
     * whose permission is still missing goes in anyway: the home screen offers to finish the setup
     * until the permission arrives, and the step takes hold the moment it does. With the groups
     * locked by the commitment password nothing is written, so running the wizard again cannot
     * get around the passphrase.
     * @return false when onboarding was already completed by an earlier call (double-tap guard) —
     * the caller must not navigate again in that case
     */
    fun completeOnboarding(): Boolean {
        if (completed) return false
        completed = true
        val selectedApps = _appRows.value.filter { it.checked }.map { it.packageName }.toSet()
        val budgetMs = TimeUnit.MINUTES.toMillis(_budgetMinutes.value.toLong())
        val preset = _preset.value
        if (selectedApps.isNotEmpty() && !OnboardingState.isOnboardingLocked) {
            RuleGroups.create {
                name = RuleGroups.uniqueName(application.getString(R.string.ruleGroups_onboarding_name))
                appExceptions = selectedApps
                appExceptionListType = AppExceptionListType.ONLY_LIST
                scrollBreakAfterMs = BreakDoomScrollingFeature.DefaultTimeUntilWarningMs
                if (preset != OnboardingPreset.GENTLE) {
                    this.budgetMs = budgetMs
                    // in the Strict preset gray warns before the lock takes hold; with identical
                    // thresholds the lock would always win and the gray stretch would never show
                    grayAfterMs = if (preset == OnboardingPreset.STRICT) {
                        DistractingAppsHeuristic.strictGrayscaleBudgetMs(budgetMs)
                    } else {
                        budgetMs
                    }
                    locks = preset == OnboardingPreset.STRICT
                }
            }
            RuleGroups.onChanged()
        }
        // start DetoxDroid itself if possible (throws without WRITE_SECURE_SETTINGS)
        runCatching { AccessibilityServiceController.activate(application) }
        OnboardingState.isOnboardingCompleted = true
        return true
    }
}
