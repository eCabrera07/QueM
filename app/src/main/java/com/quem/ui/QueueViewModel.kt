package com.quem.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.quem.core.model.Attachment
import com.quem.core.model.AttachmentType
import com.quem.core.model.HistoryEntry
import com.quem.core.model.Priority
import com.quem.core.model.QueueItem
import com.quem.core.model.QueueStatus
import com.quem.core.model.SyncState
import com.quem.core.time.Clock
import com.quem.core.time.SystemClock
import com.quem.data.repository.QueueRepository
import android.content.ContentResolver
import com.quem.drive.DisconnectedDriveConnectionRepository
import com.quem.drive.DriveConnectionRepository
import com.quem.drive.DriveConnectionState
import com.quem.drive.DriveFileUploadGateway
import com.quem.drive.DriveShareGateway
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

enum class SyncIndicator { PENDING, SYNCING, ERROR }

enum class UploadState { NONE, IN_PROGRESS, FAILED }

data class AttachmentUi(
    val id: String,
    val displayName: String,
    val url: String?,
    val driveFileId: String?,
    val isLink: Boolean,
    val isDriveFile: Boolean,
    val isDriveFolder: Boolean,
    val uploadState: UploadState = UploadState.NONE
)

data class HistoryEntryUi(
    val id: String,
    val displayText: String
)

data class QueueItemDetailUi(
    val id: String,
    val title: String,
    val description: String?,
    val priorityLabel: String?,
    val dueDateLabel: String?,
    val dueDateIso: String?,    // ISO-8601 (e.g. "2026-06-01") — use for pre-populating edit forms
    val attachments: List<AttachmentUi>,
    val history: List<HistoryEntryUi>,
    val syncIndicator: SyncIndicator?,
    val status: QueueStatus,
    val sharedDriveFileId: String?,
    val sharedWith: List<String>
)

@OptIn(ExperimentalCoroutinesApi::class)
class QueueViewModel(
    private val repository: QueueRepository,
    private val driveConnectionRepository: DriveConnectionRepository = DisconnectedDriveConnectionRepository(),
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val clock: Clock = SystemClock(),
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uploadingAttachmentIds = MutableStateFlow<Set<String>>(emptySet())

    val selectedStatus: StateFlow<QueueStatus> =
        savedStateHandle.getStateFlow(KEY_SELECTED_STATUS, QueueStatus.QUEUED)

    private val selectedItemId: StateFlow<String?> =
        savedStateHandle.getStateFlow(KEY_SELECTED_ITEM_ID, null)

    private val _screen = MutableStateFlow(restoredScreen())
    val screen: StateFlow<QueMScreen> = _screen

    val isCreatingItem: StateFlow<Boolean> =
        savedStateHandle.getStateFlow(KEY_IS_CREATING_ITEM, screen.value == QueMScreen.Create)

    val isShowingSettings: StateFlow<Boolean> =
        savedStateHandle.getStateFlow(KEY_IS_SHOWING_SETTINGS, screen.value == QueMScreen.Settings)

    val driveConnectionState: StateFlow<DriveConnectionState> =
        driveConnectionRepository.state

    val items: StateFlow<List<QueueListItemUi>> =
        selectedStatus
            .flatMapLatest { status -> repository.observeItems(status) }
            .flatMapLatest { items ->
                if (items.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(
                        items.map { item ->
                            repository.observeAttachments(item.id).map { attachments ->
                                item.toListItemUi(attachmentCount = attachments.size)
                            }
                        }
                    ) { listItems -> listItems.toList() }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                initialValue = emptyList()
            )

    val selectedItem: StateFlow<QueueItemDetailUi?> =
        combine(
            selectedItemId
                .flatMapLatest { id ->
                    if (id == null) {
                        flowOf(null)
                    } else {
                        combine(
                            repository.observeItem(id),
                            repository.observeAttachments(id),
                            repository.observeHistory(id)
                        ) { item, attachments, history ->
                            val now = clock.now()
                            item?.toDetailUi(
                                attachments = attachments.map { it.toAttachmentUi() },
                                history     = history.map { HistoryEntryUi(it.id, it.toDisplayString(now)) }
                            )
                        }
                    }
                },
            _uploadingAttachmentIds
        ) { detail, uploadingIds ->
            detail?.copy(
                attachments = detail.attachments.map { att ->
                    if (att.id in uploadingIds) att.copy(uploadState = UploadState.IN_PROGRESS)
                    else att
                }
            )
        }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = null
        )

    val isShowingArchive: StateFlow<Boolean> =
        savedStateHandle.getStateFlow(KEY_IS_SHOWING_ARCHIVE, screen.value == QueMScreen.Archive)

    val isEditingItem: StateFlow<Boolean> =
        savedStateHandle.getStateFlow(KEY_IS_EDITING_ITEM, screen.value is QueMScreen.Edit)

    val archiveQuery: StateFlow<String> =
        savedStateHandle.getStateFlow(KEY_ARCHIVE_QUERY, "")

    val archiveResults: StateFlow<List<QueueListItemUi>> =
        archiveQuery
            .flatMapLatest { query -> repository.searchArchive(query) }
            .flatMapLatest { items ->
                if (items.isEmpty()) flowOf(emptyList())
                else combine(
                    items.map { item ->
                        repository.observeAttachments(item.id).map { attachments ->
                            item.toListItemUi(attachmentCount = attachments.size)
                        }
                    }
                ) { results -> results.toList() }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                initialValue = emptyList()
            )

    init {
        syncCompatibilityFlags(screen.value)
    }

    fun selectStatus(status: QueueStatus) {
        savedStateHandle[KEY_SELECTED_STATUS] = status
    }

    fun selectItem(id: String) {
        savedStateHandle[KEY_SELECTED_ITEM_ID] = id
        setScreen(ROUTE_DETAIL, selectedItemId = id)
    }

    fun startCreate() {
        savedStateHandle[KEY_SELECTED_ITEM_ID] = null
        setScreen(ROUTE_CREATE, selectedItemId = null)
    }

    fun cancelCreate() {
        setScreen(ROUTE_LIST)
    }

    fun showSettings() {
        savedStateHandle[KEY_SELECTED_ITEM_ID] = null
        setScreen(ROUTE_SETTINGS, selectedItemId = null)
    }

    fun closeSettings() {
        setScreen(ROUTE_LIST)
    }

    fun showArchive() {
        savedStateHandle[KEY_ARCHIVE_QUERY] = ""
        savedStateHandle[KEY_SELECTED_ITEM_ID] = null
        setScreen(ROUTE_ARCHIVE, selectedItemId = null)
    }

    fun closeArchive() {
        setScreen(ROUTE_LIST)
    }

    fun startEdit() {
        val id = selectedItemId.value ?: return
        setScreen(ROUTE_EDIT, selectedItemId = id)
    }

    fun cancelEdit() {
        val id = selectedItemId.value
        setScreen(if (id == null) ROUTE_LIST else ROUTE_DETAIL, selectedItemId = id)
    }

    fun saveEdit(title: String, description: String?, priority: String?, dueDate: String?) {
        val id = selectedItemId.value ?: return
        viewModelScope.launch {
            repository.updateItem(
                id          = id,
                title       = title,
                description = description,
                priority    = priority.toPriorityOrNull(),
                dueDate     = dueDate.toLocalDateOrNull()
            )
            setScreen(ROUTE_DETAIL, selectedItemId = id)
        }
    }

    fun selectArchiveItem(id: String) {
        savedStateHandle[KEY_SELECTED_ITEM_ID] = id
        setScreen(ROUTE_DETAIL, selectedItemId = id)
    }

    fun setArchiveQuery(query: String) {
        savedStateHandle[KEY_ARCHIVE_QUERY] = query
    }

    fun requestDriveSignIn() {
        driveConnectionRepository.requestSignIn()
    }

    fun disconnectDrive() {
        driveConnectionRepository.disconnect()
    }

    fun createItem(
        title: String,
        description: String?,
        priority: String? = null,
        dueDate: String? = null
    ) {
        viewModelScope.launch {
            val created = repository.createItem(
                title = title,
                description = description,
                priority = priority.toPriorityOrNull(),
                dueDate = dueDate.toLocalDateOrNull()
            )
            savedStateHandle[KEY_SELECTED_STATUS] = QueueStatus.QUEUED
            savedStateHandle[KEY_SELECTED_ITEM_ID] = created.id
            setScreen(ROUTE_DETAIL, selectedItemId = created.id)
        }
    }

    fun doneSelectedItem() {
        moveSelectedItemTo(QueueStatus.DONE)
    }

    fun dismissSelectedItem() {
        moveSelectedItemTo(QueueStatus.DISMISSED)
    }

    fun changeStatusOfSelectedItem(status: QueueStatus) {
        moveSelectedItemTo(status)
    }

    fun addTextAttachment(title: String, text: String) {
        val id = selectedItemId.value ?: return
        viewModelScope.launch {
            repository.addTextAttachment(
                queueItemId = id,
                title = title,
                text = text
            )
        }
    }

    fun addLinkAttachment(title: String, url: String) {
        val id = selectedItemId.value ?: return
        viewModelScope.launch {
            repository.addLinkAttachment(
                queueItemId = id,
                title = title,
                url = url
            )
        }
    }

    fun addDriveFileAttachment(title: String, driveFileId: String, mimeType: String?) {
        addDriveAttachment(
            title = title,
            driveFileId = driveFileId,
            mimeType = mimeType,
            isFolder = false
        )
    }

    fun addDriveFolderAttachment(title: String, driveFolderId: String) {
        addDriveAttachment(
            title = title,
            driveFileId = driveFolderId,
            mimeType = null,
            isFolder = true
        )
    }

    fun deleteAttachment(attachmentId: String) {
        viewModelScope.launch { repository.deleteAttachment(attachmentId) }
    }

    fun updateAttachmentTitle(attachmentId: String, title: String) {
        viewModelScope.launch { repository.updateAttachmentTitle(attachmentId, title) }
    }

    fun deleteHistoryEntry(historyEntryId: String) {
        viewModelScope.launch { repository.deleteHistoryEntry(historyEntryId) }
    }

    fun attachAndUploadLocalFile(
        uri: String,
        displayName: String,
        mimeType: String?,
        contentResolver: ContentResolver,
        gatewayFactory: () -> DriveFileUploadGateway
    ) {
        val itemId = selectedItemId.value ?: return
        viewModelScope.launch {
            val attachmentId = repository.attachLocalFile(
                queueItemId = itemId,
                uri         = uri,
                displayName = displayName,
                mimeType    = mimeType
            )
            if (attachmentId.isBlank()) return@launch
            _uploadingAttachmentIds.update { it + attachmentId }
            try {
                val gateway = withContext(ioDispatcher) { gatewayFactory() }
                repository.uploadPendingFile(attachmentId, contentResolver, gateway)
            } finally {
                _uploadingAttachmentIds.update { it - attachmentId }
            }
        }
    }

    fun retryFileUpload(
        attachmentId: String,
        contentResolver: ContentResolver,
        gatewayFactory: () -> DriveFileUploadGateway
    ) {
        viewModelScope.launch {
            _uploadingAttachmentIds.update { it + attachmentId }
            try {
                val gateway = withContext(ioDispatcher) { gatewayFactory() }
                repository.retryFileUpload(attachmentId, contentResolver, gateway)
            } finally {
                _uploadingAttachmentIds.update { it - attachmentId }
            }
        }
    }

    fun backToList() {
        savedStateHandle[KEY_SELECTED_ITEM_ID] = null
        setScreen(ROUTE_LIST, selectedItemId = null)
    }

    fun navigateBack() {
        when (val currentScreen = screen.value) {
            QueMScreen.List -> Unit
            QueMScreen.Create,
            QueMScreen.Settings,
            QueMScreen.Archive -> setScreen(ROUTE_LIST)
            is QueMScreen.Detail -> backToList()
            is QueMScreen.Edit -> {
                savedStateHandle[KEY_SELECTED_ITEM_ID] = currentScreen.itemId
                setScreen(ROUTE_DETAIL, selectedItemId = currentScreen.itemId)
            }
        }
    }

    val isShowingShareDialog: StateFlow<Boolean> =
        savedStateHandle.getStateFlow(KEY_IS_SHOWING_SHARE_DIALOG, false)

    private val _shareError = MutableStateFlow<String?>(null)
    val shareError: StateFlow<String?> = _shareError.asStateFlow()

    private val _isSharingInFlight = MutableStateFlow(false)
    val isSharingInFlight: StateFlow<Boolean> = _isSharingInFlight.asStateFlow()

    fun showShareDialog() {
        savedStateHandle[KEY_IS_SHOWING_SHARE_DIALOG] = true
        _shareError.value = null
    }

    fun closeShareDialog() {
        savedStateHandle[KEY_IS_SHOWING_SHARE_DIALOG] = false
        _shareError.value = null
    }

    fun setShareError(message: String) {
        _shareError.value = message
    }

    fun shareItem(recipientEmail: String, gatewayFactory: () -> DriveShareGateway) {
        if (_isSharingInFlight.value) return   // guard against double-tap
        val id = selectedItemId.value ?: return
        viewModelScope.launch {
            _isSharingInFlight.value = true
            try {
                val gateway = withContext(ioDispatcher) { gatewayFactory() }
                val success = repository.shareItem(id, recipientEmail, gateway)
                if (success) {
                    savedStateHandle[KEY_IS_SHOWING_SHARE_DIALOG] = false
                    _shareError.value = null
                } else {
                    _shareError.value = "Could not share item. Check the email and try again."
                }
            } finally {
                _isSharingInFlight.value = false
            }
        }
    }

    private fun setScreen(route: String, selectedItemId: String? = this.selectedItemId.value) {
        val nextSelectedItemId = selectedItemId.takeIf { route.isItemRoute() }
        val nextScreen = route.toScreen(nextSelectedItemId)
        savedStateHandle[KEY_SCREEN_ROUTE] = nextScreen.toRoute()
        savedStateHandle[KEY_SELECTED_ITEM_ID] = nextSelectedItemId
        syncCompatibilityFlags(nextScreen)
        _screen.value = nextScreen
    }

    private fun syncCompatibilityFlags(screen: QueMScreen) {
        savedStateHandle[KEY_IS_CREATING_ITEM] = screen == QueMScreen.Create
        savedStateHandle[KEY_IS_SHOWING_SETTINGS] = screen == QueMScreen.Settings
        savedStateHandle[KEY_IS_SHOWING_ARCHIVE] = screen == QueMScreen.Archive
        savedStateHandle[KEY_IS_EDITING_ITEM] = screen is QueMScreen.Edit
    }

    private fun restoredScreen(): QueMScreen {
        val route = restoredRoute()
        val restoredSelectedItemId = selectedItemId.value.takeIf { route.isItemRoute() }
        val restoredScreen = route.toScreen(restoredSelectedItemId)
        savedStateHandle[KEY_SCREEN_ROUTE] = restoredScreen.toRoute()
        savedStateHandle[KEY_SELECTED_ITEM_ID] = restoredSelectedItemId
        return restoredScreen
    }

    private fun restoredRoute(): String =
        savedStateHandle.get<String>(KEY_SCREEN_ROUTE) ?: legacyRoute()

    private fun legacyRoute(): String = when {
        savedStateHandle.get<Boolean>(KEY_IS_SHOWING_SETTINGS) == true -> ROUTE_SETTINGS
        savedStateHandle.get<Boolean>(KEY_IS_CREATING_ITEM) == true -> ROUTE_CREATE
        savedStateHandle.get<Boolean>(KEY_IS_EDITING_ITEM) == true -> ROUTE_EDIT
        savedStateHandle.get<Boolean>(KEY_IS_SHOWING_ARCHIVE) == true -> ROUTE_ARCHIVE
        selectedItemId.value != null -> ROUTE_DETAIL
        else -> ROUTE_LIST
    }

    private fun addDriveAttachment(
        title: String,
        driveFileId: String,
        mimeType: String?,
        isFolder: Boolean
    ) {
        val id = selectedItemId.value ?: return
        viewModelScope.launch {
            repository.addDriveAttachment(
                queueItemId = id,
                title = title,
                driveFileId = driveFileId,
                mimeType = mimeType,
                isFolder = isFolder
            )
        }
    }

    private fun moveSelectedItemTo(status: QueueStatus) {
        val id = selectedItemId.value ?: return
        viewModelScope.launch {
            repository.changeStatus(id = id, status = status)
            savedStateHandle[KEY_SELECTED_STATUS] = status
            backToList()
        }
    }

    companion object {
        fun factory(
            repository: QueueRepository,
            driveConnectionRepository: DriveConnectionRepository = DisconnectedDriveConnectionRepository()
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(QueueViewModel::class.java)) {
                        return QueueViewModel(
                            repository = repository,
                            driveConnectionRepository = driveConnectionRepository
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }

                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    if (modelClass.isAssignableFrom(QueueViewModel::class.java)) {
                        return QueueViewModel(
                            repository = repository,
                            driveConnectionRepository = driveConnectionRepository,
                            savedStateHandle = extras.createSavedStateHandle()
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }

        private const val KEY_SELECTED_STATUS = "selectedStatus"
        private const val KEY_SCREEN_ROUTE = "screenRoute"
        private const val KEY_IS_CREATING_ITEM = "isCreatingItem"
        private const val KEY_IS_SHOWING_SETTINGS = "isShowingSettings"
        private const val KEY_SELECTED_ITEM_ID = "selectedItemId"
        private const val KEY_IS_SHOWING_ARCHIVE = "isShowingArchive"
        private const val KEY_ARCHIVE_QUERY            = "archiveQuery"
        private const val KEY_IS_EDITING_ITEM          = "isEditingItem"
        private const val KEY_IS_SHOWING_SHARE_DIALOG  = "isShowingShareDialog"

        private const val ROUTE_LIST = "list"
        private const val ROUTE_CREATE = "create"
        private const val ROUTE_SETTINGS = "settings"
        private const val ROUTE_ARCHIVE = "archive"
        private const val ROUTE_DETAIL = "detail"
        private const val ROUTE_EDIT = "edit"
    }
}

private fun String.toScreen(selectedItemId: String?): QueMScreen = when (this) {
    "create" -> QueMScreen.Create
    "settings" -> QueMScreen.Settings
    "archive" -> QueMScreen.Archive
    "detail" -> selectedItemId?.let(QueMScreen::Detail) ?: QueMScreen.List
    "edit" -> selectedItemId?.let(QueMScreen::Edit) ?: QueMScreen.List
    else -> QueMScreen.List
}

private fun String.isItemRoute(): Boolean =
    this == "detail" || this == "edit"

private fun QueMScreen.toRoute(): String = when (this) {
    QueMScreen.List -> "list"
    QueMScreen.Create -> "create"
    QueMScreen.Settings -> "settings"
    QueMScreen.Archive -> "archive"
    is QueMScreen.Detail -> "detail"
    is QueMScreen.Edit -> "edit"
}

private fun QueueItem.toListItemUi(attachmentCount: Int) = QueueListItemUi(
    id                = id,
    title             = title,
    priorityLabel     = priority?.name,
    dueDateLabel      = dueDate?.toString(),
    attachmentSummary = attachmentCount.toAttachmentSummary(),
    syncIndicator     = syncState.toIndicator()
)

private fun QueueItem.toDetailUi(attachments: List<AttachmentUi>, history: List<HistoryEntryUi>) = QueueItemDetailUi(
    id                = id,
    title             = title,
    description       = description,
    priorityLabel     = priority?.name,
    dueDateLabel      = dueDate?.toString(),
    dueDateIso        = dueDate?.toString(),
    attachments       = attachments,
    history           = history,
    syncIndicator     = syncState.toIndicator(),
    status            = status,
    sharedDriveFileId = sharedDriveFileId,
    sharedWith        = sharedWith
)

private fun Int.toAttachmentSummary(): String =
    if (this == 1) "1 attachment" else "$this attachments"

private fun Attachment.toAttachmentUi() = AttachmentUi(
    id            = id,
    displayName   = displayName,
    url           = url,
    driveFileId   = driveFileId,
    isLink        = type == AttachmentType.LINK,
    isDriveFile   = type == AttachmentType.DRIVE_FILE,
    isDriveFolder = type == AttachmentType.DRIVE_FOLDER,
    uploadState   = when (syncState) {
        SyncState.UPLOAD_FAILED  -> UploadState.FAILED
        SyncState.PENDING_UPLOAD -> UploadState.IN_PROGRESS  // re-enqueued on next sync attempt
        else                     -> UploadState.NONE
    }
)

private fun SyncState.toIndicator(): SyncIndicator? = when (this) {
    SyncState.SYNCED         -> null
    SyncState.PENDING_SYNC   -> SyncIndicator.PENDING
    SyncState.SYNCING        -> SyncIndicator.SYNCING
    SyncState.ERROR          -> SyncIndicator.ERROR
    SyncState.PENDING_UPLOAD -> SyncIndicator.PENDING
    SyncState.UPLOAD_FAILED  -> SyncIndicator.ERROR
}

private fun String?.toPriorityOrNull(): Priority? {
    val normalized = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return Priority.entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) }
}

private fun String?.toLocalDateOrNull(): LocalDate? {
    val normalized = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { LocalDate.parse(normalized) }.getOrNull()
}

// internal (not private) so QueueViewModelTest can call it directly without instrumentation
internal fun HistoryEntry.toDisplayString(now: Instant): String {
    val elapsed = Duration.between(createdAt, now)
    val timeLabel = when {
        elapsed.isNegative -> "just now"
        elapsed.seconds < 60 -> "just now"
        elapsed.toMinutes() < 60 -> {
            val m = elapsed.toMinutes()
            if (m == 1L) "1 minute ago" else "$m minutes ago"
        }
        elapsed.toHours() < 24 -> {
            val h = elapsed.toHours()
            if (h == 1L) "1 hour ago" else "$h hours ago"
        }
        else -> {
            val d = elapsed.toDays()
            if (d == 1L) "1 day ago" else "$d days ago"
        }
    }
    return "$timeLabel · $message"
}
