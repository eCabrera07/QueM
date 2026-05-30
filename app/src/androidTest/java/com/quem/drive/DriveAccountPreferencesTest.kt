package com.quem.drive

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DriveAccountPreferencesTest {
    private lateinit var prefs: DriveAccountPreferences

    @Before
    fun setUp() {
        prefs = DriveAccountPreferences(ApplicationProvider.getApplicationContext())
        prefs.clear() // ensure clean state between tests
    }

    @Test
    fun loadReturnsNullWhenNothingSaved() {
        assertNull(prefs.load())
    }

    @Test
    fun saveAndLoadReturnsEmail() {
        prefs.save("user@example.com")
        assertEquals("user@example.com", prefs.load())
    }

    @Test
    fun clearRemovesEmail() {
        prefs.save("user@example.com")
        prefs.clear()
        assertNull(prefs.load())
    }

    @Test
    fun saveOverwritesPreviousEmail() {
        prefs.save("old@example.com")
        prefs.save("new@example.com")
        assertEquals("new@example.com", prefs.load())
    }
}
