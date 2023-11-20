package com.flx_apps.digitaldetox.system_integration

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Service that can display an overlay on top of other apps
 * @param content the content to display
 */
abstract class OverlayService(private val overlayContent: OverlayContent) : LifecycleService(),
    SavedStateRegistryOwner {
    companion object {
        /**
         * May be used to pass the package name of the app that is currently running in the
         * foreground to the overlay service via an intent.
         */
        const val EXTRA_RUNNING_APP_PACKAGE_NAME: String = "runningAppPackageName"
    }

    private lateinit var savedStateRegistryController: SavedStateRegistryController
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
    private lateinit var contentView: View

    private lateinit var runningAppPackageName: String

    override fun onCreate() {
        super.onCreate()

        // initialize SavedStateRegistry
        savedStateRegistryController = SavedStateRegistryController.create(this)
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)

        // configuration of ComposeView
        contentView = ComposeView(this).apply {
            alpha = 0f
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setViewTreeLifecycleOwner(this@OverlayService)
            setContent {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    overlayContent.content()
                }
            }
            animate().alpha(1f).duration = 250
        }

        // add ComposeView to window
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.RIGHT or Gravity.TOP
        windowManager.addView(contentView, params)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // get the package name of the app that is currently running in the foreground
        runningAppPackageName = intent?.getStringExtra(EXTRA_RUNNING_APP_PACKAGE_NAME) ?: ""
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::runningAppPackageName.isInitialized) {
            // try to kill the app that was running in the foreground
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.killBackgroundProcesses(packageName)
        }
    }

    /**
     * Close the overlay, stop the service and go to the home screen
     * @param secondsUntilGoToHomeScreen Seconds that have to pass after the overlay closed until
     * the user is directed to the home screen. This is for cases where we want to allow the user
     * to finish a task before going to the home screen.
     */
    fun closeOverlay(secondsUntilGoToHomeScreen: Long = 0) {
        val intentToHome = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        if (secondsUntilGoToHomeScreen <= 0) {
            // go to home screen immediately
            startActivity(intentToHome)
        } else {
            // use the AlarmManager to go to home screen after a certain amount of time
            val pendingIntentToHome = PendingIntent.getActivity(
                this, 0, intentToHome, PendingIntent.FLAG_UPDATE_CURRENT
            )
            val triggerTime = SystemClock.elapsedRealtime() + secondsUntilGoToHomeScreen * 1000
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.set(AlarmManager.ELAPSED_REALTIME, triggerTime, pendingIntentToHome)
        }
        // remove overlay after animation and stop service
        contentView.animate().alpha(0f).setDuration(250L).withEndAction {
            contentView.visibility = View.GONE
            val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            windowManager.removeView(contentView)
            stopSelf()
        }.start()
    }
}

/**
 * Workaround:
 * This is just a wrapper for the content that should be displayed in the overlay.
 * If we passed the content directly to the service via the constructor, we occasionally got an
 * error, probably due to bugs in the Jetpack Compose library:
 *
 * > `androidx.compose.runtime.internal.ComposableLambdaImpl cannot be cast to kotlin.jvm.functions.Function0`
 *
 * @see <a href="https://stackoverflow.com/questions/68083934/jetpack-compose-androidx-compose-runtime-internal-composablelambdaimpl-cannot">StackOverflow</a>
 */
class OverlayContent(val content: @Composable () -> Unit)
