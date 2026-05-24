package com.quem.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.quem.core.model.QueueStatus
import com.quem.ui.QueueListItemUi
import com.quem.ui.QueueListScreen

@Composable
fun QueMApp() {
    var selectedStatus by remember { mutableStateOf(QueueStatus.QUEUED) }
    val sampleItems = remember {
        listOf(
            QueueListItemUi(
                id = "sample-1",
                title = "Read contract",
                priorityLabel = "High",
                dueDateLabel = "Due today",
                attachmentSummary = "2 attachments"
            )
        )
    }

    QueueListScreen(
        selectedStatus = selectedStatus,
        items = sampleItems,
        onStatusSelected = { selectedStatus = it },
        onItemSelected = {},
        onCreateItem = {}
    )
}
