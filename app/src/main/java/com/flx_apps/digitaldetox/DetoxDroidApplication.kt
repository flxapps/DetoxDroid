package com.flx_apps.digitaldetox

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * The main application class. It is used to initialize the dependency injection framework.
 */
@HiltAndroidApp
class DetoxDroidApplication : Application() {
    companion object {
        lateinit var appContext: Application
    }

    override fun onCreate() {
        super.onCreate()
        appContext = this
    }
}