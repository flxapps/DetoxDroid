package com.flx_apps.digitaldetox

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent


class DetoxDroidDeviceAdminReceiver : DeviceAdminReceiver() {
    companion object {
        fun isGranted(context: Context): Boolean {
            return (context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager?)
                ?.isDeviceOwnerApp(context.packageName) == true
        }

        fun revokePermission(context: Context) {
            (context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager?)
                ?.clearDeviceOwnerApp(context.packageName)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        // This method is called when the BroadcastReceiver is receiving an Intent broadcast.
//        TODO("DetoxDroidDeviceAdminReceiver.onReceive() is not implemented")
    }
}