package com.quem.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun showsAccountAndManualSyncControls() {
        compose.setContent {
            SettingsScreen(
                accountEmail = "user@example.com",
                syncStatus = "Last synced just now",
                onManualSync = {},
                onDisconnect = {}
            )
        }

        compose.onNodeWithText("user@example.com").assertIsDisplayed()
        compose.onNodeWithText("Last synced just now").assertIsDisplayed()
        compose.onNodeWithText("Sync now").assertIsDisplayed()
        compose.onNodeWithText("Disconnect").assertIsDisplayed()
    }

    @Test
    fun invokesManualSyncAndDisconnectCallbacks() {
        var manualSyncClicks = 0
        var disconnectClicks = 0

        compose.setContent {
            SettingsScreen(
                accountEmail = null,
                syncStatus = "Sync unavailable",
                onManualSync = { manualSyncClicks += 1 },
                onDisconnect = { disconnectClicks += 1 }
            )
        }

        compose.onNodeWithText("Not signed in").assertIsDisplayed()
        compose.onNodeWithText("Sync now").performClick()
        compose.onNodeWithText("Disconnect").performClick()

        assertEquals(1, manualSyncClicks)
        assertEquals(1, disconnectClicks)
    }
}
