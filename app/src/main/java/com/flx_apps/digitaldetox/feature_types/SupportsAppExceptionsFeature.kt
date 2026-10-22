package com.flx_apps.digitaldetox.feature_types

import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.flx_apps.digitaldetox.data.DataStoreProperty
import com.flx_apps.digitaldetox.data.DataStorePropertyTransformer
import com.flx_apps.digitaldetox.websites.Websites

/**
 * The type of the app exception list, or of its websites.
 * [AppExceptionListType.NOT_LIST] means, that the entries in the list are NOT affected by the
 * feature.
 * [AppExceptionListType.ONLY_LIST] means, that ONLY the entries in the list are affected by the
 * feature.
 */
enum class AppExceptionListType {
    NOT_LIST, ONLY_LIST
}

interface SupportsAppExceptionsFeature {
    /**
     * Holds the package names of apps that are exceptions for this feature, and the websites (see
     * [Websites]).
     */
    var appExceptions: Set<String>

    /**
     * Whether the apps in [appExceptions] should be treated as a blocklist or a allowlist.
     * @see AppExceptionListType
     */
    var appExceptionListType: AppExceptionListType

    /**
     * Whether the websites in [appExceptions] should be treated as a blocklist or a allowlist, set
     * apart from the apps: "only these apps, but every website except these" is a list of its own.
     * A website that "only selected" leaves out counts as the browser it is shown in.
     */
    var websiteListType: AppExceptionListType

    /**
     * The [AppExceptionListType]s the feature offers for its apps. Some features possibly don't
     * need both, and set them in [Impl]. For example, the Disable Apps feature only supports
     * [AppExceptionListType.ONLY_LIST], as it does not make sense to have a list of apps that are
     * *not* blocked.
     *
     * This property affects the scope card of the app exceptions screen and what [appliesTo]
     * makes of a stored type.
     */
    val listTypes: List<AppExceptionListType>

    /**
     * The [websiteListType]s this feature offers. Every feature has both, Disable Apps included:
     * "every website except these" is how a browser is kept to the sites one needs.
     */
    val websiteListTypes: List<AppExceptionListType>
        get() = listOf(AppExceptionListType.NOT_LIST, AppExceptionListType.ONLY_LIST)

    /**
     * Whether this feature currently applies to [packageName], given the exception list and its
     * [appExceptionListType], or for a website entry its [websiteListType]. A stored type the
     * feature does not offer counts as the first one it does: Disable Apps only offers
     * [AppExceptionListType.ONLY_LIST], while the type stored for it can still be the default of
     * every feature.
     */
    fun appliesTo(packageName: String): Boolean {
        val isListed = appExceptions.contains(packageName)
        val (listType, offered) = if (Websites.isWebsite(packageName)) {
            websiteListType to websiteListTypes
        } else {
            appExceptionListType to listTypes
        }
        val effective = listType.takeIf { it in offered } ?: offered.first()
        return if (effective == AppExceptionListType.ONLY_LIST) isListed else !isListed
    }

    /**
     * The implementation of the [SupportsAppExceptionsFeature] interface.
     * @param featureId The id of the feature (usually the simple name of the feature class).
     * @param defaultExceptionListType The default [AppExceptionListType] for this feature.
     * @param listTypes The [AppExceptionListType]s the feature offers for its apps. They belong
     *   here rather than in an override on the feature: [appliesTo] runs on this object, and would
     *   not see the override.
     */
    class Impl(
        private val featureId: FeatureId,
        private val defaultExceptionListType: AppExceptionListType = AppExceptionListType.NOT_LIST,
        override val listTypes: List<AppExceptionListType> = listOf(
            AppExceptionListType.NOT_LIST, AppExceptionListType.ONLY_LIST
        ),
    ) : SupportsAppExceptionsFeature {
        override var appExceptions: Set<String> by DataStoreProperty(
            stringSetPreferencesKey("${featureId}_exceptions"), setOf()
        )
        override var appExceptionListType: AppExceptionListType by DataStoreProperty(
            stringPreferencesKey("${featureId}_exceptionListType"),
            defaultExceptionListType,
            dataTransformer = DataStorePropertyTransformer.EnumStorePropertyTransformer(
                AppExceptionListType::class.java
            )
        )

        // only the listed websites by default, whatever the apps do: with none listed, a browser
        // is an app like any other
        override var websiteListType: AppExceptionListType by DataStoreProperty(
            stringPreferencesKey("${featureId}_websiteListType"),
            AppExceptionListType.ONLY_LIST,
            dataTransformer = DataStorePropertyTransformer.EnumStorePropertyTransformer(
                AppExceptionListType::class.java
            )
        )
    }
}