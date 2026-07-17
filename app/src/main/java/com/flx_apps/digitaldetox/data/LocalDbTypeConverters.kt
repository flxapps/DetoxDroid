package com.flx_apps.digitaldetox.data

import androidx.room.TypeConverter
import java.time.LocalDate

class LocalDbTypeConverters {
    @TypeConverter
    fun fromDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }

    @TypeConverter
    fun fromIntArray(value: IntArray?): String? = value?.joinToString(",")

    @TypeConverter
    fun toIntArray(value: String?): IntArray? = value?.let { serialized ->
        if (serialized.isEmpty()) {
            IntArray(0)
        } else {
            serialized.split(",").map { it.toInt() }.toIntArray()
        }
    }
}
