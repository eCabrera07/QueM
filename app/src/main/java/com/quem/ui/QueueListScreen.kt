package com.quem.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quem.core.model.QueueStatus

data class QueueListItemUi(
    val id: String,
    val title: String,
    val priorityLabel: String?,
    val dueDateLabel: String?,
    val attachmentSummary: String
)

@Composable
fun QueueListScreen(
    selectedStatus: QueueStatus,
    items: List<QueueListItemUi>,
    onStatusSelected: (QueueStatus) -> Unit,
    onItemSelected: (String) -> Unit,
    onCreateItem: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "QueM",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Button(onClick = onCreateItem) {
                Text("New")
            }
        }

        QueueStatusTabs(
            selectedStatus = selectedStatus,
            onStatusSelected = onStatusSelected
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { it.id }) { item ->
                QueueListItemCard(
                    item = item,
                    onClick = { onItemSelected(item.id) }
                )
            }
        }
    }
}

@Composable
private fun QueueListItemCard(
    item: QueueListItemUi,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item.priorityLabel?.let { label ->
                    QueueItemMetadataText(label)
                }
                item.dueDateLabel?.let { label ->
                    QueueItemMetadataText(label)
                }
                QueueItemMetadataText(item.attachmentSummary)
            }
        }
    }
}

@Composable
private fun QueueItemMetadataText(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
