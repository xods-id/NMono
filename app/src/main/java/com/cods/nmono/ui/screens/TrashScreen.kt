package com.cods.nmono.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.cods.nmono.AppViewModel
import com.cods.nmono.data.Note
import com.cods.nmono.ui.components.formatDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(viewModel: AppViewModel, navController: NavController) {
    val notes by viewModel.notes.collectAsState()
    val trashNotes = viewModel.getTrashNotes()
    var showConfirmDialog by remember { mutableStateOf(false) }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Empty Trash") },
            text = { Text("Permanently delete ${trashNotes.size} notes? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { 
                        viewModel.emptyTrash()
                        showConfirmDialog = false 
                        viewModel.showToast("Trash emptied", com.cods.nmono.data.ToastType.ERROR)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trash (${trashNotes.size})") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (trashNotes.isNotEmpty()) {
                        IconButton(onClick = { showConfirmDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Empty Trash")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (trashNotes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Trash is empty")
                    Text("Deleted notes appear here for 30 days", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(trashNotes, key = { it.id }) { note ->
                    Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = note.title.ifEmpty { "Untitled" }, style = MaterialTheme.typography.titleMedium)
                                Text(text = note.content.take(50), style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = "${formatDate(note.deletedAt!!)} · ${daysLeft(note.deletedAt!!)} days left",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            IconButton(onClick = { viewModel.restoreNote(note.id) }) {
                                Icon(Icons.Default.RestoreFromTrash, contentDescription = "Restore")
                            }
                            IconButton(onClick = { 
                                viewModel.permadeleteNote(note.id)
                                viewModel.showToast("Note permanently deleted", com.cods.nmono.data.ToastType.ERROR)
                            }) {
                                Icon(Icons.Default.DeleteForever, contentDescription = "Delete Permanently", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

fun daysLeft(deletedAt: Long): Int {
    val thirtyDays = 30L * 24 * 60 * 60 * 1000
    val elapsed = System.currentTimeMillis() - deletedAt
    return ((thirtyDays - elapsed) / (24 * 60 * 60 * 1000)).toInt().coerceAtLeast(0)
}