package com.flx_apps.digitaldetox.system_integration

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.flx_apps.digitaldetox.R
import timber.log.Timber

class DetoxDroidDeviceAdminReceiver : DeviceAdminReceiver() {
    companion object {
        fun isGranted(context: Context): Boolean {
            return (context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager?)?.isDeviceOwnerApp(
                context.packageName
            ) == true
        }

        fun hasDeviceAdminPermission(context: Context): Boolean {
            val dpm =
                context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager?
                    ?: return false
            val componentName = ComponentName(context, DetoxDroidDeviceAdminReceiver::class.java)
            return dpm.isAdminActive(componentName)
        }

        fun revokePermission(context: Context) {
            (context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager?)?.clearDeviceOwnerApp(
                context.packageName
            )
        }

        fun setUninstallBlocked(context: Context, blocked: Boolean) {
            if (!isGranted(context)) return
            kotlin.runCatching {
                val dpm =
                    context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager?
                        ?: return
                dpm.setUninstallBlocked(null, context.packageName, blocked)
                Timber.d("setUninstallBlocked($blocked)")
            }.onFailure { Timber.e(it, "setUninstallBlocked failed") }
        }

        fun createRequestDeviceAdminIntent(context: Context, explanation: String): Intent {
            return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(
                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    ComponentName(context, DetoxDroidDeviceAdminReceiver::class.java)
                )
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, explanation)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        // intentionally empty – handled via onDisableRequested / onDisabled
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val cpFeature = runCatching {
            com.flx_apps.digitaldetox.features.CommitmentPasswordFeature
        }.getOrNull()
        return if (cpFeature?.isActivated == true) {
            context.getString(R.string.deviceAdminRevoked_disableRequestedWarning)
        } else {
            super.onDisableRequested(context, intent) ?: ""
        }
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        val cpFeature = runCatching {
            com.flx_apps.digitaldetox.features.CommitmentPasswordFeature
        }.getOrNull()
        if (cpFeature?.isActivated == true) {
            com.flx_apps.digitaldetox.ui.screens.device_admin_revoked.DeviceAdminRevokedWarningActivity.requireDeviceAdminWarning(
                context
            )
            com.flx_apps.digitaldetox.ui.screens.device_admin_revoked.DeviceAdminRevokedWarningActivity.launch(
                context,
                com.flx_apps.digitaldetox.ui.screens.device_admin_revoked.DeviceAdminRevokedWarningActivity.WarningReason.DEVICE_ADMIN_REVOKED
            )
        }
    }
}
