package com.quem.drive

import android.net.Uri
import androidx.activity.result.ActivityResultLauncher

class SafDrivePickerCoordinator(
    private val fileLauncher: ActivityResultLauncher<Array<String>>,
    private val folderLauncher: ActivityResultLauncher<Uri?>,
    private val drivePickerRepository: DrivePickerRepository
) : DrivePickerCoordinator {
    override fun pickFile(onResult: (DriveSelection?) -> Unit) {
        drivePickerRepository.clearPendingFileCallback()
        if (drivePickerRepository.setPendingFileCallback(onResult)) {
            try {
                fileLauncher.launch(arrayOf("*/*"))
            } catch (e: Exception) {
                drivePickerRepository.handleFileResult(null)
            }
        }
    }

    override fun pickFolder(onResult: (DriveSelection?) -> Unit) {
        drivePickerRepository.clearPendingFolderCallback()
        if (drivePickerRepository.setPendingFolderCallback(onResult)) {
            try {
                folderLauncher.launch(null)
            } catch (e: Exception) {
                drivePickerRepository.handleFolderResult(null)
            }
        }
    }
}
