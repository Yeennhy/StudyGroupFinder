package com.studyfinder.app.data.local.converter

import androidx.room.TypeConverter

/**
 * Room stores neither lists nor Firestore Timestamps natively.
 *
 */
class Converters {

    @TypeConverter
    fun fromStringList(value: List<String>?): String =
        value.orEmpty().joinToString(SEPARATOR.toString())

    @TypeConverter
    fun toStringList(value: String?): List<String> =
        if (value.isNullOrEmpty()) emptyList() else value.split(SEPARATOR)

    private companion object {
        /**
         * ASCII unit separator (U+001F). A Firebase uid or a Storage download
         * URL can never contain it, so round-tripping through split() is safe
         * in a way that a comma would not be.
         */
        const val SEPARATOR = ''
    }
}
