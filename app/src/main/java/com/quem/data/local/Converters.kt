package com.quem.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate

class Converters {
    @TypeConverter fun instantToString(value: Instant?): String? = value?.toString()
    @TypeConverter fun stringToInstant(value: String?): Instant? = value?.let(Instant::parse)
    @TypeConverter fun localDateToString(value: LocalDate?): String? = value?.toString()
    @TypeConverter fun stringToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
    @TypeConverter fun tagsToString(value: List<String>): String = Json.encodeToString(value)
    @TypeConverter
    fun stringToTags(value: String): List<String> = if (value.isBlank()) {
        emptyList()
    } else {
        runCatching { Json.decodeFromString<List<String>>(value) }
            .getOrElse { value.split("\u001F") }
    }
}
