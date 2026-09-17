package com.flx_apps.digitaldetox.features

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.flx_apps.digitaldetox.DetoxDroidApplication
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.data.DataStoreProperty
import com.flx_apps.digitaldetox.data.DataStorePropertyTransformer
import com.flx_apps.digitaldetox.feature_types.AppExceptionListType
import com.flx_apps.digitaldetox.feature_types.Feature
import com.flx_apps.digitaldetox.feature_types.FeatureTexts
import com.flx_apps.digitaldetox.feature_types.LockableFeature
import com.flx_apps.digitaldetox.feature_types.PausableFeature
import com.flx_apps.digitaldetox.feature_types.NeedsDrawOverlayPermissionFeature
import com.flx_apps.digitaldetox.feature_types.NeedsPermissionsFeature
import com.flx_apps.digitaldetox.feature_types.OnAppOpenedSubscriptionFeature
import com.flx_apps.digitaldetox.feature_types.OnScreenTurnedOffSubscriptionFeature
import com.flx_apps.digitaldetox.feature_types.ScreenTimeTrackingFeature
import com.flx_apps.digitaldetox.feature_types.SupportsAppExceptionsFeature
import com.flx_apps.digitaldetox.feature_types.SupportsScheduleFeature
import com.flx_apps.digitaldetox.system_integration.DetoxDroidAccessibilityService
import com.flx_apps.digitaldetox.system_integration.DetoxDroidDeviceAdminReceiver
import com.flx_apps.digitaldetox.system_integration.DetoxDroidState
import com.flx_apps.digitaldetox.system_integration.OverlayService
import com.flx_apps.digitaldetox.ui.screens.feature.disable_apps.AppDisabledOverlayService
import com.flx_apps.digitaldetox.ui.screens.feature.disable_apps.DisableAppsFeatureSettingsSection
import com.flx_apps.digitaldetox.ui.screens.feature.disable_apps.WaitBeforeOpeningActivity
import com.flx_apps.digitaldetox.util.DailyAppCounter
import timber.log.Timber

/**
 * The [DisableAppsFeature] can either deactivate apps or block them.
 * For deactivating apps, the app needs to have DEVICE_ADMIN permission.
 * For blocking apps, the app needs to have SYSTEM_ALERT_WINDOW permission.
 * @see NeedsPermissionsFeature
 * @see android.Manifest.permission.SYSTEM_ALERT_WINDOW
 * @see android.Manifest.permission.BIND_DEVICE_ADMIN
 */
enum class DisableAppsMode {
    DEACTIVATE, BLOCK,
}

val DisableAppsFeatureId = Feature.createId(DisableAppsFeature::class.java)

/**
 * This feature can disable apps. If DetoxDroid has DEVICE_ADMIN permission, it can completely
 * deactivate apps, so they are not visible in the launcher anymore. Otherwise it can only make
 * them unusable by showing a warning screen when the user tries to open them.
 *
 * Until the [allowedDailyScreenTime] is used up, it can also make the apps wait: every open then
 * goes through [WaitBeforeOpeningActivity] first. The wait prices each open, however short, and
 * the budget caps the minutes, so together they catch both the quick checks and the long sessions.
 */
object DisableAppsFeature : Feature(), OnAppOpenedSubscriptionFeature,
    OnScreenTurnedOffSubscriptionFeature,
    SupportsAppExceptionsFeature by SupportsAppExceptionsFeature.Impl(DisableAppsFeatureId),
    SupportsScheduleFeature by SupportsScheduleFeature.Impl(DisableAppsFeatureId),
    NeedsPermissionsFeature by NeedsDrawOverlayPermissionFeature(),
    ScreenTimeTrackingFeature by ScreenTimeTrackingFeature.Impl(DisableAppsFeatureId),
    LockableFeature, PausableFeature {
    override val texts: FeatureTexts = FeatureTexts(
        title = R.string.feature_disableApps,
        subtitle = R.string.feature_disableApps_subtitle,
        description = R.string.feature_disableApps_description,
    )
    override val iconRes: Int = R.drawable.ic_disable_app
    override val settingsContent: @Composable () -> Unit = {
        DisableAppsFeatureSettingsSection()
    }

    /**
     * The [DisableAppsFeature] supports only the [AppExceptionListType.ONLY_LIST] type, as it is
     * only possible to configure apps that are to be disabled.
     */
    override val listTypes: List<AppExceptionListType>
        get() = listOf(
            AppExceptionListType.ONLY_LIST
        )

    /**
     * Marks [allowedDailyScreenTime] as unlimited: the apps are never disabled, so only the
     * [waitBeforeOpening] applies.
     */
    const val NO_DAILY_LIMIT = -1L

    /**
     * The allowed daily screen time in milliseconds. If the user has already used more screen time
     * than this value, the apps will be disabled. If the value is 0, the apps will always be
     * disabled, while the feature and DetoxDroid are active; with [NO_DAILY_LIMIT], never.
     */
    var allowedDailyScreenTime: Long by DataStoreProperty(
        longPreferencesKey("${id}_allowedDailyScreenTime"), 0L
    )

    /**
     * How long a listed app makes the user wait before it opens, in milliseconds; 0 means no wait.
     * Only applies while there is screen time left, afterwards the apps are disabled anyway.
     */
    var waitBeforeOpening: Long by DataStoreProperty(
        longPreferencesKey("${id}_waitBeforeOpening"), 0L
    )

    /**
     * The apps the user has sat out the wait for since the screen last turned on. They open right
     * away until then, so switching back and forth between apps doesn't cost a wait every time,
     * while picking the phone up again does.
     */
    private val waitedForApps = mutableSetOf<String>()

    /**
     * Lets [packageName] open without a wait until the screen turns off. Called when the user has
     * waited and chooses to open the app.
     */
    fun skipWaitUntilScreenOff(packageName: String) {
        waitedForApps += packageName
    }

    /**
     * The operation mode of the feature. By default, it is set to [DisableAppsMode.BLOCK], because
     * [DisableAppsMode.DEACTIVATE] requires the DEVICE_ADMIN permission, which has to be granted by
     * the user from the computer using adb.
     * @see DisableAppsMode
     */
    var operationMode: DisableAppsMode by DataStoreProperty(
        key = stringPreferencesKey("${id}_disableAppsMode"),
        DisableAppsMode.BLOCK,
        dataTransformer = DataStorePropertyTransformer.EnumStorePropertyTransformer(DisableAppsMode::class.java)
    )

    /**
     * Per-app count of app-open blocks triggered today. Resets automatically at midnight and is
     * persisted into the usage-stats database by the snapshot worker.
     */
    val blockCounter = DailyAppCounter()

    /**
     * The apps that are to be disabled are implemented using the [appExceptions].
     * @see SupportsAppExceptionsFeature
     */
    val disableableApps: Set<String>
        get() = appExceptions

    /**
     * Whether the apps are currently deactivated.
     */
    private var isAppsDeactivated = false

    /**
     * The disableable app that most recently started a tracking session. Used by
     * [budgetExhaustedChecker] to know which app to block when the budget runs out mid-session.
     */
    @Volatile
    private var lastTrackedPackage: String? = null

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    /**
     * Fires when the daily screen-time budget is expected to run out mid-session. Without it, the
     * block would only engage on the next window change, so a user could stay inside a single
     * tracked app past their budget. A still-running tracking session
     * ([ScreenTimeTrackingFeature.trackingSinceTimestamp]) signals the user is still in the app.
     */
    private val budgetExhaustedChecker = Runnable {
        kotlin.runCatching {
            val packageName = lastTrackedPackage ?: return@Runnable
            if (!isActive() || trackingSinceTimestamp == 0L) return@Runnable
            if (DetoxDroidAccessibilityService.updateState() != DetoxDroidState.Active) return@Runnable
            if (!hasScreenTimeLeft()) enforceOn(DetoxDroidApplication.appContext, packageName)
        }
    }

    private fun scheduleBudgetExhaustedCheck() {
        mainHandler.removeCallbacks(budgetExhaustedChecker)
        if (allowedDailyScreenTime == NO_DAILY_LIMIT) return
        val remainingMs = allowedDailyScreenTime - currentUsedUpScreenTime()
        if (remainingMs <= 0) return
        mainHandler.postDelayed(budgetExhaustedChecker, remainingMs + 250)
    }

    /**
     * Whether the listed apps may still be used today: always with [NO_DAILY_LIMIT], never with a
     * limit of 0, otherwise until the used up screen time reaches [allowedDailyScreenTime].
     */
    private fun hasScreenTimeLeft(): Boolean = when (allowedDailyScreenTime) {
        NO_DAILY_LIMIT -> true
        0L -> false
        // includes the running tracking session, so the budget also runs out while the user stays
        // inside a single tracked app
        else -> currentUsedUpScreenTime() < allowedDailyScreenTime
    }

    /**
     * Whether an app may be held back by the wait right now. Not over the lock screen, where the
     * wait screen cannot show: an app there is nearly always taking a call, and the lock screen
     * would cover it. Not while the phone rings or is in a call either, whose screen must never
     * end up behind a wait.
     */
    private fun mayHoldBack(context: Context): Boolean {
        val keyguardLocked =
            context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        val audioMode =
            context.getSystemService(AudioManager::class.java)?.mode ?: AudioManager.MODE_NORMAL
        return !keyguardLocked && audioMode == AudioManager.MODE_NORMAL
    }

    override fun onPause(context: Context) {
        // a paused feature sees no more app switches, so the time in a listed app that is still
        // open stops counting here and not when the next app shows up, maybe the next morning
        eventuallyIncreaseUsedUpScreenTime()
        mainHandler.removeCallbacks(budgetExhaustedChecker)
        waitedForApps.clear()
        if (operationMode == DisableAppsMode.DEACTIVATE) {
            // if the apps are deactivated, we need to reactivate them when DetoxDroid is paused
            setAppsDeactivated(context, false, forceOperation = true)
        }
    }

    /**
     * When an app is opened, it is checked whether the app is in the list of apps that should be
     * disabled. If it is, the used up screen time is increased by the time since the last
     * disableable app was opened.
     *
     * If the user has already used up their daily screen time, the apps are disabled. Before
     * that, an app the user has not waited for yet is held back by the [WaitBeforeOpeningActivity].
     */
    override fun onAppOpened(
        context: Context, packageName: String, accessibilityEvent: AccessibilityEvent
    ) {
        if (!disableableApps.contains(packageName)) {
            // the app is not in the list of apps that should be disabled, so increase the used up
            // screen time and return
            eventuallyIncreaseUsedUpScreenTime()
            return
        }
        val screenTimeLeft = hasScreenTimeLeft()
        if (screenTimeLeft && waitBeforeOpening > 0L && packageName !in waitedForApps &&
            mayHoldBack(context)
        ) {
            // no tracking here: the wait is not screen time, and the wait screen stops the app
            // (its window event also ends any session a previous listed app left running)
            WaitBeforeOpeningActivity.start(context, packageName, waitBeforeOpening)
            return
        }
        eventuallyStartTracking() // start tracking the screen time
        lastTrackedPackage = packageName
        if (screenTimeLeft) {
            // the user has not used up their daily screen time yet; re-check when the budget is
            // expected to be exhausted, because no further window events arrive while the user
            // stays in this app
            scheduleBudgetExhaustedCheck()
            return
        }
        enforceOn(context, packageName)
    }

    /**
     * Applies the configured [operationMode] to [packageName]: shows the blocking overlay or
     * deactivates the configured apps via device admin.
     */
    private fun enforceOn(context: Context, packageName: String) {
        blockCounter.increment(packageName)
        when (operationMode) {
            DisableAppsMode.BLOCK -> {
                context.startService(Intent(context, AppDisabledOverlayService::class.java).apply {
                    putExtra(OverlayService.EXTRA_RUNNING_APP_PACKAGE_NAME, packageName)
                })
            }

            DisableAppsMode.DEACTIVATE -> {
                setAppsDeactivated(context, true)
            }
        }
    }

    /**
     * When the screen is turned off, the used up screen time is increased by the time since the
     * last disableable app was opened. Coming back to the phone counts as a new open, so every app
     * has to be waited for again.
     * @see eventuallyIncreaseUsedUpScreenTime
     */
    override fun onScreenTurnedOff(context: Context?) {
        eventuallyIncreaseUsedUpScreenTime()
        waitedForApps.clear()
    }

    /**
     * Sets whether the apps should be deactivated or not using the [DevicePolicyManager].
     * "Deactivated" means that the apps are not visible in the launcher anymore and cannot be
     * opened or used in any way.
     * @see DevicePolicyManager.setApplicationHidden
     */
    fun setAppsDeactivated(
        context: Context, deactivated: Boolean, forceOperation: Boolean = false
    ) {
        val skipOperation = !forceOperation && deactivated == isAppsDeactivated
        Timber.d("Setting apps deactivated: $deactivated (skip: $skipOperation)")
        if (skipOperation || !DetoxDroidDeviceAdminReceiver.isGranted(context)) {
            // the apps are already deactivated or the device admin permission is not granted, so
            // we cannot do anything
            return
        }
        val devicePolicyManager =
            (context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager)
        disableableApps.forEach {
            val appDeactivated = devicePolicyManager.setApplicationHidden(
                ComponentName(context, DetoxDroidDeviceAdminReceiver::class.java), it, deactivated
            )
            Timber.d("Operation on $it was successful: $appDeactivated")
        }
        isAppsDeactivated = deactivated
    }
}

