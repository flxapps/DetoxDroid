package com.flx_apps.digitaldetox.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.flx_apps.digitaldetox.DetoxDroidApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * The data store instance that is used to persist the preferences across app restarts.
 * TODO This is always initialized with the app context. Using dependency injection would be better,
 *   but this fairly pragmatic solution works for now.
 * @see persistValue
 * @see loadValues
 */
val DataStore: DataStore<Preferences> by lazy { DetoxDroidApplication.appContext.dataStore }

/**
 * The data store that is used to persist the preferences across app restarts. It is initialized
 * with the name of the preferences file. The file is stored in the app's private data directory.
 * @see persistValue
 * @see loadValues
 */
val Context.dataStore by preferencesDataStore("detox_droid_preferences")

/**
 * Helper function to persist a value to the data store. It will just launch a coroutine in the
 * background and we just assume that the operation was successful, as we also store the state
 * in a local variable (which will be used over the data store value).
 *
 * The data store is only used to persist the preferences across app restarts.
 */
fun <T> DataStore<Preferences>.persistValue(key: Preferences.Key<T>, value: T) {
    runBlocking {
        launch {
            this@persistValue.edit { preferences ->
                preferences[key] = value
            }
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


