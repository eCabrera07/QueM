package com.quem.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quem.core.model.QueueStatus

private val StatusTabs = listOf(
    QueueStatus.QUEUED      to "Queued",
    QueueStatus.IN_PROGRESS to "In Progress",
    QueueStatus.DONE        to "Done",
    QueueStatus.DISMISSED   to "Dismissed"
)

@Composable
fun QueueStatusTabs(
    selectedStatus: QueueStatus,
    onStatusSelected: (QueueStatus) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatusTabs.forEach { (status, label) ->
            val selected = status == selectedStatus
            Surface(
                onClick = { onStatusSelected(status) },
                shape = RoundedCornerShape(50),
                color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                tonalElevation = if (selected) 0.dp else 1.dp
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
