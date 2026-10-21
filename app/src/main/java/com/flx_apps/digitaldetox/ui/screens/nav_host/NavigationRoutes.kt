package com.flx_apps.digitaldetox.ui.screens.nav_host

import android.os.Parcelable
import com.flx_apps.digitaldetox.feature_types.FeatureId
import com.flx_apps.digitaldetox.rules.GroupId
import com.flx_apps.digitaldetox.ui.screens.permissions_required.GrantPermissionsCommand
import kotlinx.parcelize.Parcelize

/**
 * The routes that can be navigated to in the app. Although the [AppExceptions] route and the
 * [FeatureSchedule] route are in principle downstream of the [ManageFeature] and [RuleGroup]
 * routes, they are configured as top-level routes for the sake of simplicity. Both take the id
 * of what owns the list or the schedule: a group, or a feature that still has one of its own.
 */
sealed class NavigationRoutes : Parcelable {
    @Parcelize
    data object Home : NavigationRoutes()

    @Parcelize
    data object Onboarding : NavigationRoutes()

    @Parcelize
    data object ShizukuSetup : NavigationRoutes()

    @Parcelize
    data class ManageFeature(
        val featureId: FeatureId
    ) : NavigationRoutes()

    @Parcelize
    data class AppExceptions(
        val featureId: String
    ) : NavigationRoutes()

    @Parcelize
    data class FeatureSchedule(
        val featureId: String
    ) : NavigationRoutes()

    /** A group's screen: the rule as a sentence, today, the steps, the schedule. */
    @Parcelize
    data class RuleGroup(
        val groupId: GroupId
    ) : NavigationRoutes()

    /** The budget of a group, against its last seven days. */
    @Parcelize
    data class GroupBudget(
        val groupId: GroupId
    ) : NavigationRoutes()

    /** The short setup for a new group: a template, what counts, how strict. */
    @Parcelize
    data object NewRuleGroup : NavigationRoutes()

    @Parcelize
    data class PermissionsRequired(
        val grantPermissionsCommand: GrantPermissionsCommand
    ) : NavigationRoutes()

    @Parcelize
    data class UsageStats(
        /** Opens with the sheet of apps left out of the numbers already up. */
        val showExclusions: Boolean = false,
        /** Opens with the details of this app up, once its numbers are in. */
        val openApp: String? = null,
    ) : NavigationRoutes()

    @Parcelize
    data object About : NavigationRoutes()

    @Parcelize
    data object LogViewer : NavigationRoutes()
}