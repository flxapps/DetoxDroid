package com.flx_apps.digitaldetox.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.flx_apps.digitaldetox.DetoxDroidApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * The data store instance that is used to persist the preferences across app restarts.
 * TODO This is always initialized with the app context. Using dependency injection would be better,
 *   but this fairly pragmatic solution works for now.
 * @see persistValue
 * @see loadValue
 */
val DataStore: DataStore<Preferences> by lazy { DetoxDroidApplication.appContext.dataStore }

/**
 * Persists one write at a time, in the order they were made. With writes free to start in any
 * order, two quick writes of one key could reach the disk older-last, and after the next restart
 * the file would no longer match what the app had in memory.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private val dataStorePersistScope =
    CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
private val dataStorePersistMutex = Mutex()

/**
 * The data store that is used to persist the preferences across app restarts. It is initialized
 * with the name of the preferences file. The file is stored in the app's private data directory.
 * @see persistValue
 * @see loadValue
 */
val Context.dataStore by preferencesDataStore("detox_droid_preferences")

/**
 * Helper function to persist a value to the data store. Writes run on a serialized background
 * scope (see [dataStorePersistScope]) because feature state also keeps an in-memory value for
 * immediate reads.
 *
 * The data store is only used to persist the preferences across app restarts.
 */
fun <T> DataStore<Preferences>.persistValue(key: Preferences.Key<T>, value: T) {
    dataStorePersistScope.launch {
        try {
            editLocked { preferences -> preferences[key] = value }
        } catch (e: Exception) {
            Timber.e(e, "Failed to persist DataStore preference %s", key.name)
        }
    }
}

/**
 * Edits the data store behind the same lock as [persistValue], so a write that touches many keys
 * at once (restoring a settings file) cannot interleave with a property write that lands at the
 * same moment.
 */
suspend fun DataStore<Preferences>.editLocked(transform: (MutablePreferences) -> Unit) {
    dataStorePersistMutex.withLock { edit(transform) }
}

/**
 * Convenience function to load a value from the data store. It will just return the first value
 * that is emitted by the data store flow.
 *
 * If no value is found, null is returned.
 *
 * This function is blocking for simplicity reasons. It should only be used in the initialization
 * of a property. (See [DataStoreProperty] for an example.)
 */
fun <T> DataStore<Preferences>.loadValue(key: Preferences.Key<*>): T? {
    return runBlocking {
        val data = data.first()
        data[key] as T?
    }
}

/**
 * Drops every preference whose key starts with [prefix], behind the same lock as the other writes.
 * What a deleted group stored would otherwise sit in the file for good, and come back in every
 * settings file written after it.
 */
fun DataStore<Preferences>.removeKeysWithPrefix(prefix: String) {
    dataStorePersistScope.launch {
        try {
            editLocked { preferences ->
                preferences.asMap().keys
                    .filter { it.name.startsWith(prefix) }
                    .forEach { preferences.remove(it) }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to remove DataStore preferences with prefix %s", prefix)
        }
    }
}
