package com.flx_apps.digitaldetox.ui.screens.nav_host

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flx_apps.digitaldetox.MainActivity
import com.flx_apps.digitaldetox.feature_types.FeatureId
import com.flx_apps.digitaldetox.features.FeaturesProvider
import com.flx_apps.digitaldetox.ui.screens.permissions_required.PermissionsRequiredScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.olshevski.navigation.reimagined.navController
import dev.olshevski.navigation.reimagined.navigate
import dev.olshevski.navigation.reimagined.pop
import javax.inject.Inject

val DefaultFeatureId = FeaturesProvider.featureList[0].id

/**
 * The view model for the navigation host. It is responsible for managing the navigation state.
 */
@HiltViewModel
class NavViewModel @Inject constructor(private val savedStateHandle: SavedStateHandle) :
    ViewModel() {
    companion object {
        /**
         * A factory function that creates a [NavViewModel]. We need to set the [viewModelStoreOwner]
         * to the [MainActivity] in order to avoid creating multiple instances of the view model.
         */
        @Composable
        fun navViewModel(): NavViewModel =
            viewModel(viewModelStoreOwner = LocalContext.current as MainActivity)
    }

    /**
     * The navigation controller that manages the navigation state.
     */
    private val navController =
        navController<NavigationRoutes>(startDestination = NavigationRoutes.Home)

    // You may either make navController public or just its backstack. The latter is convenient
    // when you don't want to expose navigation methods in the UI layer.
    val backstack get() = navController.backstack
    val isBackHandlerEnabled get() = navController.backstack.entries.size > 1

    /**
     * Handles the back press event.
     */
    fun onBackPress() {
        navController.pop()
    }

    fun openManageFeatureScreen(
        featureId: FeatureId
    ) {
        savedStateHandle["featureId"] = featureId
        navController.navigate(NavigationRoutes.ManageFeature(featureId))
    }

    fun openAppExceptionsScreen(
        featureId: FeatureId = savedStateHandle["featureId"] ?: DefaultFeatureId
    ) {
        savedStateHandle["featureId"] = featureId
        navController.navigate(NavigationRoutes.AppExceptions(featureId))
    }

    fun openFeatureScheduleScreen(
        featureId: FeatureId = savedStateHandle["featureId"] ?: DefaultFeatureId
    ) {
        savedStateHandle["featureId"] = featureId
        navController.navigate(NavigationRoutes.FeatureSchedule(featureId))
    }

    /**
     * Opens the screen to show the user how to grant required permissions.
     * @see PermissionsRequiredScreen
     */
    fun openPermissionsRequiredScreen(grantPermissionsCommand: String) {
        navController.navigate(NavigationRoutes.PermissionsRequired(grantPermissionsCommand))
    }
}