package com.flx_apps.digitaldetox.feature_types

import AppExceptionsListSettingsSheet
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.flx_apps.digitaldetox.data.DataStoreProperty
import com.flx_apps.digitaldetox.data.DataStorePropertyTransformer

/**
 * The type of the app exception list.
 * [AppExceptionListType.NOT_LIST] means, that the apps in the list are NOT affected by the feature.
 * [AppExceptionListType.ONLY_LIST] means, that ONLY the apps in the list are ONLY affected by the
 * feature.
 */
enum class AppExceptionListType {
    NOT_LIST, ONLY_LIST
}

interface SupportsAppExceptionsFeature {
    /**
     * Holds the package names of apps that are exceptions for this feature.
     */
    var appExceptions: Set<String>

    /**
     * Whether the [appExceptions] should be treated as a blocklist or a allowlist.
     * @see AppExceptionListType
     */
    var appExceptionListType: AppExceptionListType

    /**
     * A list of all possible [AppExceptionListType]s. Some features possibly don't need all kinds
     * of [AppExceptionListType]s, so they can override this property. For example, the
     * [DisableAppsFeature] only supports [AppExceptionListType.ONLY_LIST], as it does not make
     * sense to have a list of apps that are *not* blocked.
     *
     * This property affects the UI of the app exceptions screen, cf.
     * [AppExceptionsListSettingsSheet].
     */
    val listTypes: List<AppExceptionListType>
        get() = listOf(
            AppExceptionListType.NOT_LIST, AppExceptionListType.ONLY_LIST
        )

    /**
     * The implementation of the [SupportsAppExceptionsFeature] interface.
     * @param featureId The id of the feature (usually the simple name of the feature class).
     * @param defaultExceptionListType The default [AppExceptionListType] for this feature.
     */
    class Impl(
        private val featureId: FeatureId,
        private val defaultExceptionListType: AppExceptionListType = AppExceptionListType.NOT_LIST
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
    }
}