package com.quem.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.quem.app.QueMApplication
import com.quem.core.time.SystemClock
import com.quem.drive.DriveAccountPreferences
import com.quem.drive.GoogleDriveAuthorizationCoordinator
import com.quem.drive.GoogleDriveGateway
import java.io.IOException

class SyncWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) {

    override suspend fun doWork(): Result {
        return try {
            val email = DriveAccountPreferences(applicationContext).load()
                ?: return Result.success() // not signed in — skip silently

            val credential = GoogleAccountCredential
                .usingOAuth2(applicationContext, listOf(GoogleDriveAuthorizationCoordinator.DRIVE_FILE_SCOPE))
                .setSelectedAccountName(email)

            val drive = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            )
                .setApplicationName("QueM")
                .build()

            val deps = (applicationContext as QueMApplication).dependencies

            SyncCoordinator(
                dao         = deps.dao,
                syncManager = SyncManager(GoogleDriveGateway(drive)),
                clock       = SystemClock()
            ).sync()

            Result.success()
        } catch (e: IOException) {
            Log.w(TAG, "Sync failed — will retry", e)
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed permanently", e)
            Result.failure()
        }
    }

    private companion object {
        const val TAG = "SyncWorker"
    }
}
