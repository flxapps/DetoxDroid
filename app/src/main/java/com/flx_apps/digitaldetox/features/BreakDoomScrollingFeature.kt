package com.flx_apps.digitaldetox.features

import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.longPreferencesKey
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.data.DataStoreProperty
import com.flx_apps.digitaldetox.feature_types.AppExceptionListType
import com.flx_apps.digitaldetox.feature_types.Feature
import com.flx_apps.digitaldetox.feature_types.FeatureTexts
import com.flx_apps.digitaldetox.feature_types.LockableFeature
import com.flx_apps.digitaldetox.feature_types.PausableFeature
import com.flx_apps.digitaldetox.feature_types.NeedsDrawOverlayPermissionFeature
import com.flx_apps.digitaldetox.feature_types.NeedsPermissionsFeature
import com.flx_apps.digitaldetox.feature_types.OnScrollEventSubscriptionFeature
import com.flx_apps.digitaldetox.feature_types.SupportsAppExceptionsFeature
import com.flx_apps.digitaldetox.feature_types.SupportsScheduleFeature
import com.flx_apps.digitaldetox.system_integration.OverlayService
import com.flx_apps.digitaldetox.ui.screens.feature.break_doom_scrolling.BreakDoomScrollingFeatureSettingsSection
import com.flx_apps.digitaldetox.ui.screens.feature.break_doom_scrolling.BreakDoomScrollingOverlayService
import com.flx_apps.digitaldetox.util.AccessibilityEventUtil
import com.flx_apps.digitaldetox.util.DailyAppCounter
import com.flx_apps.digitaldetox.util.SelfExpiringHashMap
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

val BreakDoomScrollingFeatureId = Feature.createId(BreakDoomScrollingFeature::class.java)

/**
 * This feature can detect infinite scrolling behavior and open a break screen if the user has
 * been scrolling for a defined amount of time.
 */
object BreakDoomScrollingFeature : Feature(), OnScrollEventSubscriptionFeature,
    SupportsAppExceptionsFeature by SupportsAppExceptionsFeature.Impl(
        BreakDoomScrollingFeatureId,
        defaultExceptionListType = AppExceptionListType.ONLY_LIST,
    ), SupportsScheduleFeature by SupportsScheduleFeature.Impl(BreakDoomScrollingFeatureId),
    NeedsPermissionsFeature by NeedsDrawOverlayPermissionFeature(), LockableFeature, PausableFeature {
    override val texts: FeatureTexts = FeatureTexts(
        R.string.feature_doomScrolling,
        R.string.feature_doomScrolling_subtitle,
        R.string.feature_doomScrolling_description,
    )
    override val iconRes = R.drawable.ic_scroll
    override val settingsContent: @Composable () -> Unit =
        { BreakDoomScrollingFeatureSettingsSection() }

    /**
     * The time in milliseconds until the break screen is opened, if the user has been scrolling for
     * too long.
     */
    var timeUntilWarning: Long by DataStoreProperty(
        longPreferencesKey("${BreakDoomScrollingFeatureId}_timeUntilWarning"),
        TimeUnit.MINUTES.toMillis(3)
    )

    /**
     * Per-app count of doom-scroll breaks triggered today. Resets automatically at midnight and is
     * persisted into the usage-stats database by the snapshot worker.
     */
    val breakCounter = DailyAppCounter()

    /**
     * Contains information about all currently active scroll views, associated with their IDs.
     * The information is automatically removed, if a scroll view has not been scrolled for one
     * minute.
     */
    private val activeScrollViews: SelfExpiringHashMap<Int, ScrollViewInfo> =
        SelfExpiringHashMap(2.minutes.inWholeMilliseconds)

    /**
     * Contains the package names of apps that are currently blocked, associated with the time
     * when they were blocked.
     */
    private val blockedApps = SelfExpiringHashMap<String, Long>(3.minutes.inWholeMilliseconds)

    /**
     * If a scroll view is scrolled, we check whether it has grown three times in size and if it
     * has, we assume that it is infinite and the user is doomscrolling. If the user has been
     * scrolling for too long, we open the break screen.
     */
    override fun onScrollEvent(
        context: Context,
        scrollViewId: Int,
        scrollViewSize: Int,
        accessibilityEvent: AccessibilityEvent
    ) {
        val pkg = accessibilityEvent.packageName.toString()
        if (!AccessibilityEventUtil.isDownScrollEvent(accessibilityEvent)) return

        if (blockedApps.containsKey(pkg)) {
            // If the app is currently blocked, the user tries to scroll again after the break screen
            // has been opened. We will re-show the break screen, so that the user cannot continue
            // scrolling. Re-shows are not counted as new breaks and do not offer a snooze.
            openBreakScreen(context, pkg, offerSnooze = false)
            return
        }

        if (!appliesTo(pkg)) return

        val scrollViewInfo = activeScrollViews[scrollViewId]

        if (scrollViewInfo == null) {
            activeScrollViews[scrollViewId] = ScrollViewInfo(scrollViewSize)
            Timber.d(
                "New scroll view registered id=%d size=%d pkg=%s", scrollViewId, scrollViewSize, pkg
            )
        } else {
            val growthSize = scrollViewSize - scrollViewInfo.sizeY
            scrollViewInfo.sizeY = scrollViewSize

            if (growthSize > 1) {
                // if the scroll view has grown by only one item, it is probably a "normal" scroll view,
                // because in infinite scroll views, the scroll view usually grows by a specific amount
                // (e.g. 10 items)
                scrollViewInfo.timesGrown++
            } else if (growthSize < 0) {
                // if the scroll view has shrunk, we reset the timesGrown counter and warned flag
                scrollViewInfo.timesGrown = 0
                scrollViewInfo.warned = false
            }

            if (scrollViewInfo.timesGrown >= 3 && !scrollViewInfo.warned) {
                // assume that a scroll view is infinite, if its size has grown three times
                val scrollingTime = System.currentTimeMillis() - scrollViewInfo.added
                if (scrollingTime >= timeUntilWarning) {
                    // if the scroll view is seemingly infinite and the user has been too long on it,
                    // we assume that they are doomscrolling and open the break screen
                    Timber.i("Break screen triggered after %d ms for pkg=%s", scrollingTime, pkg)
                    scrollViewInfo.warned = true
                    breakCounter.increment(pkg)
                    openBreakScreen(context, pkg, offerSnooze = true)
                }
            }
        }
    }

    /**
     * Grants a grace period after the user tapped "give me a bit more time" on the break screen:
     * the app is unblocked and all tracked scroll views are forgotten, so the doom-scrolling
     * timer ([timeUntilWarning]) starts over before the next warning can appear.
     */
    fun snoozeApp(packageName: String) {
        blockedApps.remove(packageName)
        activeScrollViews.clear()
    }

    private fun openBreakScreen(context: Context, packageName: String, offerSnooze: Boolean) {
        context.startService(
            Intent(
                context, BreakDoomScrollingOverlayService::class.java
            ).apply {
                putExtra(OverlayService.EXTRA_RUNNING_APP_PACKAGE_NAME, packageName)
                putExtra(BreakDoomScrollingOverlayService.EXTRA_OFFER_SNOOZE, offerSnooze)
            })
        blockedApps[packageName] = System.currentTimeMillis()
    }

    /**
     * Contains information about a scroll view, i.e. when it was added, how often it has grown
     * and how big it is.
     */
    data class ScrollViewInfo(var sizeY: Int) {
        val added: Long = System.currentTimeMillis()
        var timesGrown = 0
        var warned = false

        override fun toString(): String {
            return "ScrollViewInfo(sizeY=$sizeY, added=$added, timesGrown=$timesGrown, warned=$warned)"
        }
    }
}

