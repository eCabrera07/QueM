package com.quem.data.local

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

class Converters {
    @TypeConverter fun instantToString(value: Instant?): String? = value?.toString()
    @TypeConverter fun stringToInstant(value: String?): Instant? = value?.let(Instant::parse)
    @TypeConverter fun localDateToString(value: LocalDate?): String? = value?.toString()
    @TypeConverter fun stringToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
    @TypeConverter fun tagsToString(value: List<String>): String = value.joinToString(separator = "\u001F")
    @TypeConverter fun stringToTags(value: String): List<String> = if (value.isBlank()) emptyList() else value.split("\u001F")
}
