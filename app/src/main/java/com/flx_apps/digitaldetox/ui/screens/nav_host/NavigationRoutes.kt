package com.flx_apps.digitaldetox.ui.screens.nav_host

import android.os.Parcelable
import com.flx_apps.digitaldetox.feature_types.FeatureId
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavigationRoutes.AppExceptions
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavigationRoutes.FeatureSchedule
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavigationRoutes.ManageFeature
import kotlinx.parcelize.Parcelize

/**
 * The routes that can be navigated to in the app. Although the [AppExceptions] route and the
 * [FeatureSchedule] route are in principle downstream of the [ManageFeature] route, they are
 * configured as top-level routes for the sake of simplicity.
 */
sealed class NavigationRoutes : Parcelable {
    @Parcelize
    data object Home : NavigationRoutes()

    @Parcelize
    data class ManageFeature(
        val featureId: FeatureId
    ) : NavigationRoutes()

    @Parcelize
    data class AppExceptions(
        val featureId: FeatureId
    ) : NavigationRoutes()

    @Parcelize
    data class FeatureSchedule(
        val featureId: FeatureId
    ) : NavigationRoutes()

    @Parcelize
    data class PermissionsRequired(
        val grantPermissionsCommand: String
    ) : NavigationRoutes()

    @Parcelize
    data object UsageStats : NavigationRoutes()
}