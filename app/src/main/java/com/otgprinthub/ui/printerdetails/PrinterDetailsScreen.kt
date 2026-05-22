package com.otgprinthub.ui.printerdetails

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterDetailsScreen(
    navController: NavController,
    printerId: Long,
    viewModel: PrinterDetailsViewModel = hiltViewModel()
) {
    val printer by viewModel.printer.collectAsStateWithLifecycle()
    val driver by viewModel.driver.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Remove Printer") },
            text = { Text("Remove this printer and its driver from the cache?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePrinter()
                    showDeleteDialog = false
                    navController.popBackStack()
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Printer Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove Printer", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { paddingValues ->
        if (printer == null) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val p = printer!!
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Card {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text(p.modelName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text(p.brandName, style = MaterialTheme.typography.bodyMedium)
                                    if (p.manufacturerName != null) Text(p.manufacturerName, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                item {
                    Card {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("USB Information", fontWeight = FontWeight.SemiBold)
                            DetailRow("Vendor ID (VID)", p.vid.uppercase())
                            DetailRow("Product ID (PID)", p.pid.uppercase())
                            DetailRow("Device Name", p.deviceName)
                            if (p.serialNumber != null) DetailRow("Serial Number", p.serialNumber)
                            DetailRow("USB Class", "0x${"%02X".format(p.usbClass)} (${if (p.usbClass == 7) "Printer" else "Unknown"})")
                            DetailRow("USB Subclass", "0x${"%02X".format(p.usbSubclass)}")
                            DetailRow("USB Protocol", "0x${"%02X".format(p.usbProtocol)}")
                        }
                    }
                }

                if (driver != null) {
                    val d = driver!!
                    item {
                        Card {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Driver Information", fontWeight = FontWeight.SemiBold)
                                DetailRow("Protocol", d.protocol.displayName)
                                DetailRow("Source", d.source.name)
                                DetailRow("Color Support", if (d.color) "Yes" else "No")
                                DetailRow("Duplex", if (d.duplex) "Yes" else "No")
                                DetailRow("Thermal", if (d.thermal) "Yes" else "No")
                                DetailRow("Max DPI", "${d.maxDpi} DPI")
                                DetailRow("Paper Sizes", d.paperSizes.joinToString(", "))
                                if (d.paperWidthMm != null) DetailRow("Paper Width", "${d.paperWidthMm} mm")
                            }
                        }
                    }
                }

                item {
                    DetailRow("Status", p.status.name)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}
