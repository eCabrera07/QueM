package com.quem.drive

interface DrivePickerCoordinator {
    fun pickFile(onResult: (DriveSelection?) -> Unit)
    fun pickFolder(onResult: (DriveSelection?) -> Unit)
}

class NoOpDrivePickerCoordinator : DrivePickerCoordinator {
    override fun pickFile(onResult: (DriveSelection?) -> Unit) = onResult(null)
    override fun pickFolder(onResult: (DriveSelection?) -> Unit) = onResult(null)
}
