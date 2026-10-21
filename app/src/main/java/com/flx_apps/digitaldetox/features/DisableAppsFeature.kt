package com.flx_apps.digitaldetox.features

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.stringPreferencesKey
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.data.DataStoreProperty
import com.flx_apps.digitaldetox.data.DataStorePropertyTransformer
import com.flx_apps.digitaldetox.feature_types.BrakeAwareFeature
import com.flx_apps.digitaldetox.feature_types.Feature
import com.flx_apps.digitaldetox.feature_types.FeatureTexts
import com.flx_apps.digitaldetox.feature_types.NeedsDrawOverlayPermissionFeature
import com.flx_apps.digitaldetox.feature_types.NeedsPermissionsFeature
import com.flx_apps.digitaldetox.feature_types.OnAppOpenedSubscriptionFeature
import com.flx_apps.digitaldetox.feature_types.OnScreenTurnedOffSubscriptionFeature
import com.flx_apps.digitaldetox.feature_types.PausableFeature
import com.flx_apps.digitaldetox.rules.RuleGroups
import com.flx_apps.digitaldetox.rules.Rules
import com.flx_apps.digitaldetox.system_integration.AppSuspender
import com.flx_apps.digitaldetox.system_integration.DetoxDroidAccessibilityService
import com.flx_apps.digitaldetox.system_integration.DetoxDroidDeviceAdminReceiver
import com.flx_apps.digitaldetox.system_integration.OverlayService
import com.flx_apps.digitaldetox.ui.screens.feature.disable_apps.AppDisabledOverlayService
import com.flx_apps.digitaldetox.ui.screens.feature.disable_apps.DisableAppsFeatureSettingsSection
import com.flx_apps.digitaldetox.ui.screens.feature.disable_apps.WaitBeforeOpeningActivity
import com.flx_apps.digitaldetox.util.DailyAppCounter
import com.flx_apps.digitaldetox.websites.Websites
import timber.log.Timber

/**
 * How a locked app is kept out of reach.
 *
 * [BLOCK] draws a screen over it and needs the overlay permission, which anyone can grant.
 * [DEACTIVATE] hides it outright and needs device owner, which a phone with any account on it
 * cannot give, so for most people it is not an option at all. [SUSPEND] sits between the two: the
 * app is greyed out in the launcher and refuses to start, and all it needs is Shizuku.
 * @see NeedsPermissionsFeature
 * @see android.Manifest.permission.SYSTEM_ALERT_WINDOW
 * @see android.Manifest.permission.BIND_DEVICE_ADMIN
 * @see AppSuspender
 */
enum class DisableAppsMode {
    DEACTIVATE, BLOCK, SUSPEND,
}

val DisableAppsFeatureId = Feature.createId(DisableAppsFeature::class.java)

/**
 * Runs the wait and the lock steps of the groups (see [com.flx_apps.digitaldetox.rules.Step]).
 * Which apps and websites, when, and after how much use is the groups' business; what happens then
 * is decided here: a wait screen before an open, and once a group's lock has taken hold a block
 * screen, a suspension or a hidden launcher icon, depending on [operationMode].
 *
 * The feature is on whenever a switched-on group uses one of its steps.
 */
object DisableAppsFeature : Feature(), OnAppOpenedSubscriptionFeature,
    OnScreenTurnedOffSubscriptionFeature,
    NeedsPermissionsFeature by NeedsDrawOverlayPermissionFeature(),
    PausableFeature, BrakeAwareFeature {
    override val texts: FeatureTexts = FeatureTexts(
        title = R.string.feature_disableApps,
        subtitle = R.string.feature_disableApps_subtitle,
        description = R.string.feature_disableApps_description,
    )
    override val iconRes: Int = R.drawable.ic_disable_app
    override val settingsContent: @Composable () -> Unit = {
        DisableAppsFeatureSettingsSection()
    }

    /** On while any switched-on group waits or locks; the groups are the switch. */
    override var isActivated: Boolean
        get() = Rules.usesFeature(this)
        set(@Suppress("UNUSED_PARAMETER") value) {}

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
     * A website the user chose not to wait for. Leaving the wait screen returns to the browser, and
     * when the browser shows that website again right then, it goes back from it instead of
     * starting the wait over.
     */
    private var leavingWebsite: String? = null

    /** Called when the user leaves the wait for the website [entry], see [leavingWebsite]. */
    fun leaveWebsiteOnReturn(entry: String) {
        leavingWebsite = entry
    }

    /**
     * How a locked app is kept out of reach. By default [DisableAppsMode.BLOCK], because
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

    /** The apps this feature has hidden through device admin and not shown again. */
    private var hiddenApps = setOf<String>()

    /**
     * The apps that are locked right now: named by a group whose lock has taken hold. Opening one
     * ends on the block screen, or finds it deactivated or suspended. None while DetoxDroid is not
     * running or the feature is paused.
     */
    fun blockedApps(): Set<String> {
        val enforcing = DetoxDroidAccessibilityService.instance != null && isActive() &&
                !PauseButtonFeature.isFeaturePaused(this)
        return if (enforcing) Rules.lockedApps() else emptySet()
    }

    /** Every app a switched-on group can lock, whether its lock has taken hold or not. */
    private fun lockableApps(): Set<String> = RuleGroups.enabled()
        .filter { it.locks && it.canHoldBackApps }
        .flatMapTo(mutableSetOf()) { Websites.appsOf(it.appExceptions) }

    /**
     * A brake takes the locked apps away the moment it is pulled, where the mode can do that
     * without one of them being opened first, and its end hands back what is no longer locked.
     * Blocking has nothing to do here: its screen goes up when a locked app is opened.
     */
    override fun onBrakeChanged(context: Context) {
        syncHeldBackApps(context)
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

    /**
     * Nothing may be held back while the feature is not enforcing, so a suspension left over from
     * an earlier run is handed back here. The budget that was used up when it was made has reset
     * in the meantime, or the schedule that justified it has closed, or the mode has changed.
     */
    override fun onStart(context: Context) {
        releaseSuspendedAppsIfEntitled()
    }

    override fun onPause(context: Context) {
        waitedForApps.clear()
        if (operationMode == DisableAppsMode.DEACTIVATE) {
            // whatever is hidden comes back when DetoxDroid is paused
            setAppsDeactivated(context, lockableApps() + hiddenApps, false)
        }
        AppSuspender.releaseAll()
    }

    /**
     * Hands back anything that is still suspended but should not be. A suspension outlives our
     * process and the Shizuku session that made it, and nothing in the system lifts it on its own,
     * so every path that could have ended the enforcement calls this and the periodic service
     * check calls it again, for the case where Shizuku was gone when it happened.
     */
    fun releaseSuspendedAppsIfEntitled() {
        if (!AppSuspender.hasSuspendedApps) return
        val stillLocked = if (isActive() && operationMode == DisableAppsMode.SUSPEND) {
            Rules.lockedApps()
        } else {
            emptySet()
        }
        AppSuspender.release(AppSuspender.suspended - stillLocked)
    }

    /**
     * Brings the suspended and hidden apps in line with what the groups lock right now, for the
     * modes that keep apps away without a block screen.
     */
    private fun syncHeldBackApps(context: Context) {
        when (operationMode) {
            DisableAppsMode.BLOCK -> {}
            DisableAppsMode.SUSPEND -> {
                val locked = Rules.lockedApps()
                AppSuspender.release(AppSuspender.suspended - locked)
                AppSuspender.suspend(locked)
            }

            DisableAppsMode.DEACTIVATE -> {
                val locked = Rules.lockedApps()
                setAppsDeactivated(context, hiddenApps - locked, false)
                setAppsDeactivated(context, locked, true)
            }
        }
    }

    /**
     * When an app or website comes to the front, the groups say whether it waits, opens or is
     * locked. The wait comes first, unless the app has been waited for since the screen last came
     * on; a lock that has taken hold wins over the wait.
     */
    override fun onAppOpened(
        context: Context, packageName: String, accessibilityEvent: AccessibilityEvent
    ) {
        val leaving = leavingWebsite
        leavingWebsite = null
        if (packageName == leaving) {
            DetoxDroidAccessibilityService.instance?.leaveWebsite()
            return
        }
        when (val decision = Rules.lockDecision(packageName)) {
            Rules.LockDecision.NotCovered -> {
                // a suspended app cannot report its own window events, so a budget that reset
                // or a window that closed reaches the locked apps on the next other app
                releaseSuspendedAppsIfEntitled()
            }

            is Rules.LockDecision.Wait -> {
                if (packageName !in waitedForApps && mayHoldBack(context)) {
                    WaitBeforeOpeningActivity.start(
                        context, packageName, decision.waitMs,
                        label = DetoxDroidAccessibilityService.instance?.currentWebsiteName()
                            ?.takeIf { Websites.isWebsite(packageName) }
                    )
                }
            }

            is Rules.LockDecision.Open -> {
                // the groups count the time and come back through reevaluateForeground when the
                // budget runs out
            }

            is Rules.LockDecision.Locked -> enforceOn(context, packageName)
        }
    }

    /**
     * Applies the configured [operationMode] to [packageName]: shows the blocking overlay or takes
     * the locked apps away through device admin or Shizuku. A website cannot be deactivated or
     * suspended, so it gets the block screen in every mode.
     */
    private fun enforceOn(context: Context, packageName: String) {
        blockCounter.increment(packageName)
        val mode = if (Websites.isWebsite(packageName)) DisableAppsMode.BLOCK else operationMode
        when (mode) {
            DisableAppsMode.BLOCK -> {
                context.startService(Intent(context, AppDisabledOverlayService::class.java).apply {
                    putExtra(OverlayService.EXTRA_RUNNING_APP_PACKAGE_NAME, packageName)
                })
            }

            DisableAppsMode.DEACTIVATE, DisableAppsMode.SUSPEND -> syncHeldBackApps(context)
        }
    }

    /**
     * Coming back to the phone counts as a new open, so every app has to be waited for again.
     */
    override fun onScreenTurnedOff(context: Context?) {
        waitedForApps.clear()
        leavingWebsite = null
        releaseSuspendedAppsIfEntitled()
    }

    /**
     * Hides [apps] from the launcher, or shows them again, using the [DevicePolicyManager].
     * "Deactivated" means that the apps are not visible in the launcher anymore and cannot be
     * opened or used in any way.
     * @see DevicePolicyManager.setApplicationHidden
     */
    fun setAppsDeactivated(context: Context, apps: Set<String>, deactivated: Boolean) {
        if (apps.isEmpty() || !DetoxDroidDeviceAdminReceiver.isGranted(context)) return
        Timber.d("Setting apps deactivated: $deactivated for $apps")
        val devicePolicyManager =
            (context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager)
        apps.forEach {
            val appDeactivated = devicePolicyManager.setApplicationHidden(
                ComponentName(context, DetoxDroidDeviceAdminReceiver::class.java), it, deactivated
            )
            Timber.d("Operation on $it was successful: $appDeactivated")
        }
        hiddenApps = if (deactivated) hiddenApps + apps else hiddenApps - apps
    }

    /** Shows every app this feature hid again, whatever the groups say. */
    fun reactivateAllApps(context: Context) {
        setAppsDeactivated(context, lockableApps() + hiddenApps, false)
    }
}
