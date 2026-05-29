package com.quem.drive

import org.junit.Assert.assertNull
import org.junit.Test

class DrivePickerCoordinatorTest {
    @Test
    fun noOpPickFileCallsCallbackWithNull() {
        val coordinator: DrivePickerCoordinator = NoOpDrivePickerCoordinator()
        var result: DriveSelection? = DriveSelection("initial", "initial", null, false)

        coordinator.pickFile { result = it }

        assertNull(result)
    }

    @Test
    fun noOpPickFolderCallsCallbackWithNull() {
        val coordinator: DrivePickerCoordinator = NoOpDrivePickerCoordinator()
        var result: DriveSelection? = DriveSelection("initial", "initial", null, true)

        coordinator.pickFolder { result = it }

        assertNull(result)
    }
}
