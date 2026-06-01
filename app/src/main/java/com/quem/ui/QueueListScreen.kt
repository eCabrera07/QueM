package com.quem.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quem.core.model.QueueStatus

data class QueueListItemUi(
    val id: String,
    val title: String,
    val priorityLabel: String?,
    val dueDateLabel: String?,
    val attachmentSummary: String,
    val syncIndicator: SyncIndicator? = null
)

@Composable
fun QueueListScreen(
    selectedStatus: QueueStatus,
    items: List<QueueListItemUi>,
    onStatusSelected: (QueueStatus) -> Unit,
    onItemSelected: (String) -> Unit,
    onCreateItem: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenArchive: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            QueMTopBar(
                title = "QueM",
                onSettings = onOpenSettings,
                onArchive = onOpenArchive
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateItem,
                modifier = Modifier.semantics { contentDescription = "New item" },
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            QueueStatusTabs(
                selectedStatus = selectedStatus,
                onStatusSelected = onStatusSelected
            )

            if (items.isEmpty()) {
                QueMEmptyState(
                    title = selectedStatus.emptyTitle(),
                    message = selectedStatus.emptyMessage(),
                    actionLabel = "New item",
                    onAction = onCreateItem,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
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
                    item {
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun QueueListItemCard(
    item: QueueListItemUi,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
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
            item.syncIndicator?.let { indicator ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(9.dp)
                        .background(indicator.toColor(), CircleShape)
                        .semantics { contentDescription = indicator.toLabel() }
                )
            }
        }
    }
}

private fun QueueStatus.emptyTitle(): String = when (this) {
    QueueStatus.QUEUED -> "No queued items"
    QueueStatus.IN_PROGRESS -> "Nothing in progress"
    QueueStatus.DONE -> "No done items"
    QueueStatus.DISMISSED -> "No dismissed items"
}

private fun QueueStatus.emptyMessage(): String = when (this) {
    QueueStatus.QUEUED -> "Capture the next thing you do not want to lose."
    QueueStatus.IN_PROGRESS -> "Started work will appear here."
    QueueStatus.DONE -> "Completed items will collect here."
    QueueStatus.DISMISSED -> "Dismissed items stay searchable from Archive."
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

