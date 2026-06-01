package com.quem.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.quem.core.model.Priority
import com.quem.core.model.QueueStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun PriorityDropdown(
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected?.replaceFirstChar { it.uppercase() } ?: "None",
            onValueChange = {},
            readOnly = true,
            label = { Text("Priority") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf<String?>(null, Priority.LOW.name, Priority.MEDIUM.name, Priority.HIGH.name).forEach { option ->
                DropdownMenuItem(
                    text = { Text(option?.replaceFirstChar { it.uppercase() } ?: "None") },
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
    }
}

@Composable
fun DueDatePicker(
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }

    val initialMillis = selected
        ?.runCatching { LocalDate.parse(this).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli() }
        ?.getOrNull()

    OutlinedTextField(
        value = selected ?: "None",
        onValueChange = {},
        readOnly = true,
        label = { Text("Due date (optional)") },
        trailingIcon = {
            TextButton(onClick = { showPicker = true }) { Text("Pick") }
        },
        modifier = modifier.fillMaxWidth()
    )

    if (showPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        val date = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.of("UTC"))
                            .toLocalDate()
                            .toString()
                        onSelect(date)
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { onSelect(null); showPicker = false }) { Text("Clear") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
fun StatusDropdown(
    current: QueueStatus,
    onChange: (QueueStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = current.label(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Status") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            QueueStatus.entries.forEach { status ->
                DropdownMenuItem(
                    text = { Text(status.label()) },
                    onClick = { onChange(status); expanded = false }
                )
            }
        }
    }
}

private fun QueueStatus.label(): String = when (this) {
    QueueStatus.QUEUED      -> "Queued"
    QueueStatus.IN_PROGRESS -> "In Progress"
    QueueStatus.DONE        -> "Done"
    QueueStatus.DISMISSED   -> "Dismissed"
}
