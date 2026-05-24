package com.quem.ui

import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.quem.core.model.QueueStatus

private val StatusTabs = listOf(
    QueueStatus.QUEUED to "Queued",
    QueueStatus.IN_PROGRESS to "In Progress",
    QueueStatus.DONE to "Done",
    QueueStatus.DISMISSED to "Dismissed"
)

@Composable
fun QueueStatusTabs(
    selectedStatus: QueueStatus,
    onStatusSelected: (QueueStatus) -> Unit
) {
    val selectedTabIndex = StatusTabs.indexOfFirst { (status, _) -> status == selectedStatus }
        .coerceAtLeast(0)

    PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
        StatusTabs.forEach { (status, label) ->
            Tab(
                selected = status == selectedStatus,
                onClick = { onStatusSelected(status) },
                text = {
                    Text(
                        text = label,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            )
        }
    }
}
