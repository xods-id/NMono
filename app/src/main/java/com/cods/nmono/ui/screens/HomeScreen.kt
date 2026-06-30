package com.cods.nmono.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.cods.nmono.AppViewModel
import com.cods.nmono.data.SortType
import com.cods.nmono.ui.components.NoteCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: AppViewModel, navController: NavController) {
    val notes by viewModel.notes.collectAsState()
    val isLocked by viewModel.isLocked.collectAsState()
    val sortType by viewModel.sortType.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val activeFilter by viewModel.activeFilter.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    
    val usedTags = viewModel.getAllUsedTags()
    
    var showSearch by remember { mutableStateOf(false) }
    var selectedNotes by remember { mutableStateOf(setOf<String>()) }
    var showExitDialog by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val activeNotes = notes.filter { it.deletedAt == null }
    val filteredNotes = if (searchQuery.isNotEmpty() || activeFilter != null) {
        viewModel.getFilteredNotes().filter { it.deletedAt == null }
    } else activeNotes

    BackHandler {
        if (selectedNotes.isNotEmpty()) selectedNotes = emptySet()
        else if (showSearch) {
            showSearch = false
            viewModel.setSearchQuery("")
        } else showExitDialog = true
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit NMono?") },
            text = { Text("Are you sure you want to exit?") },
            confirmButton = {
                TextButton(onClick = { android.os.Process.killProcess(android.os.Process.myPid()) }) { Text("Exit") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("Cancel") }
            }
        )
    }
    
    if (isLocked) {
        LockScreen(viewModel, navController)
        return
    }

    Scaffold(
        topBar = {
            Crossfade(targetState = selectedNotes.isEmpty(), label = "topbar") { isSelectionEmpty ->
                if (isSelectionEmpty) {
                    TopAppBar(
                        title = { Text("NMono") },
                        actions = {
                            IconButton(onClick = { showSearch = true }) { Icon(Icons.Default.Search, null) }
                            IconButton(onClick = { viewModel.toggleTheme() }) {
                                Icon(when (themeMode) {
                                    com.cods.nmono.AppThemeMode.LIGHT -> Icons.Default.LightMode
                                    com.cods.nmono.AppThemeMode.DARK -> Icons.Default.DarkMode
                                    else -> Icons.Default.Palette
                                }, null)
                            }
                            IconButton(onClick = { navController.navigate("settings") }) { Icon(Icons.Default.Settings, null) }
                        }
                    )
                } else {
                    TopAppBar(
                        title = { Text("${selectedNotes.size} selected") },
                        navigationIcon = {
                            IconButton(onClick = { selectedNotes = emptySet() }) { Icon(Icons.Default.Close, null) }
                        },
                        actions = {
                            IconButton(onClick = {
                                if (selectedNotes.size == filteredNotes.size) selectedNotes = emptySet()
                                else selectedNotes = filteredNotes.map { it.id }.toSet()
                            }) { Icon(if (selectedNotes.size == filteredNotes.size) Icons.Default.Deselect else Icons.Default.SelectAll, null) }
                            IconButton(onClick = {
                                viewModel.deleteNotes(selectedNotes.toList())
                                selectedNotes = emptySet()
                            }) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate("edit/new") }) { Icon(Icons.Default.Add, null) }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("Home") }, selected = true, onClick = { }
                )
                NavigationBarItem(
                    icon = { 
                        BadgedBox(badge = {
                            val trashCount = notes.count { it.deletedAt != null }
                            if (trashCount > 0) Badge { Text(trashCount.toString()) }
                        }) { Icon(Icons.Default.Delete, null) }
                    },
                    label = { Text("Trash") }, selected = false, onClick = { navController.navigate("trash") }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search notes...") },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(focusRequester),
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        IconButton(onClick = { 
                            showSearch = false
                            viewModel.setSearchQuery("")
                        }) { Icon(Icons.Default.Close, null) }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
            }
            
            if (usedTags.isNotEmpty()) {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp)) {
                    usedTags.forEach { (tag, count) ->
                        FilterChip(
                            selected = activeFilter == tag,
                            onClick = { viewModel.setActiveFilter(if (activeFilter == tag) null else tag) },
                            label = { Text("#$tag ($count)") },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }
            }
            
            LazyVerticalGrid(
                columns = GridCells.Adaptive(170.dp),
                modifier = Modifier.fillMaxSize().imePadding(),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(filteredNotes, key = { it.id }) { note ->
                    val isSelected = selectedNotes.contains(note.id)
                    NoteCard(
                        note = note,
                        isSelected = isSelected,
                        onClick = {
                            if (selectedNotes.isEmpty()) navController.navigate("view/${note.id}")
                            else selectedNotes = if (isSelected) selectedNotes - note.id else selectedNotes + note.id
                        },
                        onLongClick = {
                            selectedNotes = if (isSelected) selectedNotes - note.id else selectedNotes + note.id
                        },
                        onDelete = { viewModel.deleteNote(note.id) }
                    )
                }
            }
        }
    }
}
