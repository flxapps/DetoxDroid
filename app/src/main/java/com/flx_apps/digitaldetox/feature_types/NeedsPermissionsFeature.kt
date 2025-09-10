package com.flx_apps.digitaldetox.feature_types

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavigationRoutes
import com.flx_apps.digitaldetox.ui.screens.permissions_required.GrantPermissionsCommand
import com.flx_apps.digitaldetox.util.NavigationUtil

/**
 * A feature that needs specific permissions in order to work.
 * @see [hasPermissions]
 * @see [requestPermissions]
 */
interface NeedsPermissionsFeature {
    /**
     * Will be called before the feature is activated. If this method returns false, the feature
     * cannot be activated and a Snackbar will be shown to request the permissions.
     */
    fun hasPermissions(context: Context): Boolean

    /**
     * This method will be called when the user clicks on the Snackbar to request the permissions.
     */
    fun requestPermissions(context: Context, navViewModel: NavViewModel)
}

/**
 * A feature that needs the [android.Manifest.permission.SYSTEM_ALERT_WINDOW] permission in order
 * to work.
 */
class NeedsDrawOverlayPermissionFeature : NeedsPermissionsFeature {
    /**
     * Checks whether the app has the [android.Manifest.permission.SYSTEM_ALERT_WINDOW] permission.
     */
    override fun hasPermissions(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * Call this method to request the [android.Manifest.permission.SYSTEM_ALERT_WINDOW]
     */
    override fun requestPermissions(context: Context, navViewModel: NavViewModel) {
        NavigationUtil.openOverlayPermissionsSettings(context)
    }
}

/**
 * A feature that needs the [android.Manifest.permission.WRITE_SECURE_SETTINGS] permission in order
 * to work.
 */
class NeedsWriteSecureSettingsPermission : NeedsPermissionsFeature {
    /**
     * Checks whether the app has the [android.Manifest.permission.WRITE_SECURE_SETTINGS] permission.
     */
    override fun hasPermissions(context: Context): Boolean {
        return context.checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Call this method to request the [android.Manifest.permission.WRITE_SECURE_SETTINGS]
     */
    override fun requestPermissions(context: Context, navViewModel: NavViewModel) {
        navViewModel.openRoute(
            NavigationRoutes.PermissionsRequired(
                GrantPermissionsCommand(
                    command = context.getString(R.string.rootCommand_grantWriteSecuritySettingsPermission),
                    supportsShizuku = true
                )
            )
        )
    }
}