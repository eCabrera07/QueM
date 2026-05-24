package com.quem.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.quem.core.model.QueueStatus
import com.quem.ui.ItemDetailScreen
import com.quem.ui.QueueListItemUi
import com.quem.ui.QueueListScreen

@Composable
fun QueMApp() {
    var selectedStatus by rememberSaveable { mutableStateOf(QueueStatus.QUEUED) }
    var selectedItemId by rememberSaveable { mutableStateOf<String?>(null) }
    val sampleItems = remember {
        mutableStateListOf(
            SampleQueueItem(
                id = "sample-1",
                title = "Read contract",
                description = "Review renewal terms before the next team sync.",
                status = QueueStatus.QUEUED,
                priorityLabel = "High",
                dueDateLabel = "Due today",
                attachments = listOf("contract.pdf", "pricing-sheet.xlsx"),
                history = listOf("Created item", "Added contract.pdf")
            )
        )
    }
    val selectedItem = sampleItems.firstOrNull { it.id == selectedItemId }
    fun updateSelectedItemStatus(status: QueueStatus) {
        val itemId = selectedItemId ?: return
        val itemIndex = sampleItems.indexOfFirst { it.id == itemId }
        if (itemIndex >= 0) {
            sampleItems[itemIndex] = sampleItems[itemIndex].copy(status = status)
        }
        selectedStatus = status
        selectedItemId = null
    }

    if (selectedItem == null) {
        QueueListScreen(
            selectedStatus = selectedStatus,
            items = sampleItems
                .filter { it.status == selectedStatus }
                .map { it.toListItemUi() },
            onStatusSelected = { selectedStatus = it },
            onItemSelected = { selectedItemId = it },
            onCreateItem = {}
        )
    } else {
        ItemDetailScreen(
            title = selectedItem.title,
            description = selectedItem.description,
            dueDateLabel = selectedItem.dueDateLabel,
            attachments = selectedItem.attachments,
            history = selectedItem.history,
            onDismiss = {
                updateSelectedItemStatus(QueueStatus.DISMISSED)
            },
            onDone = {
                updateSelectedItemStatus(QueueStatus.DONE)
            },
            onBack = { selectedItemId = null }
        )
    }
}

private data class SampleQueueItem(
    val id: String,
    val title: String,
    val description: String?,
    val status: QueueStatus,
    val priorityLabel: String?,
    val dueDateLabel: String?,
    val attachments: List<String>,
    val history: List<String>
)

private fun SampleQueueItem.toListItemUi(): QueueListItemUi =
    QueueListItemUi(
        id = id,
        title = title,
        priorityLabel = priorityLabel,
        dueDateLabel = dueDateLabel,
        attachmentSummary = "${attachments.size} attachments"
    )
