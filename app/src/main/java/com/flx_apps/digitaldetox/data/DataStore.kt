package com.flx_apps.digitaldetox.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.flx_apps.digitaldetox.DetoxDroidApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

private val dataStorePersistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
 * scope because feature state also keeps an in-memory value for immediate reads.
 *
 * The data store is only used to persist the preferences across app restarts.
 */
fun <T> DataStore<Preferences>.persistValue(key: Preferences.Key<T>, value: T) {
    dataStorePersistScope.launch {
        try {
            dataStorePersistMutex.withLock {
                this@persistValue.edit { preferences ->
                    preferences[key] = value
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to persist DataStore preference %s", key.name)
        }
    }
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
