package com.flx_apps.digitaldetox.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * An elaborate delegate for a property that is persisted in the data store. The property has a
 * type T and a representation type for the data store (DataStoreType). The property is persisted
 * under the given key in the data store. The value is loaded from the data store on initialization
 * and is persisted to the data store whenever it is changed.
 * @param defaultValue The initial (default) value of the property (if no value is found in the
 * data store).
 * @param key The key under which the value is persisted in the data store.
 * @param dataStore The data store to use. Defaults to the app's data store.
 * @param dataTransformer A mapper to convert the value to the data store type and vice versa. If no
 * mapper is given, the value is assumed to be of the data store type.
 */
@Suppress("UNCHECKED_CAST")
class DataStoreProperty<T, DataStoreType>(
    private val key: Preferences.Key<DataStoreType>,
    private var defaultValue: T,
    private val dataTransformer: DataStorePropertyTransformer<T, DataStoreType>? = null,
    private val dataStore: DataStore<Preferences> = DataStore,
) {
    operator fun provideDelegate(thisRef: Any?, prop: KProperty<*>): ReadWriteProperty<Any?, T> {
        return object : ReadWriteProperty<Any?, T> {
            /**
             * Returns the value of the property. On initialization, the value is loaded from the
             * data store (see [DataStoreProperty] init block).
             */
            override fun getValue(thisRef: Any?, property: KProperty<*>): T {
                return defaultValue
            }

            /**
             * Persist the value to the data store whenever it is changed.
             * For simplicity reasons, we just assume that the operation was successful.
             */
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
                defaultValue = value
                dataStore.persistValue(
                    key, dataTransformer?.transformTo(value) ?: value as DataStoreType
                )
            }
        }
    }

    /**
     * Load the value from the data store on initialization.
     */
    init {
        dataStore.loadValue<DataStoreType>(key)?.let {
            defaultValue = dataTransformer?.transformFrom(it) ?: it as T
        }
    }
}

/**
 * A data transformer that is used to convert the value of a property to the data store type and
 * vice versa.
 * @param T The type of the property.
 * @param DataStoreType The type that is used to persist the property in the data store.
 */
interface DataStorePropertyTransformer<T, DataStoreType> {
    /**
     * Transforms the value to the data store type.
     */
    fun transformTo(value: T): DataStoreType

    /**
     * Transforms the value from the data store type.
     */
    fun transformFrom(value: DataStoreType): T

    /**
     * A data transformer for enums. It just uses the enum name as the data store type.
     */
    class EnumStorePropertyTransformer<T : Enum<T>>(private val enumClass: Class<T>) :
        DataStorePropertyTransformer<T, String> {
        override fun transformTo(value: T): String {
            return value.name
        }

        override fun transformFrom(value: String): T {
            return java.lang.Enum.valueOf(enumClass, value)
        }
    }

    /**
     * A data transformer for sets. It uses the given functions to convert the items of the set to
     * and from strings. (String sets are supported by the data store.)
     */
    class SetStorePropertyTransformer<T>(
        private val itemToString: (T) -> String, private val itemFromString: (String) -> T?
    ) : DataStorePropertyTransformer<Set<T>, Set<String>> {
        override fun transformTo(value: Set<T>): Set<String> {
            return value.map { itemToString(it) }.toSet()
        }

        override fun transformFrom(value: Set<String>): Set<T> {
            return value.mapNotNull { itemFromString(it) }.toSet()
        }
    }
}