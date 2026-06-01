package com.quem.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun ArchiveSearchScreen(
    query: String,
    results: List<QueueListItemUi>,
    onQueryChange: (String) -> Unit,
    onItemSelected: (String) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = { QueMTopBar(title = "Archive", onBack = onBack) }
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
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                )
            }

            if (results.isEmpty()) {
                item {
                    QueMEmptyState(
                        title = if (query.isBlank()) "Nothing archived yet" else "No results for \"$query\"",
                        message = if (query.isBlank()) {
                            "Done and dismissed items will appear here."
                        } else {
                            "Try a different title or attachment keyword."
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                items(results, key = { it.id }) { item ->
                    QueueListItemCard(
                        item = item,
                        onClick = { onItemSelected(item.id) }
                    )
                }
            }
        }
    }
}
