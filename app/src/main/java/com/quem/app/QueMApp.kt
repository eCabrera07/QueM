package com.quem.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.quem.data.repository.QueueRepository
import com.quem.data.sync.SyncScheduler
import com.quem.drive.DisconnectedDriveConnectionRepository
import com.quem.drive.DriveConnectionRepository
import com.quem.drive.DriveConnectionState
import com.quem.drive.DrivePickerCoordinator
import com.quem.drive.GoogleDriveAuthorizationCoordinator
import com.quem.drive.GoogleDriveShareGateway
import com.quem.drive.NoOpDrivePickerCoordinator
import com.quem.ui.ArchiveSearchScreen
import com.quem.ui.CreateItemScreen
import com.quem.ui.EditItemScreen
import com.quem.ui.ItemDetailScreen
import com.quem.ui.QueueListScreen
import com.quem.ui.QueueViewModel
import com.quem.ui.QueMScreen
import com.quem.ui.SettingsScreen

@Composable
fun QueMApp(
    queueRepository: QueueRepository,
    driveConnectionRepository: DriveConnectionRepository = DisconnectedDriveConnectionRepository(),
    drivePickerCoordinator: DrivePickerCoordinator = NoOpDrivePickerCoordinator
) {
    val context = LocalContext.current
    val viewModel: QueueViewModel = viewModel(
        factory = QueueViewModel.factory(
            repository = queueRepository,
            driveConnectionRepository = driveConnectionRepository
        )
    )
    val selectedStatus by viewModel.selectedStatus.collectAsStateWithLifecycle()
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val selectedItem by viewModel.selectedItem.collectAsStateWithLifecycle()
    val driveConnectionState by viewModel.driveConnectionState.collectAsStateWithLifecycle()
    val archiveQuery by viewModel.archiveQuery.collectAsStateWithLifecycle()
    val archiveResults by viewModel.archiveResults.collectAsStateWithLifecycle()
    val isShowingShareDialog by viewModel.isShowingShareDialog.collectAsStateWithLifecycle()
    val isSharingInFlight    by viewModel.isSharingInFlight.collectAsStateWithLifecycle()
    val shareError           by viewModel.shareError.collectAsStateWithLifecycle()

    when (screen) {
        QueMScreen.List -> {
            QueueListScreen(
                selectedStatus = selectedStatus,
                items = items,
                onStatusSelected = viewModel::selectStatus,
                onItemSelected = viewModel::selectItem,
                onCreateItem = viewModel::startCreate,
                onOpenSettings = viewModel::showSettings,
                onOpenArchive = viewModel::showArchive
            )
        }

        QueMScreen.Create -> {
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
        }

        QueMScreen.Settings -> {
            SettingsScreen(
                accountEmail = driveConnectionState.accountEmail(),
                syncStatus = driveConnectionState.syncStatusLabel(),
                onManualSync = { SyncScheduler.scheduleOnce(context) },
                onSignIn = viewModel::requestDriveSignIn,
                onDisconnect = viewModel::disconnectDrive,
                onBack = viewModel::closeSettings
            )
        }

        QueMScreen.Archive -> {
            ArchiveSearchScreen(
                query = archiveQuery,
                results = archiveResults,
                onQueryChange = viewModel::setArchiveQuery,
                onItemSelected = viewModel::selectArchiveItem,
                onBack = viewModel::closeArchive
            )
        }

        is QueMScreen.Detail -> {
            val item = selectedItem ?: return
            ItemDetailScreen(
                title = item.title,
                description = item.description,
                dueDateLabel = item.dueDateLabel,
                priorityLabel = item.priorityLabel,
                attachments = item.attachments,
                history = item.history,
                syncIndicator = item.syncIndicator,
                currentStatus = item.status,
                onStatusChange = viewModel::changeStatusOfSelectedItem,
                onEdit = viewModel::startEdit,
                onDeleteAttachment = viewModel::deleteAttachment,
                onRenameAttachment = viewModel::updateAttachmentTitle,
                onDeleteHistoryEntry = viewModel::deleteHistoryEntry,
                sharedWith           = item.sharedWith,
                isShowingShareDialog = isShowingShareDialog,
                isSharingInFlight    = isSharingInFlight,
                shareError           = shareError,
                onShare              = viewModel::showShareDialog,
                onShareDialogDismiss = viewModel::closeShareDialog,
                onShareConfirm       = { email ->
                    val connectedState = driveConnectionState as? DriveConnectionState.Connected
                    val accountEmail = connectedState?.account?.email
                    if (accountEmail == null) {
                        viewModel.setShareError("Sign in to Google Drive to share items")
                    } else {
                        // Gateway factory — called on Dispatchers.IO inside viewModel.shareItem
                        viewModel.shareItem(email) {
                            val credential = GoogleAccountCredential
                                .usingOAuth2(context, listOf(GoogleDriveAuthorizationCoordinator.DRIVE_FILE_SCOPE))
                                .setSelectedAccountName(accountEmail)
                            val drive = Drive.Builder(
                                NetHttpTransport(),
                                GsonFactory.getDefaultInstance(),
                                credential
                            ).setApplicationName("QueM").build()
                            GoogleDriveShareGateway(drive)
                        }
                    }
                },
                onBack = viewModel::backToList
            )
        }

        is QueMScreen.Edit -> {
            val item = selectedItem ?: return
            EditItemScreen(
                initialTitle = item.title,
                initialDescription = item.description ?: "",
                initialPriority = item.priorityLabel ?: "",
                initialDueDate = item.dueDateIso ?: "",
                attachments = item.attachments,
                onSave = viewModel::saveEdit,
                onCancel = viewModel::cancelEdit,
                onAddTextAttachment = viewModel::addTextAttachment,
                onAddLinkAttachment = viewModel::addLinkAttachment,
                onAttachDriveFile = { title, id, mime ->
                    viewModel.addDriveFileAttachment(title, id, mime)
                },
                onAttachDriveFolder = { title, id ->
                    viewModel.addDriveFolderAttachment(title, id)
                },
                onDeleteAttachment = viewModel::deleteAttachment,
                onRenameAttachment = viewModel::updateAttachmentTitle
            )
        }
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
