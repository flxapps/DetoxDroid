package com.flx_apps.digitaldetox.widgets.minimal_launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import com.flx_apps.digitaldetox.util.getAppCategoryTitle
import com.flx_apps.digitaldetox.util.isSystemApp
import java.util.Locale

data class LaunchableAppInfo(
    val packageName: String,
    val appName: String,
    val appCategory: String,
    val isSystemApp: Boolean,
)

object MinimalLauncherWidgetAppRepository {
    fun getLaunchableApps(context: Context): List<LaunchableAppInfo> {
        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return queryLauncherActivities(packageManager, launcherIntent)
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo?.applicationInfo?.packageName ?: return@mapNotNull null
                val applicationInfo = getApplicationInfo(packageManager, packageName) ?: return@mapNotNull null
                LaunchableAppInfo(
                    packageName = packageName,
                    appName = packageManager.getApplicationLabel(applicationInfo).toString(),
                    appCategory = applicationInfo.getAppCategoryTitle(context),
                    isSystemApp = applicationInfo.isSystemApp()
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.appName.lowercase(Locale.getDefault()) }
    }

    fun getLaunchableAppsByPackages(
        context: Context,
        selectedPackages: List<String>
    ): List<LaunchableAppInfo> {
        val packageManager = context.packageManager
        return selectedPackages
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .mapNotNull { selectedPackage ->
                val launchIntent = packageManager.getLaunchIntentForPackage(selectedPackage) ?: return@mapNotNull null
                val packageName = launchIntent.component?.packageName ?: selectedPackage
                val applicationInfo = getApplicationInfo(packageManager, packageName) ?: return@mapNotNull null
                LaunchableAppInfo(
                    packageName = packageName,
                    appName = packageManager.getApplicationLabel(applicationInfo).toString(),
                    appCategory = applicationInfo.getAppCategoryTitle(context),
                    isSystemApp = applicationInfo.isSystemApp()
                )
            }
            .toList()
    }

    @Suppress("DEPRECATION")
    private fun queryLauncherActivities(
        packageManager: PackageManager,
        intent: Intent
    ): List<ResolveInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            packageManager.queryIntentActivities(intent, 0)
        }
    }

    @Suppress("DEPRECATION")
    private fun getApplicationInfo(
        packageManager: PackageManager,
        packageName: String
    ): ApplicationInfo? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0)
                )
            } else {
                packageManager.getApplicationInfo(packageName, 0)
            }
        }.getOrNull()
    }
}
