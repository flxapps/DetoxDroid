package com.flx_apps.digitaldetox.data

import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.flx_apps.digitaldetox.BuildConfig
import com.flx_apps.digitaldetox.features.CommitmentPasswordFeatureId
import com.flx_apps.digitaldetox.features.EmergencyBrakeFeature
import com.flx_apps.digitaldetox.features.FeaturesProvider
import com.flx_apps.digitaldetox.features.GrayscaleAppsFeatureId
import com.flx_apps.digitaldetox.widgets.WidgetSettingsStore
import com.flx_apps.digitaldetox.websites.Websites
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.time.LocalDate
import kotlin.system.exitProcess

/**
 * Writes the whole configuration to a file the user keeps, and reads it back.
 *
 * Everything a user sets up lives in one [DataStore] of typed preferences, so the file is that map
 * with a type tag per entry rather than a hand-maintained schema: a setting added later is carried
 * without anybody remembering to add it here. Usage history is not in it. That lives in Room, it is
 * measurement rather than configuration, and it is the part that grows without bound.
 *
 * Not everything in the map is configuration, see [isBackedUp]. The commitment password's state
 * stays out of both directions, or a backup would be a way around it: export while unlocked, then
 * import the old state back over a lock that is in force. A running brake stays out for the same
 * reason, the premium flag because it is bought, and the rest because it describes this phone
 * rather than a choice: what is suspended through Shizuku right now, the clock anchors, the system
 * color settings the grayscale feature owes the user back, today's used-up budgets, the apps and
 * the look picked for a widget id that exists on no other phone. Carried over, each of them is
 * wrong on arrival.
 */
object ConfigBackup {
    const val MIME_TYPE = "application/json"

    /** Bumped when the file layout changes in a way a reader has to know about. */
    private const val FORMAT_VERSION = 1

    private const val KEY_VERSION = "formatVersion"
    private const val KEY_CREATED_AT = "createdAt"
    private const val KEY_APP_VERSION_NAME = "appVersionName"
    private const val KEY_APP_VERSION_CODE = "appVersionCode"
    private const val KEY_VALUES = "values"
    private const val KEY_TYPE = "type"
    private const val KEY_VALUE = "value"

    private const val TYPE_BOOLEAN = "boolean"
    private const val TYPE_INT = "int"
    private const val TYPE_LONG = "long"
    private const val TYPE_FLOAT = "float"
    private const val TYPE_DOUBLE = "double"
    private const val TYPE_STRING = "string"
    private const val TYPE_STRING_SET = "stringSet"

    /** Exact preference keys that are neither written nor read back. */
    private val EXCLUDED_KEYS = setOf(
        "premium_unlocked",
        "onboarding_pendingFeatureActivations",
        // agreed to by whoever uses this phone, not by whoever made the file
        "accessibilityService_disclosureAccepted",
    )

    /** Key prefixes of state that belongs to this phone, see the class docs. */
    private val EXCLUDED_PREFIXES = listOf(
        "${CommitmentPasswordFeatureId}_",
        "AppSuspender_",
        "trustedClock_",
        "minimal_launcher_widget_",
        WidgetSettingsStore.KEY_PREFIX,
        "premium_nudge_",
        "review_request_",
        "${GrayscaleAppsFeatureId}_daltonizerApplied",
        "${GrayscaleAppsFeatureId}_defaultDaltonizer",
        "${GrayscaleAppsFeatureId}_extraDimApplied",
    )

    /** Key suffixes of state that belongs to today, see the class docs. */
    private val EXCLUDED_SUFFIXES = listOf("_usedUpScreenTime")

    fun suggestedFileName(): String = "detoxdroid-settings-${LocalDate.now()}.json"

    /**
     * Whether a preference belongs in a backup at all.
     * @see ConfigBackup
     */
    fun isBackedUp(keyName: String) =
        keyName !in EXCLUDED_KEYS && keyName !in EmergencyBrakeFeature.runningStateKeys &&
                EXCLUDED_PREFIXES.none { keyName.startsWith(it) } &&
                EXCLUDED_SUFFIXES.none { keyName.endsWith(it) }

    /** What a settings file says about itself, and what it holds. */
    class BackupFile(
        val createdAt: LocalDate?,
        val appVersionName: String?,
        val appVersionCode: Int?,
        val values: Map<String, Any>,
    )

    /** One feature's share of a file, for the user to look at before restoring. */
    data class FeatureSummary(
        val featureId: String,
        @StringRes val titleRes: Int?,
        val isActivated: Boolean?,
        val appCount: Int?,
        val websiteCount: Int = 0,
    )

    /**
     * What restoring a file would do on this phone, see [preview].
     * @param values the entries that would be written, the file's values minus what [isBackedUp]
     * keeps out and minus what [applicable] drops
     * @param changes how many of them differ from what is set right now
     * @param otherSettings entries that belong to no feature this build knows, or to none at all
     */
    class Preview(
        val file: BackupFile,
        val values: Map<String, Any>,
        val features: List<FeatureSummary>,
        val otherSettings: Int,
        val changes: Int,
    ) {
        val isFromNewerVersion: Boolean
            get() = (file.appVersionCode ?: 0) > BuildConfig.VERSION_CODE
    }

    /** The current configuration as the text that goes into the file. */
    suspend fun export(): String = serialize(currentValues())

    private suspend fun currentValues(): Map<String, Any> = DataStore.data.first().asMap()
        .filterKeys { isBackedUp(it.name) }
        .map { (key, value) -> key.name to value }
        .toMap()

    /**
     * Reads [json] and works out what restoring it would mean here, without writing anything.
     * Throws on a file that is not a settings file, like [parse].
     */
    suspend fun preview(json: String): Preview {
        val file = read(json)
        val current = currentValues()
        val values = applicable(file.values.filterKeys { isBackedUp(it) }, current)
        val knownFeatureIds = FeaturesProvider.featureList.map { it.id }.toSet()
        val byFeature = values.keys.groupBy { featureIdOf(it) }
        val features = FeaturesProvider.featureList.filter { it.id in byFeature }.map { feature ->
            val entries = (values["${feature.id}_exceptions"] as? Set<*>)?.filterIsInstance<String>()
            FeatureSummary(
                featureId = feature.id,
                titleRes = feature.texts.title,
                isActivated = values["${feature.id}_isActivated"] as? Boolean,
                appCount = entries?.let { Websites.appsOf(it).size },
                websiteCount = entries?.let { Websites.websitesOf(it).size } ?: 0
            )
        }
        return Preview(
            file = file,
            values = values,
            features = features,
            otherSettings = byFeature.filterKeys { it !in knownFeatureIds }.values.sumOf { it.size },
            changes = values.count { (name, value) -> current[name] != value }
        )
    }

    /**
     * Writes [values] back (the file's values as [preview] filtered them), leaving preferences the
     * file does not name alone.
     * @return how many values were written
     */
    suspend fun import(values: Map<String, Any>): Int {
        if (values.isEmpty()) return 0
        var written = 0
        DataStore.editLocked { preferences ->
            for ((name, value) in values) {
                val key = keyFor(name, value) ?: continue
                @Suppress("UNCHECKED_CAST")
                preferences[key as Preferences.Key<Any>] = value
                written++
            }
        }
        return written
    }

    /**
     * Keeps the entries of a file that can be written over [current] without breaking a reader.
     * Preferences are typed by key, and every feature reads its keys as one fixed type: a value
     * stored under a name the app reads as an Int has to be an Int, or the read throws at the next
     * start. A file written by a version that stored the same name differently is the case; such
     * an entry is dropped rather than failing the whole restore. Names not set yet are taken as
     * they are.
     */
    internal fun applicable(
        fileValues: Map<String, Any>, current: Map<String, Any>
    ): Map<String, Any> = fileValues.filter { (name, value) ->
        val existing = current[name] ?: return@filter true
        when (value) {
            is Set<*> -> existing is Set<*>
            else -> existing::class == value::class
        }
    }

    /** The feature a preference belongs to, by the key convention `${featureId}_name`. */
    private fun featureIdOf(keyName: String) = keyName.substringBefore('_')

    /**
     * Restarts DetoxDroid so the imported values are the ones it runs on. Every feature reads its
     * preferences once and keeps them in memory (see [DataStoreProperty]), so a running process
     * would go on using what was configured before the import and write it back over the file's
     * values the next time anything changed.
     */
    fun restartApp(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        runCatching { launchIntent?.let { context.startActivity(it) } }
            .onFailure { Timber.w(it, "Could not relaunch after the import") }
        exitProcess(0)
    }

    internal fun serialize(
        values: Map<String, Any>,
        appVersionName: String = BuildConfig.VERSION_NAME,
        appVersionCode: Int = BuildConfig.VERSION_CODE,
    ): String {
        val entries = JSONObject()
        for ((name, value) in values) {
            val entry = JSONObject()
            when (value) {
                is Boolean -> entry.put(KEY_TYPE, TYPE_BOOLEAN).put(KEY_VALUE, value)
                is Int -> entry.put(KEY_TYPE, TYPE_INT).put(KEY_VALUE, value)
                is Long -> entry.put(KEY_TYPE, TYPE_LONG).put(KEY_VALUE, value)
                is Float -> entry.put(KEY_TYPE, TYPE_FLOAT).put(KEY_VALUE, value.toDouble())
                is Double -> entry.put(KEY_TYPE, TYPE_DOUBLE).put(KEY_VALUE, value)
                is String -> entry.put(KEY_TYPE, TYPE_STRING).put(KEY_VALUE, value)
                is Set<*> -> entry.put(KEY_TYPE, TYPE_STRING_SET)
                    .put(KEY_VALUE, JSONArray(value.map { it.toString() }))

                else -> continue
            }
            entries.put(name, entry)
        }
        return JSONObject()
            .put(KEY_VERSION, FORMAT_VERSION)
            .put(KEY_CREATED_AT, LocalDate.now().toString())
            .put(KEY_APP_VERSION_NAME, appVersionName)
            .put(KEY_APP_VERSION_CODE, appVersionCode)
            .put(KEY_VALUES, entries)
            .toString(2)
    }

    /**
     * Reads a backup file. Throws on anything that is not one; an entry whose type is not known
     * (a file from a later version) is skipped rather than failing the whole import, and so is a
     * header field that is missing or malformed.
     */
    internal fun read(json: String): BackupFile {
        val root = runCatching { JSONObject(json) }
            .getOrElse { throw IllegalArgumentException("not a DetoxDroid settings file", it) }
        require(root.optJSONObject(KEY_VALUES) != null) { "not a DetoxDroid settings file" }
        val entries = root.getJSONObject(KEY_VALUES)
        val values = mutableMapOf<String, Any>()
        for (name in entries.keys()) {
            val entry = entries.optJSONObject(name) ?: continue
            val value: Any = when (entry.optString(KEY_TYPE)) {
                TYPE_BOOLEAN -> entry.getBoolean(KEY_VALUE)
                TYPE_INT -> entry.getInt(KEY_VALUE)
                TYPE_LONG -> entry.getLong(KEY_VALUE)
                TYPE_FLOAT -> entry.getDouble(KEY_VALUE).toFloat()
                TYPE_DOUBLE -> entry.getDouble(KEY_VALUE)
                TYPE_STRING -> entry.getString(KEY_VALUE)
                TYPE_STRING_SET -> entry.getJSONArray(KEY_VALUE).let { array ->
                    (0 until array.length()).mapTo(mutableSetOf()) { array.getString(it) }
                }

                else -> continue
            }
            values[name] = value
        }
        return BackupFile(
            createdAt = root.optString(KEY_CREATED_AT).takeIf { it.isNotEmpty() }
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            appVersionName = root.optString(KEY_APP_VERSION_NAME).takeIf { it.isNotEmpty() },
            appVersionCode = root.optInt(KEY_APP_VERSION_CODE).takeIf { root.has(KEY_APP_VERSION_CODE) },
            values = values
        )
    }

    private fun keyFor(name: String, value: Any): Preferences.Key<*>? = when (value) {
        is Boolean -> booleanPreferencesKey(name)
        is Int -> intPreferencesKey(name)
        is Long -> longPreferencesKey(name)
        is Float -> floatPreferencesKey(name)
        is Double -> doublePreferencesKey(name)
        is String -> stringPreferencesKey(name)
        is Set<*> -> stringSetPreferencesKey(name)
        else -> null
    }
}
