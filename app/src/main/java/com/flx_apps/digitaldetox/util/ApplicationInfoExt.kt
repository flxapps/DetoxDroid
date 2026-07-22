package com.flx_apps.digitaldetox.util

import android.content.Context
import android.content.pm.ApplicationInfo
import com.flx_apps.digitaldetox.R

/**
 * Extension function for [ApplicationInfo] to get the app category title. It first tries to get the
 * title from the package name (for known apps, see [KnownAppCategory]). If that fails, it obtains
 * the title from the category, if available by the system (see [ApplicationInfo.category]).
 * @param context The context to get the string from.
 */
fun ApplicationInfo.getAppCategoryTitle(
    context: Context
): String {
    // first obtain the string resource id from the package name for known apps
    var stringResourceId: Int = when (knownCategoryOf(this.packageName)) {
        KnownAppCategory.SOCIAL -> R.string.appCategories_social
        KnownAppCategory.MUSIC -> R.string.appCategories_music
        KnownAppCategory.VIDEO -> R.string.appCategories_video
        KnownAppCategory.SHOPPING -> R.string.appCategories_shopping
        KnownAppCategory.DATING -> R.string.appCategories_dating
        KnownAppCategory.TRAVEL -> R.string.appCategories_travel
        KnownAppCategory.WORKOUT -> R.string.appCategories_workout
        null -> R.string.appCategories_other
    }
    // otherwise, try to obtain the string resource id from the category
    if (stringResourceId == R.string.appCategories_other) {
        stringResourceId = when (this.category) {
            ApplicationInfo.CATEGORY_AUDIO -> R.string.appCategories_audio
            ApplicationInfo.CATEGORY_GAME -> R.string.appCategories_games
            ApplicationInfo.CATEGORY_IMAGE -> R.string.appCategories_image
            ApplicationInfo.CATEGORY_MAPS -> R.string.appCategories_maps
            ApplicationInfo.CATEGORY_NEWS -> R.string.appCategories_news
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> R.string.appCategories_productivity
            ApplicationInfo.CATEGORY_SOCIAL -> R.string.appCategories_social
            ApplicationInfo.CATEGORY_UNDEFINED -> R.string.appCategories_other
            ApplicationInfo.CATEGORY_VIDEO -> R.string.appCategories_video
            else -> R.string.appCategories_other
        }
    }
    return context.getString(stringResourceId)
}

/**
 * Returns true if the app is a system app, false otherwise.
 */
fun ApplicationInfo.isSystemApp(): Boolean {
    return this.flags and ApplicationInfo.FLAG_SYSTEM != 0
}
