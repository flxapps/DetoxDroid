package com.flx_apps.digitaldetox

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavHostScreen
import com.flx_apps.digitaldetox.ui.theme.DetoxDroidTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * From the main activity, the user can turn on/off and configure the app's features.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        setContent {
            DetoxDroidTheme(darkTheme = false) { // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    NavHostScreen()
                }
            }
        }
    }
}
