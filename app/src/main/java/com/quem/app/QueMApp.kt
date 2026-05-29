package com.quem.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quem.data.repository.QueueRepository
import com.quem.drive.DisconnectedDriveConnectionRepository
import com.quem.drive.DriveConnectionRepository
import com.quem.drive.DriveConnectionState
import com.quem.drive.DrivePickerCoordinator
import com.quem.drive.NoOpDrivePickerCoordinator
import com.quem.ui.CreateItemScreen
import com.quem.ui.ItemDetailScreen
import com.quem.ui.QueueListScreen
import com.quem.ui.QueueViewModel
import com.quem.ui.SettingsScreen

@Composable
fun QueMApp(
    queueRepository: QueueRepository,
    driveConnectionRepository: DriveConnectionRepository = DisconnectedDriveConnectionRepository(),
    drivePickerCoordinator: DrivePickerCoordinator = NoOpDrivePickerCoordinator()
) {
    val viewModel: QueueViewModel = viewModel(
        factory = QueueViewModel.factory(
            repository = queueRepository,
            driveConnectionRepository = driveConnectionRepository
        )
    )
    val selectedStatus by viewModel.selectedStatus.collectAsStateWithLifecycle()
    val isCreatingItem by viewModel.isCreatingItem.collectAsStateWithLifecycle()
    val isShowingSettings by viewModel.isShowingSettings.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val selectedItem by viewModel.selectedItem.collectAsStateWithLifecycle()
    val driveConnectionState by viewModel.driveConnectionState.collectAsStateWithLifecycle()

    if (isShowingSettings) {
        SettingsScreen(
            accountEmail = driveConnectionState.accountEmail(),
            syncStatus = driveConnectionState.syncStatusLabel(),
            onManualSync = {},
            onSignIn = viewModel::requestDriveSignIn,
            onDisconnect = viewModel::disconnectDrive,
            onBack = viewModel::closeSettings
        )
    } else if (isCreatingItem) {
        CreateItemScreen(
            onSave = { title, description, priority, dueDate ->
                viewModel.createItem(
                    title = title,
                    description = description,
                    priority = priority,
                    dueDate = dueDate
                )
            },
            onCancel = viewModel::cancelCreate
        )
    } else if (selectedItem == null) {
        QueueListScreen(
            selectedStatus = selectedStatus,
            items = items,
            onStatusSelected = viewModel::selectStatus,
            onItemSelected = viewModel::selectItem,
            onCreateItem = viewModel::startCreate,
            onOpenSettings = viewModel::showSettings
        )
    } else {
        val item = selectedItem ?: return
        val driveConnected = driveConnectionState is DriveConnectionState.Connected
        ItemDetailScreen(
            title = item.title,
            description = item.description,
            dueDateLabel = item.dueDateLabel,
            attachments = item.attachments,
            history = item.history,
            onAddTextAttachment = viewModel::addTextAttachment,
            onAddLinkAttachment = viewModel::addLinkAttachment,
            driveActionsEnabled = driveConnected,
            onAttachDriveFile = {
                drivePickerCoordinator.pickFile { selection ->
                    if (selection != null) {
                        viewModel.addDriveFileAttachment(
                            title = selection.name,
                            driveFileId = selection.id,
                            mimeType = selection.mimeType
                        )
                    }
                }
            },
            onAttachDriveFolder = {
                drivePickerCoordinator.pickFolder { selection ->
                    if (selection != null) {
                        viewModel.addDriveFolderAttachment(
                            title = selection.name,
                            driveFolderId = selection.id
                        )
                    }
                }
            },
            onDismiss = viewModel::dismissSelectedItem,
            onDone = viewModel::doneSelectedItem,
            onBack = viewModel::backToList
        )
    }
}

private fun DriveConnectionState.accountEmail(): String? =
    when (this) {
        is DriveConnectionState.Connected -> account.email
        DriveConnectionState.Disconnected,
        is DriveConnectionState.Error -> null
    }

private fun DriveConnectionState.syncStatusLabel(): String =
    when (this) {
        is DriveConnectionState.Connected -> "Drive connected"
        DriveConnectionState.Disconnected -> "Sync unavailable"
        is DriveConnectionState.Error -> message
    }
