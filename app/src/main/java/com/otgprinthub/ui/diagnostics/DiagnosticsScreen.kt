package com.otgprinthub.ui.diagnostics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    navController: NavController,
    viewModel: DiagnosticsViewModel = hiltViewModel()
) {
    val printer by viewModel.connectedPrinter.collectAsStateWithLifecycle()
    val usbState by viewModel.usbState.collectAsStateWithLifecycle()
    val descriptor by viewModel.descriptor.collectAsStateWithLifecycle()
    val exportPath by viewModel.exportPath.collectAsStateWithLifecycle()

    LaunchedEffect(exportPath) {
        exportPath?.let {
            // Show snackbar or toast
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("USB Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.exportDiagnostics() }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                DiagCard("Connection Status") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val connected = printer != null
                        Icon(
                            if (connected) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = null,
                            tint = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (connected) "Printer Connected" else "No Printer Connected")
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("State: $usbState", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    printer?.let { p ->
                        Text("Printer: ${p.brandName} ${p.modelName}", style = MaterialTheme.typography.bodySmall)
                        Text("VID: ${p.vid.uppercase()} PID: ${p.pid.uppercase()}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            if (descriptor != null) {
                item {
                    DiagCard("USB Descriptor") {
                        SelectionContainer {
                            Text(
                                text = formatDescriptor(descriptor!!),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            exportPath?.let { path ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Diagnostics exported!", fontWeight = FontWeight.Bold)
                                Text(path, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

private fun formatDescriptor(desc: Map<String, Any>): String {
    val sb = StringBuilder()
    desc.forEach { (key, value) ->
        when (value) {
            is List<*> -> {
                sb.appendLine("$key:")
                value.forEachIndexed { i, item ->
                    sb.appendLine("  [$i]: $item")
                }
            }
            else -> sb.appendLine("$key: $value")
        }
    }
    return sb.toString().trim()
}
