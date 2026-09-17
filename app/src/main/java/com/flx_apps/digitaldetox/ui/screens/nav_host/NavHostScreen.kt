package com.flx_apps.digitaldetox.ui.screens.nav_host

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flx_apps.digitaldetox.ui.screens.about.AboutScreen
import com.flx_apps.digitaldetox.ui.screens.app_exceptions.ManageAppExceptionsScreen
import com.flx_apps.digitaldetox.ui.screens.feature.FeatureScreen
import com.flx_apps.digitaldetox.ui.screens.home.HomeScreen
import com.flx_apps.digitaldetox.ui.screens.logs.LogViewerScreen
import com.flx_apps.digitaldetox.ui.screens.onboarding.OnboardingScreen
import com.flx_apps.digitaldetox.ui.screens.permissions_required.PermissionsRequiredScreen
import com.flx_apps.digitaldetox.ui.screens.premium.PremiumSheetHost
import com.flx_apps.digitaldetox.ui.screens.schedule.FeatureScheduleScreen
import com.flx_apps.digitaldetox.ui.screens.shizuku_setup.ShizukuSetupScreen
import com.flx_apps.digitaldetox.ui.screens.usage_stats.UsageStatsScreen
import dev.olshevski.navigation.reimagined.AnimatedNavHost
import dev.olshevski.navigation.reimagined.NavAction
import dev.olshevski.navigation.reimagined.NavTransitionSpec

private const val TransitionMillis = 300

/**
 * Going deeper slides the next screen in from the end as it fades in, going back slides the other
 * way, the way Android's own settings move. Replacing the whole backstack, as leaving the
 * onboarding does, only fades.
 */
private val ScreenTransitions = NavTransitionSpec<NavigationRoutes> { action, _, _ ->
    val towards = when (action) {
        NavAction.Navigate -> SlideDirection.Start
        NavAction.Pop -> SlideDirection.End
        else -> null
    }
    val slide = tween<IntOffset>(TransitionMillis, easing = FastOutSlowInEasing)
    val fadeIn = fadeIn(tween(TransitionMillis * 2 / 3, delayMillis = TransitionMillis / 3))
    val fadeOut = fadeOut(tween(TransitionMillis / 3))
    if (towards == null) {
        fadeIn togetherWith fadeOut
    } else {
        (fadeIn + slideIntoContainer(towards, slide) { it / 10 }) togetherWith
                (fadeOut + slideOutOfContainer(towards, slide) { it / 10 })
    }
}

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
        backstack = navViewModel.backstack,
        transitionSpec = ScreenTransitions
    ) { route ->
        when (route) {
            is NavigationRoutes.Home -> HomeScreen()

            is NavigationRoutes.Onboarding -> OnboardingScreen()

            is NavigationRoutes.ShizukuSetup -> ShizukuSetupScreen()

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

            is NavigationRoutes.UsageStats -> UsageStatsScreen()

            is NavigationRoutes.About -> AboutScreen()

            is NavigationRoutes.LogViewer -> LogViewerScreen()
        }
    }

    PremiumSheetHost()
}