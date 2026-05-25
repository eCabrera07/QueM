package com.quem.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.quem.drive.ActivityResultData
import com.quem.drive.GoogleDriveAuthorizationCoordinator
import com.quem.ui.theme.QueMTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dependencies = (application as QueMApplication).dependencies
        lateinit var driveAuthorizationCoordinator: GoogleDriveAuthorizationCoordinator
        val driveAuthorizationLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            dependencies.driveConnectionRepository.handleResolutionResult(
                ActivityResultData(
                    resultCode = result.resultCode,
                    data = result.data
                )
            )
        }
        driveAuthorizationCoordinator = GoogleDriveAuthorizationCoordinator(
            activity = this,
            resolutionLauncher = driveAuthorizationLauncher
        )
        dependencies.driveConnectionRepository.setAuthorizationCoordinator(driveAuthorizationCoordinator)

        setContent {
            QueMTheme {
                QueMApp(
                    queueRepository = dependencies.queueRepository,
                    driveConnectionRepository = dependencies.driveConnectionRepository
                )
            }
        }
    }
}
