package com.cods.nmono.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.cods.nmono.AppThemeMode
import com.cods.nmono.AppViewModel
import com.cods.nmono.data.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AppViewModel, navController: NavController) {
    val passwordHash by viewModel.passwordHash.collectAsState()
    val biometricEnabled by viewModel.biometricEnabled.collectAsState()
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showExportImportDialog by remember { mutableStateOf<String?>(null) } // "export" or "import"
    
    var passwordInput by remember { mutableStateOf("") }
    var confirmInput by remember { mutableStateOf("") }
    var dialogMode by remember { mutableStateOf("set") }
    
    val context = LocalContext.current
    var pendingImportJson by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { out ->
                out.write(viewModel.exportData(passwordInput).toByteArray())
                viewModel.showToast("Notes exported and encrypted", ToastType.SUCCESS)
                passwordInput = ""
                showExportImportDialog = null
            }
        }
    }
    
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { input ->
                pendingImportJson = input.bufferedReader().readText()
                showExportImportDialog = "import"
            }
        }
    }

    if (showExportImportDialog != null) {
        AlertDialog(
            onDismissRequest = { 
                showExportImportDialog = null
                passwordInput = ""
                pendingImportJson = null
            },
            title = { Text(if (showExportImportDialog == "export") "Set Encryption Password" else "Enter Backup Password") },
            text = {
                Column {
                    Text(if (showExportImportDialog == "export") 
                        "Set a password to encrypt this backup. You will need it to import these notes later." 
                        else "Enter the password used to encrypt this backup file.")
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (passwordInput.isBlank()) {
                        viewModel.showToast("Password cannot be empty", ToastType.ERROR)
                        return@Button
                    }
                    if (showExportImportDialog == "export") {
                        exportLauncher.launch("nmono-backup.json")
                    } else {
                        val count = viewModel.importData(pendingImportJson ?: "", passwordInput)
                        if (count >= 0) {
                            viewModel.showToast("Successfully imported $count notes", ToastType.SUCCESS)
                            showExportImportDialog = null
                            passwordInput = ""
                            pendingImportJson = null
                        } else {
                            viewModel.showToast("Import failed: Incorrect password or corrupted file", ToastType.ERROR)
                        }
                    }
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showExportImportDialog = null
                    passwordInput = ""
                    pendingImportJson = null
                }) { Text("Cancel") }
            }
        )
    }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text(if (dialogMode == "set") "Set App Password" else "Enter Password") },
            text = {
                Column {
                    OutlinedTextField(
                        value = passwordInput, 
                        onValueChange = { passwordInput = it }, 
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation()
                    )
                    if (dialogMode == "set") {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = confirmInput, 
                            onValueChange = { confirmInput = it }, 
                            label = { Text("Confirm") },
                            visualTransformation = PasswordVisualTransformation()
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (dialogMode == "set" && passwordInput != confirmInput) {
                        viewModel.showToast("Passwords don't match", ToastType.ERROR)
                        return@Button
                    }
                    viewModel.setPassword(passwordInput)
                    showPasswordDialog = false
                    passwordInput = ""
                    confirmInput = ""
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .imePadding()
        ) {
            Text("Security", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (passwordHash == null) {
                Button(onClick = { dialogMode = "set"; showPasswordDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Lock, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Set Password")
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.lock() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Lock, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Lock")
                    }
                    Button(
                        onClick = { viewModel.removePassword() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.LockOpen, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Remove")
                    }
                }
            }
            
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            
            Text("Data Backup", style = MaterialTheme.typography.titleMedium)
            Text("Backups are always encrypted for your security.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showExportImportDialog = "export" }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Upload, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Export")
                }
                Button(onClick = { importLauncher.launch(arrayOf("application/json")) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Download, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Import")
                }
            }
            
            Spacer(Modifier.height(24.dp))
            Text("About", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("NMono v1.0", style = MaterialTheme.typography.bodyMedium)
            Text("AES-256-GCM Encrypted · PBKDF2 Hashing", style = MaterialTheme.typography.bodySmall)
        }
    }
}
