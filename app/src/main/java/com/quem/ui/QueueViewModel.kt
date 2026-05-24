package com.quem.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quem.core.model.QueueItem
import com.quem.core.model.QueueStatus
import com.quem.data.repository.QueueRepository
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
import kotlinx.coroutines.launch

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
    private val repository: QueueRepository
) : ViewModel() {
    private val _selectedStatus = MutableStateFlow(QueueStatus.QUEUED)
    val selectedStatus: StateFlow<QueueStatus> = _selectedStatus.asStateFlow()

    private val _isCreatingItem = MutableStateFlow(false)
    val isCreatingItem: StateFlow<Boolean> = _isCreatingItem.asStateFlow()

    private val selectedItemId = MutableStateFlow<String?>(null)

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
                        repository.observeAttachments(id)
                    ) { item, attachments ->
                        item?.toDetailUi(attachments = attachments.map { it.displayName })
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                initialValue = null
            )

    fun selectStatus(status: QueueStatus) {
        _selectedStatus.value = status
    }

    fun selectItem(id: String) {
        selectedItemId.value = id
    }

    fun startCreate() {
        _isCreatingItem.value = true
    }

    fun cancelCreate() {
        _isCreatingItem.value = false
    }

    fun createItem(title: String, description: String?) {
        viewModelScope.launch {
            val created = repository.createItem(title = title, description = description)
            _selectedStatus.value = QueueStatus.QUEUED
            selectedItemId.value = created.id
            _isCreatingItem.value = false
        }
    }

    fun doneSelectedItem() {
        moveSelectedItemTo(QueueStatus.DONE)
    }

    fun dismissSelectedItem() {
        moveSelectedItemTo(QueueStatus.DISMISSED)
    }

    fun backToList() {
        selectedItemId.value = null
    }

    private fun moveSelectedItemTo(status: QueueStatus) {
        val id = selectedItemId.value ?: return
        viewModelScope.launch {
            repository.changeStatus(id = id, status = status)
            _selectedStatus.value = status
            backToList()
        }
    }

    companion object {
        fun factory(repository: QueueRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(QueueViewModel::class.java)) {
                        return QueueViewModel(repository) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}

private fun QueueItem.toListItemUi(attachmentCount: Int) = QueueListItemUi(
    id = id,
    title = title,
    priorityLabel = priority?.name,
    dueDateLabel = dueDate?.toString(),
    attachmentSummary = attachmentCount.toAttachmentSummary()
)

private fun QueueItem.toDetailUi(attachments: List<String>) = QueueItemDetailUi(
    id = id,
    title = title,
    description = description,
    dueDateLabel = dueDate?.toString(),
    attachments = attachments,
    history = emptyList()
)

private fun Int.toAttachmentSummary(): String =
    if (this == 1) "1 attachment" else "$this attachments"
