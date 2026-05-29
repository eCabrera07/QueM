package com.quem.drive

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivePickerRepositoryTest {
    private val repository = DrivePickerRepository(
        contentResolver = ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver
    )

    @Test
    fun handleFileResultWithNullUriCallsCallbackWithNull() {
        var result: DriveSelection? = DriveSelection("initial", "initial", null, false)
        repository.setPendingFileCallback { result = it }

        repository.handleFileResult(null)

        assertNull(result)
    }

    @Test
    fun handleFolderResultWithNullUriCallsCallbackWithNull() {
        var result: DriveSelection? = DriveSelection("initial", "initial", null, true)
        repository.setPendingFolderCallback { result = it }

        repository.handleFolderResult(null)

        assertNull(result)
    }

    @Test
    fun setPendingFileCallbackReturnsTrueWhenNoPendingCallback() {
        assertTrue(repository.setPendingFileCallback {})
    }

    @Test
    fun setPendingFileCallbackReturnsFalseWhenCallbackAlreadyPending() {
        repository.setPendingFileCallback {}

        assertFalse(repository.setPendingFileCallback {})
    }

    @Test
    fun callbackIsClearedAfterHandleFileResult() {
        var invocations = 0
        repository.setPendingFileCallback { invocations++ }

        repository.handleFileResult(null)
        repository.handleFileResult(null)  // second call: callback is already cleared

        assertEquals(1, invocations)
    }

    @Test
    fun handleFileResultWithoutPendingCallbackDoesNothing() {
        // Must not crash
        repository.handleFileResult(null)
    }
}
