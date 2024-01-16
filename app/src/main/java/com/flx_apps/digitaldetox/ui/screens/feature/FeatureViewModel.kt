package com.flx_apps.digitaldetox.ui.screens.feature

import android.app.Application
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flx_apps.digitaldetox.feature_types.Feature
import com.flx_apps.digitaldetox.feature_types.FeatureId
import com.flx_apps.digitaldetox.feature_types.NeedsPermissionsFeature
import com.flx_apps.digitaldetox.features.FeaturesProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.olshevski.navigation.reimagined.hilt.hiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * A factory for the view model of the feature screen. It is used to pass the feature ID to the
 * view model.
 */
abstract class FeatureViewModelFactory {
    @Composable
    inline fun <reified T : FeatureViewModel> withFeatureId(featureId: FeatureId): T {
        Timber.d("withFeatureId: $featureId")
        return hiltViewModel(defaultArguments = bundleOf("featureId" to featureId))
    }
}

/**
 * The view model for the feature screen. It contains the feature and provides methods to toggle
 * the active state of the feature.
 */
@HiltViewModel
open class FeatureViewModel @Inject constructor(
    private val application: Application, savedStateHandle: androidx.lifecycle.SavedStateHandle
) : AndroidViewModel(application) {
    companion object : FeatureViewModelFactory()

    init {
        Timber.d("savedStateHandle: $savedStateHandle")
    }

    private val featureId: String = savedStateHandle["featureId"]!!
    val feature: Feature = FeaturesProvider.getFeatureById(featureId)!!

    private val _featureIsActive = MutableStateFlow(feature.isActivated)
    val featureIsActive: StateFlow<Boolean> = _featureIsActive

    private val _snackbarState = FeatureScreenSnackbarStateProvider.snackbarState

    /**
     * Checks if the feature needs permissions to be activated and if the app has the permissions.
     * @return True if the feature needs permissions and the app has not the permissions, false
     * otherwise.
     */
    fun activationNeedsPermission(): Boolean {
        return feature is NeedsPermissionsFeature && !feature.hasPermissions(application)
    }

    /**
     * Toggles the active state of the feature.
     * @return The new active state of the feature or null if the necessary permissions are missing
     */
    internal fun toggleFeatureActive(): Boolean? {
        if (activationNeedsPermission()) {
            return null
        }
        feature.isActivated = !feature.isActivated
        _featureIsActive.value = feature.isActivated
        FeaturesProvider.startOrStopFeature(feature)
        return feature.isActivated
    }

    fun showSnackbar(
        message: String,
        actionLabel: String? = null,
        duration: SnackbarDuration = SnackbarDuration.Indefinite,
        onResult: (SnackbarResult) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = _snackbarState.showSnackbar(
                message = message, actionLabel = actionLabel, duration = duration
            )
            onResult(result)
        }
    }
}