package com.quem.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CreateItemScreen(
    onSave: (title: String, description: String?, priority: String?, dueDate: String?) -> Unit,
    onCancel: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableStateOf<String?>(null) }
    var dueDate by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            QueMTopBar(title = "Create item", onBack = onCancel)
        },
        bottomBar = {
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
        }
    }
}
