package com.cods.nmono.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaRecorder
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.cods.nmono.AppViewModel
import com.cods.nmono.data.Note
import com.cods.nmono.ui.components.ImagePreview
import com.cods.nmono.ui.components.compressImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditNoteScreen(viewModel: AppViewModel, navController: NavController, noteId: String?) {
    val notes by viewModel.notes.collectAsState()
    val autoSaveIndicator by viewModel.autoSaveIndicator.collectAsState()
    val existingNote = if (noteId != "new") notes.find { it.id == noteId } else null

    var title by remember { mutableStateOf(existingNote?.title ?: "") }
    var content by remember { mutableStateOf(existingNote?.content ?: "") }
    var hashtagsText by remember {
        mutableStateOf(existingNote?.hashtags?.joinToString(", ") { it.removePrefix("#") } ?: "")
    }
    var imageBase64 by remember { mutableStateOf(existingNote?.imageBase64) }
    var audioBase64 by remember { mutableStateOf(existingNote?.audioBase64) }

    var isCompressingImage by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFile by remember { mutableStateOf<File?>(null) }
    
    // Voice Visualizer States
    val amplitudes = remember { mutableStateListOf<Float>() }
    var currentAmplitudo by remember { mutableStateOf(0f) }

    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()

    fun startRecording() {
        try {
            val file = File(context.cacheDir, "temp_record.m4a")
            audioFile = file
            amplitudes.clear()
            recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    setOutputFormat(MediaRecorder.OutputFormat.OGG)
                    setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                    setAudioEncodingBitRate(64000)
                } else {
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(128000)
                }
                setAudioSamplingRate(48000)
                setAudioChannels(1)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            isRecording = true
        } catch (e: Exception) {
            try {
                recorder?.release()
                val file = File(context.cacheDir, "temp_record.m4a")
                audioFile = file
                recorder = MediaRecorder().apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(128000)
                    setAudioSamplingRate(44100)
                    setOutputFile(file.absolutePath)
                    prepare()
                    start()
                }
                isRecording = true
            } catch (inner: Exception) {
                viewModel.showToast("Microphone busy or unavailable", com.cods.nmono.data.ToastType.ERROR)
                inner.printStackTrace()
            }
        }
    }

    // Effect to poll amplitude
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording) {
                val amp = recorder?.maxAmplitude?.toFloat() ?: 0f
                currentAmplitudo = (amp / 32767f).coerceIn(0f, 1f)
                amplitudes.add(currentAmplitudo)
                if (amplitudes.size > 40) amplitudes.removeAt(0)
                delay(100)
            }
        } else {
            currentAmplitudo = 0f
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startRecording()
        } else {
            viewModel.showToast("Microphone permission denied", com.cods.nmono.data.ToastType.ERROR)
        }
    }

    fun stopRecording() {
        try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            isRecording = false
            
            audioFile?.let { file ->
                val bytes = file.readBytes()
                audioBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun navigateBack() {
        if (isRecording) stopRecording()
        if (!navController.popBackStack()) {
            activity?.finish()
        }
    }

    BackHandler { navigateBack() }

    val note = remember {
        mutableStateOf(existingNote ?: Note(
            id = UUID.randomUUID().toString(),
            title = "", content = "", hashtags = emptyList(),
            imageBase64 = null, createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        ))
    }

    LaunchedEffect(title, content, hashtagsText, imageBase64, audioBase64) {
        note.value = note.value.copy(
            title = title,
            content = content,
            hashtags = hashtagsText.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { if (!it.startsWith("#")) "#$it" else it },
            imageBase64 = imageBase64,
            audioBase64 = audioBase64,
            updatedAt = System.currentTimeMillis()
        )
        if (existingNote != null || title.isNotEmpty() || content.isNotEmpty()) {
            viewModel.triggerAutoSave(note.value)
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            isCompressingImage = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(it)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    if (bitmap != null) {
                        val compressed = compressImage(bitmap)
                        withContext(Dispatchers.Main) {
                            imageBase64 = compressed
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    withContext(Dispatchers.Main) {
                        isCompressingImage = false
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (existingNote != null) "Edit Note" else "New Note",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navigateBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    AnimatedVisibility(visible = autoSaveIndicator, enter = fadeIn(), exit = fadeOut()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 4.dp)) {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Saved", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                        }
                    }

                    IconButton(onClick = { imagePicker.launch("image/*") }) {
                        Icon(Icons.Default.Image, contentDescription = "Add Image")
                    }

                    TextButton(onClick = {
                        viewModel.saveNote(note.value)
                        navigateBack()
                    }) {
                        Text("Done")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Column {
                    // Hashtags Row (Separated)
                    Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Tag, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            BasicTextField(
                                value = hashtagsText,
                                onValueChange = { hashtagsText = it },
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                decorationBox = { innerTextField ->
                                    if (hashtagsText.isEmpty()) Text("design, work, ideas", style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)))
                                    innerTextField()
                                }
                            )
                        }
                    }
                    
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    // Voice Control Area (Optimized)
                    Row(
                        modifier = Modifier.fillMaxWidth().height(80.dp).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (isRecording) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Visualizer kaya WhatsApp
                                    Row(
                                        modifier = Modifier.height(30.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        amplitudes.forEach { amp ->
                                            val height by animateDpAsState(
                                                targetValue = (amp * 30).coerceAtLeast(3f).dp,
                                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                                label = ""
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .width(2.dp)
                                                    .height(height)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text("Recording...", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                            } else if (audioBase64 != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Mic, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Voice Note", style = MaterialTheme.typography.labelLarge)
                                            Spacer(Modifier.width(4.dp))
                                            IconButton(onClick = { audioBase64 = null }, modifier = Modifier.size(24.dp)) {
                                                Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text("Tap mic to record voice", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            }
                        }

                        FloatingActionButton(
                            onClick = {
                                if (isRecording) {
                                    stopRecording()
                                } else {
                                    val status = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                                    if (status == PackageManager.PERMISSION_GRANTED) {
                                        startRecording()
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            contentColor = if (isRecording) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                            shape = CircleShape,
                            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = "Record"
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            BasicTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = false,
                textStyle = MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    Box {
                        if (title.isEmpty()) {
                            Text("Judul Catatan", style = MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)))
                        }
                        innerTextField()
                    }
                }
            )

            Spacer(Modifier.height(24.dp))

            if (isCompressingImage) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.height(24.dp))
            } else if (imageBase64 != null) {
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))) {
                    ImagePreview(base64 = imageBase64!!, modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp))
                    IconButton(
                        onClick = { imageBase64 = null },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape).size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, "Remove image", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            BasicTextField(
                value = content,
                onValueChange = { content = it },
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    lineHeight = 28.sp,
                    fontFamily = MaterialTheme.typography.bodyLarge.fontFamily
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 600.dp),
                decorationBox = { innerTextField ->
                    Box {
                        if (content.isEmpty()) {
                            Text("Mulai menulis...", style = TextStyle(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f), fontSize = 16.sp, lineHeight = 28.sp))
                        }
                        innerTextField()
                    }
                }
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}
