package com.otgprinthub.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.otgprinthub.domain.model.FileType
import com.otgprinthub.domain.model.JobStatus
import com.otgprinthub.domain.model.PrinterStatus
import com.otgprinthub.ui.components.PrintJobItem
import com.otgprinthub.ui.components.PrinterStatusCard
import com.otgprinthub.ui.navigation.Screen
import com.otgprinthub.util.Extensions.toVidPidString
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val printer by viewModel.connectedPrinter.collectAsStateWithLifecycle()
    val recentJobs by viewModel.recentJobs.collectAsStateWithLifecycle()

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val encoded = URLEncoder.encode(it.toString(), "UTF-8")
            navController.navigate(Screen.PrintPreview.createRoute(encoded, FileType.PDF.name))
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val encoded = URLEncoder.encode(it.toString(), "UTF-8")
            navController.navigate(Screen.PrintPreview.createRoute(encoded, FileType.IMAGE.name))
        }
    }

    val textLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val encoded = URLEncoder.encode(it.toString(), "UTF-8")
            navController.navigate(Screen.PrintPreview.createRoute(encoded, FileType.TEXT.name))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OTG Print Hub", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Detection.route) }) {
                        Icon(Icons.Default.Search, contentDescription = "Detect Printer")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                PrinterStatusCard(
                    printer = printer,
                    onConnectClick = { navController.navigate(Screen.Detection.route) },
                    onPrinterDetailsClick = { p ->
                        navController.navigate(Screen.PrinterDetails.createRoute(p.id))
                    }
                )
            }

            item {
                Text(
                    text = "Quick Print",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.PictureAsPdf,
                        label = "Print PDF",
                        enabled = printer?.status == PrinterStatus.READY || printer?.status == PrinterStatus.DRIVER_FOUND,
                        onClick = { pdfLauncher.launch(arrayOf("application/pdf")) }
                    )
                    QuickActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Image,
                        label = "Print Image",
                        enabled = printer?.status == PrinterStatus.READY || printer?.status == PrinterStatus.DRIVER_FOUND,
                        onClick = { imageLauncher.launch(arrayOf("image/*")) }
                    )
                    QuickActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.TextFields,
                        label = "Print Text",
                        enabled = printer?.status == PrinterStatus.READY || printer?.status == PrinterStatus.DRIVER_FOUND,
                        onClick = { textLauncher.launch(arrayOf("text/*")) }
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Jobs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(onClick = { navController.navigate(Screen.PrintQueue.route) }) {
                        Text("See All")
                    }
                }
            }

            val displayJobs = recentJobs.take(5)
            if (displayJobs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No recent print jobs",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(displayJobs) { job ->
                    PrintJobItem(job = job, onRetry = null, onCancel = null)
                }
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    ElevatedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
