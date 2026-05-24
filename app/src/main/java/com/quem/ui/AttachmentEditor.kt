package com.quem.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun AttachmentEditor(
    onAddText: () -> Unit,
    onAddLink: () -> Unit,
    onAttachDriveFile: () -> Unit,
    onAttachDriveFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AttachmentButton(text = "Text", onClick = onAddText)
        AttachmentButton(text = "Link", onClick = onAddLink)
        AttachmentButton(text = "Drive file", onClick = onAttachDriveFile)
        AttachmentButton(text = "Drive folder", onClick = onAttachDriveFolder)
    }
}

@Composable
private fun AttachmentButton(
    text: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
