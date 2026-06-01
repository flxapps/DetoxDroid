package com.flx_apps.digitaldetox.widgets.minimal_launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.flx_apps.digitaldetox.R

class MinimalLauncherWidgetClickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_LAUNCH_APP) {
            return
        }
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        if (packageName.isBlank()) {
            Toast.makeText(
                context,
                context.getString(R.string.widget_minimalLauncher_app_unavailable),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            Toast.makeText(
                context,
                context.getString(R.string.widget_minimalLauncher_app_unavailable),
                Toast.LENGTH_SHORT
            ).show()
            MinimalLauncherWidgetProvider.updateAllWidgets(context)
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
    }

    companion object {
        const val ACTION_LAUNCH_APP = "com.flx_apps.digitaldetox.widgets.minimal_launcher.ACTION_LAUNCH_APP"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
    }
}
