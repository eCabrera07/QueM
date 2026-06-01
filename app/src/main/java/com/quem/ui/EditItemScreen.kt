package com.quem.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.quem.drive.DriveUrlExtractor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

@Composable
fun EditItemScreen(
    initialTitle: String,
    initialDescription: String,
    initialPriority: String,
    initialDueDate: String,
    attachments: List<AttachmentUi> = emptyList(),
    onSave: (title: String, description: String?, priority: String?, dueDate: String?) -> Unit,
    onCancel: () -> Unit,
    onAddTextAttachment: (title: String, text: String) -> Unit = { _, _ -> },
    onAddLinkAttachment: (title: String, url: String) -> Unit = { _, _ -> },
    onAttachDriveFile: (title: String, driveFileId: String, mimeType: String?) -> Unit = { _, _, _ -> },
    onAttachDriveFolder: (title: String, driveFolderId: String) -> Unit = { _, _ -> },
    onDeleteAttachment: (attachmentId: String) -> Unit = {},
    onRenameAttachment: (attachmentId: String, newTitle: String) -> Unit = { _, _ -> }
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var description by rememberSaveable { mutableStateOf(initialDescription) }
    var priority by rememberSaveable { mutableStateOf(initialPriority.takeUnless { it.isBlank() }) }
    var dueDate by rememberSaveable { mutableStateOf(initialDueDate.takeUnless { it.isBlank() }) }
    var attachmentFormType by rememberSaveable { mutableStateOf<String?>(null) }
    var attachmentTitle by rememberSaveable { mutableStateOf("") }
    var attachmentValue by rememberSaveable { mutableStateOf("") }
    var driveUrlError by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    fun openForm(type: String) { driveUrlError = false; attachmentTitle = ""; attachmentValue = ""; attachmentFormType = type }
    fun closeForm() { driveUrlError = false; attachmentTitle = ""; attachmentValue = ""; attachmentFormType = null }

    Scaffold(
        topBar = {
            QueMTopBar(title = "Edit item", onBack = onCancel)
        },
        bottomBar = {
            if (attachmentFormType == null) {
                BottomActionBar(
                    primaryLabel = "Save",
                    onPrimary = {
                        onSave(
                            title.trim(),
                            description.trim().takeUnless { it.isBlank() },
                            priority,
                            dueDate
                        )
                    },
                    secondaryLabel = "Cancel",
                    onSecondary = onCancel,
                    primaryEnabled = title.isNotBlank()
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Title") },
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Description") },
                    minLines = 3
                )
            }

            item {
                PriorityDropdown(
                    selected = priority,
                    onSelect = { priority = it }
                )
            }

            item {
                DueDatePicker(
                    selected = dueDate,
                    onSelect = { dueDate = it }
                )
            }

            item {
                Text("Attachments", style = MaterialTheme.typography.titleSmall)
            }

            if (attachments.isEmpty() && attachmentFormType == null) {
                item { Text("No attachments", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            items(attachments) { attachment ->
                val url = when {
                    attachment.isLink -> attachment.url?.let { if (it.startsWith("http")) it else "https://$it" }
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
                        Text(
                            text = attachment.displayName,
                            modifier = Modifier.fillMaxWidth().clickable { uriHandler.openUri(url) }.padding(vertical = 4.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(text = attachment.displayName, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item {
                if (attachmentFormType == null) {
                    AttachmentEditor(
                        onAddText = { openForm("text") },
                        onAddLink = { openForm("link") },
                        onAttachDriveFile = { openForm("drive_file") },
                        onAttachDriveFolder = { openForm("drive_folder") },
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
                            val t = attachmentTitle.trim(); val v = attachmentValue.trim()
                            when (attachmentFormType) {
                                "text" -> { onAddTextAttachment(t, v); closeForm() }
                                "link" -> { onAddLinkAttachment(t, v); closeForm() }
                                "drive_file" -> {
                                    val id = DriveUrlExtractor.extractFileId(v)
                                    if (id != null) { onAttachDriveFile(t.ifBlank { "Drive file" }, id, null); closeForm() }
                                    else driveUrlError = true
                                }
                                "drive_folder" -> {
                                    val id = DriveUrlExtractor.extractFolderId(v)
                                    if (id != null) { onAttachDriveFolder(t.ifBlank { "Drive folder" }, id); closeForm() }
                                    else driveUrlError = true
                                }
                            }
                        },
                        onCancel = { closeForm() }
                    )
                }
            }
        }
    }
}
