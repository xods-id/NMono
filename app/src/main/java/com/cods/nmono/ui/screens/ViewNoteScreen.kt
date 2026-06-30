package com.cods.nmono.ui.screens

import android.util.Base64
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.cods.nmono.AppViewModel
import com.cods.nmono.ui.components.ImagePreview
import java.io.File
import java.io.FileOutputStream

@OptIn(UnstableApi::class)
@ExperimentalMaterial3Api
@Composable
fun ViewNoteScreen(viewModel: AppViewModel, navController: NavController, noteId: String) {
    val notes by viewModel.notes.collectAsState()
    val note = notes.find { it.id == noteId }

    if (note == null) {
        LaunchedEffect(Unit) { navController.popBackStack() }
        return
    }

    val context = LocalContext.current
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    
    // Create temp audio file if audioBase64 exists
    LaunchedEffect(note.audioBase64) {
        note.audioBase64?.let { base64 ->
            try {
                val bytes = Base64.decode(base64, Base64.NO_WRAP)
                val tempFile = File(context.cacheDir, "temp_audio_play.m4a")
                FileOutputStream(tempFile).use { it.write(bytes) }
                
                val mediaItem = MediaItem.fromUri(tempFile.absolutePath)
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("edit/$noteId") }) {
                        Icon(Icons.Default.Edit, "Edit")
                    }
                    IconButton(onClick = { 
                        viewModel.deleteNote(noteId)
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Default.Delete, "Delete")
                    }
                }
            )
        }
    ) { padding ->
        SelectionContainer {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                if (note.imageBase64 != null) {
                    ImagePreview(
                        base64 = note.imageBase64!!,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.FillWidth
                    )
                    Spacer(Modifier.height(24.dp))
                }

                Text(
                    text = note.title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                Spacer(Modifier.height(12.dp))

                // Hashtags
                if (note.hashtags.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        note.hashtags.forEach { tag ->
                            Text(
                                text = tag,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }

                // Audio Player (Improved UI)
                if (note.audioBase64 != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Mic, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Voice Note", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    Text("Audio recorded", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        player = exoPlayer
                                        useController = true
                                        setShowNextButton(false)
                                        setShowPreviousButton(false)
                                        setShowFastForwardButton(false)
                                        setShowRewindButton(false)
                                        setShowSubtitleButton(false)
                                        setControllerAutoShow(true)
                                        controllerShowTimeoutMs = 0 // Tetap tampilkan controller
                                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.05f))
                            )
                        }
                    }
                }

                Text(
                    text = note.content,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = 28.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 100.dp)
                )
            }
        }
    }
}
