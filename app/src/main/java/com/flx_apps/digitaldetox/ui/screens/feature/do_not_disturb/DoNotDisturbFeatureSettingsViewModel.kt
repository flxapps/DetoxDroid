package com.flx_apps.digitaldetox.ui.screens.feature.do_not_disturb

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.flx_apps.digitaldetox.util.NavigationUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DoNotDisturbFeatureSettingsViewModel @Inject constructor(application: Application) :
    AndroidViewModel(application) {
    /**
     * Opens the system settings for the do not disturb feature.
     */
    fun openDoNotDisturbSystemSettings() {
        NavigationUtil.openDoNotDisturbSettings(getApplication())
    }
}