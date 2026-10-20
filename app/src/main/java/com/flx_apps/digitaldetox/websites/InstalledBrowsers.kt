package com.flx_apps.digitaldetox.websites

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.flx_apps.digitaldetox.util.appLabel

/**
 * The browsers on this phone, split by whether DetoxDroid can read their address bar, by name.
 */
data class InstalledBrowsers(val readable: List<String>, val unreadable: List<String>) {
    companion object {
        /**
         * Every app that opens web links counts as a browser, minus DetoxDroid itself. Blocking: it
         * asks the package manager, and looks into the browsers [BrowserAddressBars] does not know.
         */
        fun find(context: Context): InstalledBrowsers {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            val browsers = runCatching {
                context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                    .map { it.activityInfo.packageName }
            }.getOrDefault(emptyList()).toSet() - context.packageName
            val (readable, unreadable) =
                browsers.partition { BrowserAddressBars.idsFor(context, it) != null }
            return InstalledBrowsers(
                readable = readable.map { context.appLabel(it) }.sorted(),
                unreadable = unreadable.map { context.appLabel(it) }.sorted(),
            )
        }
    }
}
