package com.quem.drive

/**
 * Coordinates Drive file and folder picking via the Android Activity Result API.
 *
 * Implementations must invoke [pickFile] and [pickFolder] callbacks on the main thread.
 */
interface DrivePickerCoordinator {
    fun pickFile(onResult: (DriveSelection?) -> Unit)
    fun pickFolder(onResult: (DriveSelection?) -> Unit)
}

object NoOpDrivePickerCoordinator : DrivePickerCoordinator {
    override fun pickFile(onResult: (DriveSelection?) -> Unit) = onResult(null)
    override fun pickFolder(onResult: (DriveSelection?) -> Unit) = onResult(null)
}
