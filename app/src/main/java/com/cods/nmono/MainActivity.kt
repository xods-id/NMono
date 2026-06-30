package com.cods.nmono

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cods.nmono.data.ToastData
import com.cods.nmono.ui.components.ToastHost
import com.cods.nmono.ui.screens.*
import com.cods.nmono.ui.theme.NMonoTheme
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        
        QuickNoteService.start(this)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
        
        setContent {
            val viewModel: AppViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsState()
            val isFirstRun by viewModel.isFirstRun.collectAsState()
            val passwordHash by viewModel.passwordHash.collectAsState()
            val isLocked by viewModel.isLocked.collectAsState()
            
            var showSplash by remember { mutableStateOf(true) }
            var currentToast by remember { mutableStateOf<ToastData?>(null) }

            LaunchedEffect(Unit) {
                viewModel.toastEvent.collect { toast -> currentToast = toast }
            }

            LaunchedEffect(Unit) {
                delay(1500)
                showSplash = false
            }

            NMonoTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (showSplash) {
                        SplashScreen()
                    } else if (isFirstRun) {
                        SetupScreen(viewModel, onFinished = { viewModel.completeSetup() })
                    } else if (passwordHash != null && isLocked) {
                        val navController = rememberNavController()
                        LockScreen(viewModel, navController)
                    } else {
                        MainContent(viewModel, currentToast, onDismissToast = { currentToast = null })
                    }
                }
            }
        }
    }
}

@Composable
fun MainContent(viewModel: AppViewModel, currentToast: ToastData?, onDismissToast: () -> Unit) {
    val navController = rememberNavController()
    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = "home") {
            composable("home") { HomeScreen(viewModel, navController) }
            composable("view/{noteId}") { backStackEntry ->
                val noteId = backStackEntry.arguments?.getString("noteId") ?: ""
                ViewNoteScreen(viewModel, navController, noteId)
            }
            composable("edit/{noteId}") { backStackEntry ->
                val noteId = backStackEntry.arguments?.getString("noteId")
                EditNoteScreen(viewModel, navController, noteId)
            }
            composable("trash") { TrashScreen(viewModel, navController) }
            composable("settings") { SettingsScreen(viewModel, navController) }
        }
        
        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp).fillMaxWidth()) {
            ToastHost(toastData = currentToast, onDismiss = onDismissToast)
        }
    }
}

@Composable
fun SplashScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                modifier = Modifier.size(100.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text("NMono", style = MaterialTheme.typography.headlineLarge)
        }
    }
}
