package com.quem.drive

import android.net.Uri
import androidx.activity.result.ActivityResultLauncher

class SafDrivePickerCoordinator(
    private val fileLauncher: ActivityResultLauncher<Array<String>>,
    private val folderLauncher: ActivityResultLauncher<Uri?>,
    private val drivePickerRepository: DrivePickerRepository
) : DrivePickerCoordinator {
    override fun pickFile(onResult: (DriveSelection?) -> Unit) {
        if (drivePickerRepository.setPendingFileCallback(onResult)) {
            fileLauncher.launch(arrayOf("*/*"))
        }
    }

    override fun pickFolder(onResult: (DriveSelection?) -> Unit) {
        if (drivePickerRepository.setPendingFolderCallback(onResult)) {
            folderLauncher.launch(null)
        }
    }
}
