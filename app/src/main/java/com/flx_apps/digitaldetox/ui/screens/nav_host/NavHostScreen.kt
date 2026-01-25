package com.flx_apps.digitaldetox.ui.screens.nav_host

import ManageAppExceptionsScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flx_apps.digitaldetox.ui.screens.feature.FeatureScreen
import com.flx_apps.digitaldetox.ui.screens.home.HomeScreen
import com.flx_apps.digitaldetox.ui.screens.logs.LogViewerScreen
import com.flx_apps.digitaldetox.ui.screens.permissions_required.PermissionsRequiredScreen
import com.flx_apps.digitaldetox.ui.screens.schedule.FeatureScheduleScreen
import dev.olshevski.navigation.reimagined.AnimatedNavHost

/**
 * The navigation host for the app. It is responsible for routing to the correct screen based on
 * the current navigation state.
 */
@Composable
fun NavHostScreen(navViewModel: NavViewModel = viewModel(viewModelStoreOwner = LocalActivity.current as ComponentActivity)) {
    BackHandler(navViewModel.isBackHandlerEnabled) {
        navViewModel.onBackPress()
    }

    AnimatedNavHost(
        backstack = navViewModel.backstack
    ) { route ->
        when (route) {
            is NavigationRoutes.Home -> HomeScreen()

            is NavigationRoutes.ManageFeature -> FeatureScreen(
                featureId = route.featureId
            )

            is NavigationRoutes.AppExceptions -> ManageAppExceptionsScreen(
                featureId = route.featureId
            )

            is NavigationRoutes.FeatureSchedule -> FeatureScheduleScreen(
                featureId = route.featureId
            )

            is NavigationRoutes.PermissionsRequired -> PermissionsRequiredScreen(
                grantPermissionsCommand = route.grantPermissionsCommand
            )

            is NavigationRoutes.UsageStats -> null

            is NavigationRoutes.LogViewer -> LogViewerScreen()
        }
    }
}