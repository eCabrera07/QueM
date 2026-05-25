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
            driveAuthorizationCoordinator.dispatchResolutionResult(
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
        val driveConnectionRepository = dependencies.driveConnectionRepository(
            authorizationCoordinator = driveAuthorizationCoordinator
        )

        setContent {
            QueMTheme {
                QueMApp(
                    queueRepository = dependencies.queueRepository,
                    driveConnectionRepository = driveConnectionRepository
                )
            }
        }
    }
}
