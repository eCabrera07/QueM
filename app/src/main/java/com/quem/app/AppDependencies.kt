package com.quem.app

import android.content.Context
import androidx.room.Room
import com.quem.core.time.SystemClock
import com.quem.data.local.QueMDatabase
import com.quem.data.repository.QueueRepository
import com.quem.data.repository.RoomQueueRepository
import com.quem.drive.DisconnectedDriveConnectionRepository
import com.quem.drive.DriveConnectionRepository
import java.util.UUID

class AppDependencies(context: Context) {
    private val database: QueMDatabase = Room.databaseBuilder(
        context.applicationContext,
        QueMDatabase::class.java,
        DATABASE_NAME
    ).build()

    val queueRepository: QueueRepository = RoomQueueRepository(
        dao = database.queueDao(),
        clock = SystemClock(),
        idProvider = { UUID.randomUUID().toString() }
    )

    val driveConnectionRepository: DriveConnectionRepository = DisconnectedDriveConnectionRepository()

    private companion object {
        const val DATABASE_NAME = "quem.db"
    }
}
