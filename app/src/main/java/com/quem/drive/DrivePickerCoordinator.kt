package com.quem.drive

interface DrivePickerCoordinator {
    fun pickFile(onResult: (DriveSelection?) -> Unit)
    fun pickFolder(onResult: (DriveSelection?) -> Unit)
    fun pickLocalFile(onResult: (LocalFileSelection?) -> Unit)
}

object NoOpDrivePickerCoordinator : DrivePickerCoordinator {
    override fun pickFile(onResult: (DriveSelection?) -> Unit)           = onResult(null)
    override fun pickFolder(onResult: (DriveSelection?) -> Unit)         = onResult(null)
    override fun pickLocalFile(onResult: (LocalFileSelection?) -> Unit)  = onResult(null)
}
