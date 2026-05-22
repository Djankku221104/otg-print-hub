package com.otgprinthub.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.otgprinthub.BuildConfig
import com.otgprinthub.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val autoConnect by viewModel.autoConnect.collectAsStateWithLifecycle()
    val autoDownload by viewModel.autoDownload.collectAsStateWithLifecycle()
    val driverRepoUrl by viewModel.driverRepoUrl.collectAsStateWithLifecycle()
    var showRepoDialog by remember { mutableStateOf(false) }

    if (showRepoDialog) {
        var tempUrl by remember { mutableStateOf(driverRepoUrl) }
        AlertDialog(
            onDismissRequest = { showRepoDialog = false },
            title = { Text("Driver Repository URL") },
            text = {
                OutlinedTextField(
                    value = tempUrl,
                    onValueChange = { tempUrl = it },
                    label = { Text("URL") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setDriverRepoUrl(tempUrl)
                    showRepoDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showRepoDialog = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { SectionTitle("Connection") }
            item {
                SettingsToggleItem(
                    title = "Auto-connect Last Printer",
                    subtitle = "Automatically reconnect to last used printer",
                    checked = autoConnect,
                    onToggle = { viewModel.setAutoConnect(it) }
                )
            }

            item { SectionTitle("Drivers") }
            item {
                SettingsToggleItem(
                    title = "Auto-download Drivers",
                    subtitle = "Automatically download drivers for new printers",
                    checked = autoDownload,
                    onToggle = { viewModel.setAutoDownload(it) }
                )
            }
            item {
                SettingsClickItem(
                    title = "Driver Repository URL",
                    subtitle = driverRepoUrl.take(50) + if (driverRepoUrl.length > 50) "..." else "",
                    icon = Icons.Default.Storage,
                    onClick = { showRepoDialog = true }
                )
            }
            item {
                SettingsClickItem(
                    title = "Driver Manager",
                    subtitle = "Manage downloaded printer drivers",
                    icon = Icons.Default.ManageAccounts,
                    onClick = { navController.navigate(Screen.DriverManager.route) }
                )
            }

            item { SectionTitle("Diagnostics") }
            item {
                SettingsClickItem(
                    title = "USB Diagnostics",
                    subtitle = "View USB connection details and logs",
                    icon = Icons.Default.BugReport,
                    onClick = { navController.navigate(Screen.Diagnostics.route) }
                )
            }

            item { SectionTitle("About") }
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("OTG Print Hub", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("Print documents, images, and text directly to USB printers via OTG adapter.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun SettingsToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun SettingsClickItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
