package com.cods.nmono.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cods.nmono.data.Note
import com.cods.nmono.data.ToastData
import com.cods.nmono.data.ToastType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@Composable
fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    isSelected: Boolean = false,
    isCustomSort: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (note.imageBase64 != null) {
                ImagePreview(base64 = note.imageBase64!!, modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(10.dp)))
                Spacer(Modifier.height(8.dp))
            }
            
            Text(
                text = note.title.ifEmpty { "Untitled" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            
            ChecklistContent(content = note.content, maxLines = 6)
            
            if (note.hashtags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    note.hashtags.forEach { tag ->
                        SuggestionChip(
                            onClick = { },
                            label = { Text(tag, fontSize = 11.sp) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDate(note.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isSelected) {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun ChecklistContent(content: String, maxLines: Int = Int.MAX_VALUE) {
    val lines = content.split("\n")
    var linesShown = 0
    
    Column {
        for (line in lines) {
            if (linesShown >= maxLines) break
            val match = Regex("^\\s*- \\[([ xX])\\] (.+)\$").find(line)
            if (match != null) {
                val checked = match.groupValues[1].lowercase() == "x"
                val text = match.groupValues[2]
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                linesShown++
            } else if (line.isNotBlank()) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                linesShown++
            }
        }
    }
}

@Composable
fun ImagePreview(base64: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val bitmap = remember(base64) {
        try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeStream(ByteArrayInputStream(bytes))
        } catch (e: Exception) { null }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

@Composable
fun ToastHost(toastData: ToastData?, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible = toastData != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        if (toastData != null) {
            val backgroundColor = when (toastData.type) {
                ToastType.SUCCESS -> Color(0xFF2E7D32)
                ToastType.ERROR -> Color(0xFFD32F2F)
                ToastType.INFO -> Color(0xFF1976D2)
            }
            
            Surface(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = backgroundColor,
                contentColor = Color.White,
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(12.dp, 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = toastData.message,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (toastData.undoAction != null) {
                        TextButton(
                            onClick = {
                                toastData.undoAction.invoke()
                                onDismiss()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                        ) {
                            Text("UNDO", fontWeight = FontWeight.Bold)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                }
            }

            LaunchedEffect(toastData) {
                kotlinx.coroutines.delay(toastData.duration)
                onDismiss()
            }
        }
    }
}

fun formatDate(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60000 -> "just now"
        diff < 3600000 -> "${diff / 60000}m ago"
        diff < 86400000 -> "${diff / 3600000}h ago"
        diff < 604800000 -> "${diff / 86400000}d ago"
        else -> java.text.SimpleDateFormat("MMM d", java.util.Locale.US).format(java.util.Date(timestamp))
    }
}

fun compressImage(bitmap: Bitmap): String {
    val outputStream = ByteArrayOutputStream()
    var quality = 80
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
    while (outputStream.toByteArray().size > 500 * 1024 && quality > 10) {
        outputStream.reset()
        quality -= 10
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
    }
    return Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
}
