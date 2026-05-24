package com.quem.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [QueueItemEntity::class, AttachmentEntity::class, HistoryEntryEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class QueMDatabase : RoomDatabase() {
    abstract fun queueDao(): QueueDao
}
