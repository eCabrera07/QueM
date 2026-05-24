package com.quem.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
        listOf(
            SampleQueueItem(
                id = "sample-1",
                title = "Read contract",
                description = "Review renewal terms before the next team sync.",
                priorityLabel = "High",
                dueDateLabel = "Due today",
                attachments = listOf("contract.pdf", "pricing-sheet.xlsx"),
                history = listOf("Created item", "Added contract.pdf")
            )
        )
    }
    val selectedItem = sampleItems.firstOrNull { it.id == selectedItemId }

    if (selectedItem == null) {
        QueueListScreen(
            selectedStatus = selectedStatus,
            items = sampleItems.map { it.toListItemUi() },
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
                selectedStatus = QueueStatus.DISMISSED
                selectedItemId = null
            },
            onDone = {
                selectedStatus = QueueStatus.DONE
                selectedItemId = null
            },
            onBack = { selectedItemId = null }
        )
    }
}

private data class SampleQueueItem(
    val id: String,
    val title: String,
    val description: String?,
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
