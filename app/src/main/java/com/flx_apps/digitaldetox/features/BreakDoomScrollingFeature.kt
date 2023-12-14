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
import com.flx_apps.digitaldetox.feature_types.NeedsDrawOverlayPermissionFeature
import com.flx_apps.digitaldetox.feature_types.NeedsPermissionsFeature
import com.flx_apps.digitaldetox.feature_types.OnScrollEventSubscriptionFeature
import com.flx_apps.digitaldetox.feature_types.SupportsAppExceptionsFeature
import com.flx_apps.digitaldetox.feature_types.SupportsScheduleFeature
import com.flx_apps.digitaldetox.system_integration.OverlayService
import com.flx_apps.digitaldetox.ui.screens.feature.break_doom_scrolling.BreakDoomScrollingFeatureSettingsSection
import com.flx_apps.digitaldetox.ui.screens.feature.break_doom_scrolling.BreakDoomScrollingOverlayService
import com.flx_apps.digitaldetox.util.AccessibilityEventUtil
import com.flx_apps.digitaldetox.util.SelfExpiringHashMap
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
    NeedsPermissionsFeature by NeedsDrawOverlayPermissionFeature() {
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
        if (AccessibilityEventUtil.getScrollDeltaY(accessibilityEvent) == 0) {
            // if the scroll view has not been scrolled, we will ignore it
            return
        }

        if (blockedApps.containsKey(accessibilityEvent.packageName.toString())) {
            // If the app is currently blocked, the user tries to scroll again after the break screen
            // has been opened. We will re-show the break screen, so that the user cannot continue
            // scrolling.
            openBreakScreen(context, accessibilityEvent.packageName.toString())
            return
        }

        val exceptionsContainApp = appExceptions.contains(accessibilityEvent.packageName.toString())
        if (exceptionsContainApp && appExceptionListType == AppExceptionListType.NOT_LIST) return
        if (!exceptionsContainApp && appExceptionListType == AppExceptionListType.ONLY_LIST) return

        val scrollViewInfo = activeScrollViews[scrollViewId]

        if (scrollViewInfo == null) {
            activeScrollViews[scrollViewId] = ScrollViewInfo(scrollViewSize)
        } else {
            var isInfiniteScrolling = false
            val growthSize = scrollViewSize - scrollViewInfo.sizeY
            scrollViewInfo.sizeY = scrollViewSize
            if (growthSize > 1) {
                // if the scroll view has grown by only one item, it is probably a "normal" scroll view,
                // because in infinite scroll views, the scroll view usually grows by a specific amount
                // (e.g. 10 items)
                scrollViewInfo.timesGrown++
            } else if (growthSize < 0) {
                // if the scroll view has shrunk, we reset the timesGrown counter
                scrollViewInfo.timesGrown = 0
            }

            if (scrollViewInfo.timesGrown >= 3) {
                // assume that a scroll view is infinite, if its size has grown three times
                isInfiniteScrolling = true
            }

            val scrollingTime = System.currentTimeMillis() - scrollViewInfo.added
            if (isInfiniteScrolling && scrollingTime >= timeUntilWarning) {
                // if the scroll view is seemingly infinite and the user has been too long on it,
                // we assume that they are doomscrolling and open the break screen
                openBreakScreen(context, accessibilityEvent.packageName.toString())
                activeScrollViews.clear()
            }
        }
    }

    private fun openBreakScreen(context: Context, packageName: String) {
        context.startService(Intent(
            context, BreakDoomScrollingOverlayService::class.java
        ).apply {
            putExtra(
                OverlayService.EXTRA_RUNNING_APP_PACKAGE_NAME, packageName
            )
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

        override fun toString(): String {
            return "ScrollViewInfo(sizeY=$sizeY, added=$added, timesGrown=$timesGrown)"
        }
    }
}

