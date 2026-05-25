package com.quem.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quem.data.repository.QueueRepository
import com.quem.drive.DisconnectedDriveConnectionRepository
import com.quem.drive.DriveConnectionRepository
import com.quem.drive.DriveConnectionState
import com.quem.drive.DriveSelection
import com.quem.ui.CreateItemScreen
import com.quem.ui.ItemDetailScreen
import com.quem.ui.QueueListScreen
import com.quem.ui.QueueViewModel

@Composable
fun QueMApp(
    queueRepository: QueueRepository,
    driveConnectionRepository: DriveConnectionRepository = DisconnectedDriveConnectionRepository(),
    onPickDriveFile: () -> DriveSelection? = { null },
    onPickDriveFolder: () -> DriveSelection? = { null }
) {
    val viewModel: QueueViewModel = viewModel(
        factory = QueueViewModel.factory(
            repository = queueRepository,
            driveConnectionRepository = driveConnectionRepository
        )
    )
    val selectedStatus by viewModel.selectedStatus.collectAsStateWithLifecycle()
    val isCreatingItem by viewModel.isCreatingItem.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val selectedItem by viewModel.selectedItem.collectAsStateWithLifecycle()
    val driveConnectionState by viewModel.driveConnectionState.collectAsStateWithLifecycle()

    if (isCreatingItem) {
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
            onCreateItem = viewModel::startCreate
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
                val selection = onPickDriveFile()
                if (selection != null) {
                    viewModel.addDriveFileAttachment(
                        title = selection.name,
                        driveFileId = selection.id,
                        mimeType = selection.mimeType
                    )
                }
            },
            onAttachDriveFolder = {
                val selection = onPickDriveFolder()
                if (selection != null) {
                    viewModel.addDriveFolderAttachment(
                        title = selection.name,
                        driveFolderId = selection.id
                    )
                }
            },
            onDismiss = viewModel::dismissSelectedItem,
            onDone = viewModel::doneSelectedItem,
            onBack = viewModel::backToList
        )
    }
}
