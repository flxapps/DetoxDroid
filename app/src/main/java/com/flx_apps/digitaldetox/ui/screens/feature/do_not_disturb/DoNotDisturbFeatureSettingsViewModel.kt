package com.flx_apps.digitaldetox.ui.screens.feature.do_not_disturb

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DoNotDisturbFeatureSettingsViewModel @Inject constructor(application: Application) :
    AndroidViewModel(application) {
    /**
     * Opens the system settings for the do not disturb feature.
     */
    fun openDoNotDisturbSystemSettings() {
        val intent = Intent("android.settings.ZEN_MODE_PRIORITY_SETTINGS").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
    }
}