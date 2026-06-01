package com.quem.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quem.core.model.QueueStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueMTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onArchive: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = {
            if (onEdit != null) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit")
                }
            }
            if (onArchive != null) {
                IconButton(onClick = onArchive) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Archive")
                }
            }
            if (onSettings != null) {
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
        }
    )
}

@Composable
fun QueMEmptyState(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) {
                Text(actionLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun PriorityChip(label: String, modifier: Modifier = Modifier) {
    AssistChip(
        onClick = {},
        label = { Text(label.toChipLabel(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier
    )
}

@Composable
fun StatusChip(status: QueueStatus, modifier: Modifier = Modifier) {
    AssistChip(
        onClick = {},
        label = { Text(status.toUiLabel(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier
    )
}

@Composable
fun SyncStatusChip(indicator: SyncIndicator, modifier: Modifier = Modifier) {
    AssistChip(
        onClick = {},
        label = { Text(indicator.toLabel(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier
    )
}

@Composable
fun BottomActionBar(
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    primaryEnabled: Boolean = true
) {
    Surface(shadowElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onSecondary,
                modifier = Modifier.weight(1f)
            ) {
                Text(secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Button(
                onClick = onPrimary,
                modifier = Modifier.weight(1f),
                enabled = primaryEnabled
            ) {
                Text(primaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

fun QueueStatus.toUiLabel(): String = when (this) {
    QueueStatus.QUEUED -> "Queued"
    QueueStatus.IN_PROGRESS -> "In Progress"
    QueueStatus.DONE -> "Done"
    QueueStatus.DISMISSED -> "Dismissed"
}

private fun String.toChipLabel(): String =
    lowercase().replaceFirstChar { it.uppercase() }
