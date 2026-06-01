package com.quem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.quem.core.model.QueueStatus
import com.quem.drive.DriveUrlExtractor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ItemDetailScreen(
    title: String,
    description: String?,
    dueDateLabel: String?,
    attachments: List<AttachmentUi>,
    history: List<HistoryEntryUi>,
    priorityLabel: String? = null,
    syncIndicator: SyncIndicator? = null,
    currentStatus: QueueStatus = QueueStatus.QUEUED,
    onStatusChange: (QueueStatus) -> Unit = {},
    onBack: () -> Unit,
    onEdit: () -> Unit = {},
    onAddTextAttachment: (title: String, text: String) -> Unit = { _, _ -> },
    onAddLinkAttachment: (title: String, url: String) -> Unit = { _, _ -> },
    onAttachDriveFile: (title: String, driveFileId: String, mimeType: String?) -> Unit = { _, _, _ -> },
    onAttachDriveFolder: (title: String, driveFolderId: String) -> Unit = { _, _ -> },
    onDeleteAttachment: (attachmentId: String) -> Unit = {},
    onRenameAttachment: (attachmentId: String, newTitle: String) -> Unit = { _, _ -> },
    onDeleteHistoryEntry: (historyEntryId: String) -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current
    var attachmentFormType by rememberSaveable { mutableStateOf<String?>(null) }
    var attachmentTitle by rememberSaveable { mutableStateOf("") }
    var attachmentValue by rememberSaveable { mutableStateOf("") }
    var driveUrlError by rememberSaveable { mutableStateOf(false) }

    fun openAttachmentForm(type: String) {
        driveUrlError = false
        attachmentTitle = ""
        attachmentValue = ""
        attachmentFormType = type
    }

    fun closeAttachmentForm() {
        driveUrlError = false
        attachmentTitle = ""
        attachmentValue = ""
        attachmentFormType = null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onBack) { Text("Back") }
                TextButton(onClick = onEdit) { Text("Edit") }
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )
                description?.takeIf { it.isNotBlank() }?.let { body ->
                    Text(
                        text = body,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                priorityLabel?.let { label ->
                    Text(
                        text = label,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    text = dueDateLabel?.takeIf { it.isNotBlank() } ?: "No due date",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium
                )
                syncIndicator?.let { indicator ->
                    val color = indicator.toColor()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(color, CircleShape)
                        )
                        Text(
                            text = indicator.toLabel(),
                            style = MaterialTheme.typography.labelSmall,
                            color = color
                        )
                    }
                }
            }
        }

        item {
            StatusDropdown(
                current = currentStatus,
                onChange = onStatusChange
            )
        }

        item {
            DetailSectionHeader("Attachments")
        }
        item {
            if (attachmentFormType == null) {
                AttachmentEditor(
                    onAddText = { openAttachmentForm(ATTACHMENT_FORM_TEXT) },
                    onAddLink = { openAttachmentForm(ATTACHMENT_FORM_LINK) },
                    onAttachDriveFile = { openAttachmentForm(ATTACHMENT_FORM_DRIVE_FILE) },
                    onAttachDriveFolder = { openAttachmentForm(ATTACHMENT_FORM_DRIVE_FOLDER) },
                    showDriveActions = true
                )
            } else {
                AttachmentForm(
                    type = attachmentFormType,
                    title = attachmentTitle,
                    value = attachmentValue,
                    showError = driveUrlError,
                    onTitleChange = { attachmentTitle = it },
                    onValueChange = { driveUrlError = false; attachmentValue = it },
                    onSave = {
                        val trimmedTitle = attachmentTitle.trim()
                        val trimmedValue = attachmentValue.trim()
                        when (attachmentFormType) {
                            ATTACHMENT_FORM_TEXT -> {
                                onAddTextAttachment(trimmedTitle, trimmedValue)
                                closeAttachmentForm()
                            }
                            ATTACHMENT_FORM_LINK -> {
                                onAddLinkAttachment(trimmedTitle, trimmedValue)
                                closeAttachmentForm()
                            }
                            ATTACHMENT_FORM_DRIVE_FILE -> {
                                val id = DriveUrlExtractor.extractFileId(trimmedValue)
                                if (id != null) {
                                    onAttachDriveFile(trimmedTitle.ifBlank { "Drive file" }, id, null)
                                    closeAttachmentForm()
                                } else {
                                    driveUrlError = true
                                }
                            }
                            ATTACHMENT_FORM_DRIVE_FOLDER -> {
                                val id = DriveUrlExtractor.extractFolderId(trimmedValue)
                                if (id != null) {
                                    onAttachDriveFolder(trimmedTitle.ifBlank { "Drive folder" }, id)
                                    closeAttachmentForm()
                                } else {
                                    driveUrlError = true
                                }
                            }
                        }
                    },
                    onCancel = { closeAttachmentForm() }
                )
            }
        }
        if (attachments.isEmpty()) {
            item {
                DetailEmptyText("No attachments")
            }
        } else {
            items(attachments) { attachment ->
                val url = when {
                    attachment.isLink -> attachment.url?.toAbsoluteUrl()
                    attachment.isDriveFile || attachment.isDriveFolder ->
                        attachment.driveFileId?.let { "https://drive.google.com/open?id=$it" }
                    else -> null
                }
                DeletableRow(
                    onDelete = { onDeleteAttachment(attachment.id) },
                    onRename = { newTitle -> onRenameAttachment(attachment.id, newTitle) },
                    currentName = attachment.displayName
                ) {
                    if (url != null) {
                        AttachmentLinkText(
                            text = attachment.displayName,
                            onClick = { uriHandler.openUri(url) }
                        )
                    } else {
                        DetailListText(attachment.displayName)
                    }
                }
            }
        }

        item {
            DetailSectionHeader("History")
        }
        if (history.isEmpty()) {
            item {
                DetailEmptyText("No history")
            }
        } else {
            items(history) { entry ->
                DeletableRow(
                    onDelete = { onDeleteHistoryEntry(entry.id) }
                ) {
                    DetailListText(entry.displayText)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

internal const val ATTACHMENT_FORM_TEXT        = "text"
internal const val ATTACHMENT_FORM_LINK        = "link"
internal const val ATTACHMENT_FORM_DRIVE_FILE   = "drive_file"
internal const val ATTACHMENT_FORM_DRIVE_FOLDER = "drive_folder"

@Composable
internal fun AttachmentForm(
    type: String?,
    title: String,
    value: String,
    showError: Boolean = false,
    onTitleChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val isDriveType = type == ATTACHMENT_FORM_DRIVE_FILE || type == ATTACHMENT_FORM_DRIVE_FOLDER
    val valueLabel = when (type) {
        ATTACHMENT_FORM_LINK        -> "URL"
        ATTACHMENT_FORM_DRIVE_FILE  -> "Drive file URL"
        ATTACHMENT_FORM_DRIVE_FOLDER -> "Drive folder URL"
        else                        -> "Text"
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text(if (isDriveType) "Label (optional)" else "Attachment title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(valueLabel) },
            modifier = Modifier.fillMaxWidth(),
            isError = showError,
            singleLine = isDriveType
        )
        if (showError) {
            Text(
                text = "Couldn't find a Drive ID in that URL. Paste the full Drive link.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f)
            ) {
                Text("Save", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun DetailSectionHeader(text: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        HorizontalDivider(
            thickness = DividerDefaults.Thickness,
            color = DividerDefaults.color
        )
    }
}

@Composable
private fun DetailListText(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
internal fun DeletableRow(
    onDelete: () -> Unit,
    onRename: ((String) -> Unit)? = null,
    currentName: String = "",
    content: @Composable () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf(currentName) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(modifier = Modifier.weight(1f)) { content() }
        Box(modifier = Modifier.wrapContentSize()) {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Options")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                if (onRename != null) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = { menuExpanded = false; renameValue = currentName; showRenameDialog = true }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = { menuExpanded = false; onDelete() }
                )
            }
        }
    }

    if (showRenameDialog && onRename != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { onRename(renameValue); showRenameDialog = false },
                    enabled = renameValue.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private fun String.toAbsoluteUrl(): String =
    if (startsWith("http://") || startsWith("https://")) this else "https://$this"

@Composable
private fun AttachmentLinkText(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline
    )
}

@Composable
private fun DetailEmptyText(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

