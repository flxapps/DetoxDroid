package com.flx_apps.digitaldetox.data

import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.flx_apps.digitaldetox.features.CommitmentPasswordFeatureId
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
 * Two things are deliberately left out of both directions, see [isBackedUp]. A backup that carried
 * the commitment password's state would be a way around it: export while unlocked, then import the
 * old state back over a lock that is in force. The premium flag is left out for the same shape of
 * reason.
 */
object ConfigBackup {
    const val MIME_TYPE = "application/json"

    /** Bumped when the file layout changes in a way a reader has to know about. */
    private const val FORMAT_VERSION = 1

    private const val KEY_VERSION = "formatVersion"
    private const val KEY_CREATED_AT = "createdAt"
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
    private val EXCLUDED_KEYS = setOf("premium_unlocked")

    fun suggestedFileName(): String = "detoxdroid-settings-${LocalDate.now()}.json"

    /**
     * Whether a preference belongs in a backup at all.
     * @see ConfigBackup
     */
    fun isBackedUp(keyName: String) =
        keyName !in EXCLUDED_KEYS && !keyName.startsWith("${CommitmentPasswordFeatureId}_")

    /** The current configuration as the text that goes into the file. */
    suspend fun export(): String {
        val values = DataStore.data.first().asMap()
            .filterKeys { isBackedUp(it.name) }
            .map { (key, value) -> key.name to value }
            .toMap()
        return serialize(values)
    }

    /**
     * Writes the values in [json] back, leaving preferences the file does not name alone.
     * @return how many values were restored
     */
    suspend fun import(json: String): Int {
        val values = parse(json).filterKeys { isBackedUp(it) }
        if (values.isEmpty()) return 0
        DataStore.edit { preferences ->
            for ((name, value) in values) {
                val key = keyFor(name, value) ?: continue
                @Suppress("UNCHECKED_CAST")
                preferences[key as Preferences.Key<Any>] = value
            }
        }
        return values.size
    }

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

    internal fun serialize(values: Map<String, Any>): String {
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
            .put(KEY_VALUES, entries)
            .toString(2)
    }

    /**
     * Reads a backup file. Throws on anything that is not one; an entry whose type is not known
     * (a file from a later version) is skipped rather than failing the whole import.
     */
    internal fun parse(json: String): Map<String, Any> {
        val root = JSONObject(json)
        require(root.has(KEY_VALUES)) { "not a DetoxDroid settings file" }
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
        return values
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
