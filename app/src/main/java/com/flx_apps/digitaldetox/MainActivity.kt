package com.flx_apps.digitaldetox

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.flx_apps.digitaldetox.feature_types.FeatureId
import com.flx_apps.digitaldetox.features.FeaturesProvider
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavHostScreen
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavigationRoutes
import com.flx_apps.digitaldetox.ui.theme.DetoxDroidTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * From the main activity, the user can turn on/off and configure the app's features.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val navViewModel: NavViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DetoxDroidTheme { // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    NavHostScreen()
                }
            }
        }
        // a recreated activity has its backstack back already, link included
        if (savedInstanceState == null) followLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        followLink(intent)
    }

    /** Opens the screen a link from a widget or the notification points at. */
    private fun followLink(intent: Intent?) {
        when (intent?.action) {
            ACTION_OPEN_USAGE_STATS -> navViewModel.openFromOutside(
                NavigationRoutes.UsageStats(openApp = intent.data?.schemeSpecificPart)
            )

            ACTION_OPEN_FEATURE -> intent.data?.schemeSpecificPart
                ?.takeIf { id -> FeaturesProvider.featureList.any { it.id == id } }
                ?.let { navViewModel.openFromOutside(NavigationRoutes.ManageFeature(it)) }

            ACTION_OPEN_HOME -> navViewModel.openFromOutside(NavigationRoutes.Home)
        }
    }

    companion object {
        /*
         * Actions and data rather than extras: pending intents are told apart by those, not by
         * extras, so with extras every link here would be the same token as any other plain
         * intent for this activity, and whichever was created last would decide where all of them
         * lead.
         */
        private const val ACTION_OPEN_USAGE_STATS = "com.flx_apps.digitaldetox.OPEN_USAGE_STATS"
        private const val ACTION_OPEN_FEATURE = "com.flx_apps.digitaldetox.OPEN_FEATURE"
        private const val ACTION_OPEN_HOME = "com.flx_apps.digitaldetox.OPEN_HOME"

        /** Opens the usage screen in the running DetoxDroid task, or in a new one. */
        fun openUsageStatsIntent(context: Context): PendingIntent =
            link(context, ACTION_OPEN_USAGE_STATS)

        /** Opens the usage screen with the details of [packageName] up. */
        fun openAppUsageIntent(context: Context, packageName: String): PendingIntent =
            link(context, ACTION_OPEN_USAGE_STATS, Uri.fromParts("package", packageName, null))

        /** Opens the settings of the feature with [featureId]. */
        fun openFeatureIntent(context: Context, featureId: FeatureId): PendingIntent =
            link(context, ACTION_OPEN_FEATURE, Uri.fromParts("feature", featureId, null))

        /** Opens the start screen, where DetoxDroid is switched on. */
        fun openHomeIntent(context: Context): PendingIntent = link(context, ACTION_OPEN_HOME)

        private fun link(context: Context, action: String, data: Uri? = null): PendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .setAction(action)
                    .setData(data)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                    ),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
