package com.quem.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [QueueItemEntity::class, AttachmentEntity::class, HistoryEntryEntity::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class QueMDatabase : RoomDatabase() {
    abstract fun queueDao(): QueueDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE queue_items ADD COLUMN sharedDriveFileId TEXT")
                db.execSQL("ALTER TABLE queue_items ADD COLUMN sharedWith TEXT NOT NULL DEFAULT '[]'")
            }
        }
    }
}
