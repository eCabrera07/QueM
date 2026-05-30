package com.quem.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.quem.core.model.HistoryEntry
import com.quem.core.model.Priority
import com.quem.core.model.QueueItem
import com.quem.core.model.QueueStatus
import com.quem.core.time.Clock
import com.quem.core.time.SystemClock
import com.quem.data.repository.QueueRepository
import com.quem.drive.DisconnectedDriveConnectionRepository
import com.quem.drive.DriveConnectionRepository
import com.quem.drive.DriveConnectionState
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class QueueItemDetailUi(
    val id: String,
    val title: String,
    val description: String?,
    val dueDateLabel: String?,
    val attachments: List<String>,
    val history: List<String>
)

@OptIn(ExperimentalCoroutinesApi::class)
class QueueViewModel(
    private val repository: QueueRepository,
    private val driveConnectionRepository: DriveConnectionRepository = DisconnectedDriveConnectionRepository(),
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val clock: Clock = SystemClock()
) : ViewModel() {
    val selectedStatus: StateFlow<QueueStatus> =
        savedStateHandle.getStateFlow(KEY_SELECTED_STATUS, QueueStatus.QUEUED)

    val isCreatingItem: StateFlow<Boolean> =
        savedStateHandle.getStateFlow(KEY_IS_CREATING_ITEM, false)

    val isShowingSettings: StateFlow<Boolean> =
        savedStateHandle.getStateFlow(KEY_IS_SHOWING_SETTINGS, false)

    private val selectedItemId: StateFlow<String?> =
        savedStateHandle.getStateFlow(KEY_SELECTED_ITEM_ID, null)

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
                            attachments = attachments.map { it.displayName },
                            history = history.map { it.toDisplayString(now) }
                        )
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                initialValue = null
            )

    fun selectStatus(status: QueueStatus) {
        savedStateHandle[KEY_SELECTED_STATUS] = status
    }

    fun selectItem(id: String) {
        savedStateHandle[KEY_SELECTED_ITEM_ID] = id
        savedStateHandle[KEY_IS_SHOWING_SETTINGS] = false
    }

    fun startCreate() {
        savedStateHandle[KEY_IS_CREATING_ITEM] = true
        savedStateHandle[KEY_IS_SHOWING_SETTINGS] = false
    }

    fun cancelCreate() {
        savedStateHandle[KEY_IS_CREATING_ITEM] = false
    }

    fun showSettings() {
        savedStateHandle[KEY_SELECTED_ITEM_ID] = null
        savedStateHandle[KEY_IS_CREATING_ITEM] = false
        savedStateHandle[KEY_IS_SHOWING_SETTINGS] = true
    }

    fun closeSettings() {
        savedStateHandle[KEY_IS_SHOWING_SETTINGS] = false
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
            savedStateHandle[KEY_IS_CREATING_ITEM] = false
        }
    }

    fun doneSelectedItem() {
        moveSelectedItemTo(QueueStatus.DONE)
    }

    fun dismissSelectedItem() {
        moveSelectedItemTo(QueueStatus.DISMISSED)
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

    fun backToList() {
        savedStateHandle[KEY_SELECTED_ITEM_ID] = null
        savedStateHandle[KEY_IS_SHOWING_SETTINGS] = false
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
        private const val KEY_IS_CREATING_ITEM = "isCreatingItem"
        private const val KEY_IS_SHOWING_SETTINGS = "isShowingSettings"
        private const val KEY_SELECTED_ITEM_ID = "selectedItemId"
    }
}

private fun QueueItem.toListItemUi(attachmentCount: Int) = QueueListItemUi(
    id = id,
    title = title,
    priorityLabel = priority?.name,
    dueDateLabel = dueDate?.toString(),
    attachmentSummary = attachmentCount.toAttachmentSummary()
)

private fun QueueItem.toDetailUi(attachments: List<String>, history: List<String>) = QueueItemDetailUi(
    id = id,
    title = title,
    description = description,
    dueDateLabel = dueDate?.toString(),
    attachments = attachments,
    history = history
)

private fun Int.toAttachmentSummary(): String =
    if (this == 1) "1 attachment" else "$this attachments"

private fun String?.toPriorityOrNull(): Priority? {
    val normalized = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return Priority.entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) }
}

private fun String?.toLocalDateOrNull(): LocalDate? {
    val normalized = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { LocalDate.parse(normalized) }.getOrNull()
}

internal fun HistoryEntry.toDisplayString(now: Instant): String {
    val elapsed = Duration.between(createdAt, now)
    val timeLabel = when {
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
