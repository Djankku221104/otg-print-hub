package com.otgprinthub.ui.home

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
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
import com.otgprinthub.domain.usecase.FindDriverUseCase
import com.otgprinthub.print.TestPageGenerator
import com.otgprinthub.ui.components.PrintJobItem
import com.otgprinthub.ui.components.PrinterStatusCard
import com.otgprinthub.ui.navigation.Screen
import com.otgprinthub.util.toVidPidString
import java.io.File
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val printer by viewModel.connectedPrinter.collectAsStateWithLifecycle()
    val recentJobs by viewModel.recentJobs.collectAsStateWithLifecycle()
    val driverSearchState by viewModel.autoDriverSearchState.collectAsStateWithLifecycle()
    val testPrintState by viewModel.testPrintState.collectAsStateWithLifecycle()
    val testExpanded = remember { mutableStateOf(false) }

    fun persistAndNavigate(uri: Uri, fileType: FileType) {
        scope.launch {
            // Try persistent permission first (fast path, works on most devices)
            val permOk = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }.isSuccess

            val finalUri = if (permOk) {
                uri
            } else {
                // Permission didn't persist — copy to private cache so
                // @ApplicationContext can read it without a URI grant
                val ext = when (fileType) {
                    FileType.PDF -> ".pdf"
                    FileType.IMAGE -> ".jpg"
                    FileType.TEXT -> ".txt"
                    else -> ".bin"
                }
                withContext(Dispatchers.IO) {
                    try {
                        val dest = File(context.cacheDir, "printjob_${System.currentTimeMillis()}$ext")
                        context.contentResolver.openInputStream(uri)
                            ?.use { it.copyTo(dest.outputStream()) }
                        Uri.fromFile(dest)
                    } catch (_: Exception) { uri }
                }
            }

            val encoded = URLEncoder.encode(finalUri.toString(), "UTF-8")
            navController.navigate(Screen.PrintPreview.createRoute(encoded, fileType.name))
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { persistAndNavigate(it, FileType.PDF) } }

    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { persistAndNavigate(it, FileType.IMAGE) } }

    val textLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { persistAndNavigate(it, FileType.TEXT) } }

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

            // Auto driver search status banner
            val searchMsg = when (driverSearchState) {
                is FindDriverUseCase.DriverSearchState.CheckingLocalCache -> "Checking cached drivers..."
                is FindDriverUseCase.DriverSearchState.SearchingGithubDb -> "Searching online driver database..."
                is FindDriverUseCase.DriverSearchState.CheckingOpenPrinting -> "Checking OpenPrinting.org..."
                is FindDriverUseCase.DriverSearchState.TryingGenericDrivers -> "Applying generic driver..."
                is FindDriverUseCase.DriverSearchState.DriverFound -> "Driver found: ${(driverSearchState as FindDriverUseCase.DriverSearchState.DriverFound).driver.model}"
                is FindDriverUseCase.DriverSearchState.DriverNotFound -> "No specific driver found — using generic mode"
                else -> null
            }
            if (searchMsg != null) {
                item {
                    val isSearching = driverSearchState !is FindDriverUseCase.DriverSearchState.DriverFound &&
                            driverSearchState !is FindDriverUseCase.DriverSearchState.DriverNotFound
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSearching)
                                MaterialTheme.colorScheme.secondaryContainer
                            else
                                MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (isSearching) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.CheckCircle, contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                            Text(searchMsg, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
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
                        enabled = printer != null,
                        onClick = { pdfLauncher.launch(arrayOf("application/pdf")) }
                    )
                    QuickActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Image,
                        label = "Print Image",
                        enabled = printer != null,
                        onClick = { imageLauncher.launch(arrayOf("image/*")) }
                    )
                    QuickActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.TextFields,
                        label = "Print Text",
                        enabled = printer != null,
                        onClick = { textLauncher.launch(arrayOf("text/*")) }
                    )
                }
            }

            // ── Test Print Section ────────────────────────────────────────────────
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Test Print", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                                Text("Diagnose printer issues", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                                testExpanded.value = !testExpanded.value
                                viewModel.resetTestPrintState()
                            }) {
                                Icon(
                                    if (testExpanded.value) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null
                                )
                            }
                        }

                        if (testExpanded.value) {
                            Spacer(Modifier.height(8.dp))
                            Divider()
                            Spacer(Modifier.height(8.dp))

                            TestPageGenerator.TestType.entries.forEach { type ->
                                OutlinedButton(
                                    onClick = {
                                        viewModel.printTestPage(type)
                                    },
                                    enabled = printer != null && testPrintState !is HomeViewModel.TestPrintState.Sending,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(type.displayName, style = MaterialTheme.typography.labelMedium)
                                        Text(type.description, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            when (val s = testPrintState) {
                                is HomeViewModel.TestPrintState.Sending ->
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Text("Sending test data...", style = MaterialTheme.typography.bodySmall)
                                    }
                                is HomeViewModel.TestPrintState.Done ->
                                    Text("Sent! Check printer.", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary)
                                is HomeViewModel.TestPrintState.Failed ->
                                    Text("Failed: ${s.error}", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error)
                                else -> {}
                            }
                        }
                    }
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
