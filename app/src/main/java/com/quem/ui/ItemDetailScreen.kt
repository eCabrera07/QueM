package com.quem.ui

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
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ItemDetailScreen(
    title: String,
    description: String?,
    dueDateLabel: String?,
    attachments: List<String>,
    history: List<String>,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
    onAddTextAttachment: (title: String, text: String) -> Unit = { _, _ -> },
    onAddLinkAttachment: (title: String, url: String) -> Unit = { _, _ -> },
    driveActionsEnabled: Boolean = false,
    driveUnavailableMessage: String = "Sign in to Google Drive to attach files",
    onAttachDriveFile: () -> Unit = {},
    onAttachDriveFolder: () -> Unit = {}
) {
    var attachmentFormType by rememberSaveable { mutableStateOf<String?>(null) }
    var attachmentTitle by rememberSaveable { mutableStateOf("") }
    var attachmentValue by rememberSaveable { mutableStateOf("") }
    var driveMessage by rememberSaveable { mutableStateOf<String?>(null) }

    fun openAttachmentForm(type: String) {
        driveMessage = null
        attachmentTitle = ""
        attachmentValue = ""
        attachmentFormType = type
    }

    fun closeAttachmentForm() {
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
            TextButton(onClick = onBack) {
                Text("Back")
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
                Text(
                    text = dueDateLabel?.takeIf { it.isNotBlank() } ?: "No due date",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onDone,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Done", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Dismiss", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        item {
            DetailSectionHeader("Attachments")
        }
        item {
            if (attachmentFormType == null) {
                AttachmentEditor(
                    onAddText = { openAttachmentForm(ATTACHMENT_FORM_TEXT) },
                    onAddLink = { openAttachmentForm(ATTACHMENT_FORM_LINK) },
                    onAttachDriveFile = {
                        if (driveActionsEnabled) {
                            driveMessage = null
                            onAttachDriveFile()
                        } else {
                            driveMessage = driveUnavailableMessage
                        }
                    },
                    onAttachDriveFolder = {
                        if (driveActionsEnabled) {
                            driveMessage = null
                            onAttachDriveFolder()
                        } else {
                            driveMessage = driveUnavailableMessage
                        }
                    },
                    showDriveActions = true
                )
            } else {
                AttachmentForm(
                    type = attachmentFormType,
                    title = attachmentTitle,
                    value = attachmentValue,
                    onTitleChange = { attachmentTitle = it },
                    onValueChange = { attachmentValue = it },
                    onSave = {
                        val trimmedTitle = attachmentTitle.trim()
                        val trimmedValue = attachmentValue.trim()
                        when (attachmentFormType) {
                            ATTACHMENT_FORM_TEXT -> onAddTextAttachment(trimmedTitle, trimmedValue)
                            ATTACHMENT_FORM_LINK -> onAddLinkAttachment(trimmedTitle, trimmedValue)
                        }
                        closeAttachmentForm()
                    },
                    onCancel = { closeAttachmentForm() }
                )
            }
        }
        driveMessage?.let { message ->
            item {
                DetailEmptyText(message)
            }
        }
        if (attachments.isEmpty()) {
            item {
                DetailEmptyText("No attachments")
            }
        } else {
            items(attachments) { attachment ->
                DetailListText(attachment)
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
            items(history) { event ->
                DetailListText(event)
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private const val ATTACHMENT_FORM_TEXT = "text"
private const val ATTACHMENT_FORM_LINK = "link"

@Composable
private fun AttachmentForm(
    type: String?,
    title: String,
    value: String,
    onTitleChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Attachment title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(if (type == ATTACHMENT_FORM_LINK) "URL" else "Text") },
            modifier = Modifier.fillMaxWidth()
        )
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
private fun DetailEmptyText(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
