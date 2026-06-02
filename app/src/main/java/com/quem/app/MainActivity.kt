package com.quem.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.quem.drive.ActivityResultData
import com.quem.drive.GoogleDriveAuthorizationCoordinator
import com.quem.drive.SafDrivePickerCoordinator
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

        val filePickerLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            dependencies.drivePickerRepository.handleFileResult(uri)
        }
        val folderPickerLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri: Uri? ->
            dependencies.drivePickerRepository.handleFolderResult(uri)
        }
        val localFileLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            // Take persistent read permission so the URI is accessible for retry after process restart
            if (uri != null) {
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
            dependencies.drivePickerRepository.handleLocalFileResult(uri)
        }
        val drivePickerCoordinator = SafDrivePickerCoordinator(
            fileLauncher      = filePickerLauncher,
            folderLauncher    = folderPickerLauncher,
            localFileLauncher = localFileLauncher,
            drivePickerRepository = dependencies.drivePickerRepository
        )

        setContent {
            QueMTheme {
                QueMApp(
                    queueRepository = dependencies.queueRepository,
                    driveConnectionRepository = dependencies.driveConnectionRepository,
                    drivePickerCoordinator = drivePickerCoordinator
                )
            }
        }
    }
}
