package com.otgprinthub.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.otgprinthub.ui.components.GradientBackground
import com.otgprinthub.ui.navigation.NavGraph
import com.otgprinthub.ui.navigation.Screen
import com.otgprinthub.ui.theme.GlassBorder
import com.otgprinthub.ui.theme.GlassOnSurface
import com.otgprinthub.ui.theme.GlassPrimary
import com.otgprinthub.ui.theme.GlassOnSurfaceVar
import com.otgprinthub.ui.theme.OtgPrintHubTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingShare by mutableStateOf<Pair<Uri, String>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShareIntent(intent)
        setContent {
            OtgPrintHubTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val scope = rememberCoroutineScope()

                val bottomNavRoutes = listOf(
                    Screen.Home.route, Screen.PrintQueue.route, Screen.Settings.route
                )
                val showBottomBar = currentRoute in bottomNavRoutes

                // Navigate to PrintPreview whenever a file arrives via share/open intent
                val shared = pendingShare
                LaunchedEffect(shared) {
                    if (shared != null) {
                        val (uri, fileType) = shared
                        pendingShare = null
                        scope.launch {
                            val ext = when (fileType) {
                                "PDF"   -> ".pdf"
                                "IMAGE" -> ".jpg"
                                "TEXT"  -> ".txt"
                                else    -> ".bin"
                            }
                            val finalUri = withContext(Dispatchers.IO) {
                                try {
                                    val dest = File(
                                        cacheDir,
                                        "printjob_${System.currentTimeMillis()}$ext"
                                    )
                                    contentResolver.openInputStream(uri)
                                        ?.use { it.copyTo(dest.outputStream()) }
                                    if (dest.exists() && dest.length() > 0)
                                        Uri.fromFile(dest)
                                    else uri
                                } catch (_: Exception) { uri }
                            }
                            val encoded = URLEncoder.encode(finalUri.toString(), "UTF-8")
                            navController.navigate(
                                Screen.PrintPreview.createRoute(encoded, fileType)
                            )
                        }
                    }
                }

                GradientBackground {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.Transparent,
                        bottomBar = {
                            if (showBottomBar) {
                                NavigationBar(
                                    containerColor = Color.White.copy(alpha = 0.08f),
                                    tonalElevation = 0.dp
                                ) {
                                    val itemColors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = GlassPrimary,
                                        selectedTextColor = GlassPrimary,
                                        unselectedIconColor = GlassOnSurfaceVar,
                                        unselectedTextColor = GlassOnSurfaceVar,
                                        indicatorColor = GlassPrimary.copy(alpha = 0.15f)
                                    )
                                    NavigationBarItem(
                                        icon = { Icon(Icons.Default.Home, "Home") },
                                        label = { Text("Home") },
                                        selected = currentRoute == Screen.Home.route,
                                        colors = itemColors,
                                        onClick = {
                                            navController.navigate(Screen.Home.route) {
                                                popUpTo(Screen.Home.route) { inclusive = true }
                                            }
                                        }
                                    )
                                    NavigationBarItem(
                                        icon = { Icon(Icons.Default.List, "Queue") },
                                        label = { Text("Queue") },
                                        selected = currentRoute == Screen.PrintQueue.route,
                                        colors = itemColors,
                                        onClick = {
                                            navController.navigate(Screen.PrintQueue.route) {
                                                popUpTo(Screen.Home.route)
                                            }
                                        }
                                    )
                                    NavigationBarItem(
                                        icon = { Icon(Icons.Default.Settings, "Settings") },
                                        label = { Text("Settings") },
                                        selected = currentRoute == Screen.Settings.route,
                                        colors = itemColors,
                                        onClick = {
                                            navController.navigate(Screen.Settings.route) {
                                                popUpTo(Screen.Home.route)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    ) { paddingValues ->
                        NavGraph(navController = navController)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                } ?: intent.data
            }
            Intent.ACTION_VIEW -> intent.data
            else -> null
        } ?: return

        val mimeType = intent?.type ?: contentResolver.getType(uri) ?: ""
        val fileType = when {
            mimeType.contains("pdf", ignoreCase = true) -> "PDF"
            mimeType.startsWith("image/", ignoreCase = true) -> "IMAGE"
            mimeType.startsWith("text/", ignoreCase = true) -> "TEXT"
            mimeType.contains("word", ignoreCase = true) -> "TEXT"
            else -> "UNKNOWN"
        }
        pendingShare = Pair(uri, fileType)
    }
}
