package com.otgprinthub.ui.preview

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.otgprinthub.domain.model.*
import com.otgprinthub.ui.theme.ErrorRed
import com.otgprinthub.ui.theme.SuccessGreen
import java.net.URLDecoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintPreviewScreen(
    navController: NavController,
    fileUri: String,
    fileType: String,
    viewModel: PrintPreviewViewModel = hiltViewModel()
) {
    val decodedUri = URLDecoder.decode(fileUri, "UTF-8")
    val uri = Uri.parse(decodedUri)
    val fileTypeEnum = runCatching { FileType.valueOf(fileType) }.getOrDefault(FileType.UNKNOWN)

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val printer by viewModel.connectedPrinter.collectAsStateWithLifecycle()
    val printState by viewModel.printState.collectAsStateWithLifecycle()

    val settingsExpanded = remember { mutableStateOf(false) }

    LaunchedEffect(printState) {
        if (printState is PrintPreviewViewModel.PrintState.Done) {
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Print Preview") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (printState is PrintPreviewViewModel.PrintState.Idle) viewModel.print(uri, fileTypeEnum) },
                icon = { Icon(Icons.Default.Print, contentDescription = "Print") },
                text = { Text("Print") },
                expanded = true
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // File Info Card
            Card {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = when (fileTypeEnum) {
                            FileType.PDF -> Icons.Default.PictureAsPdf
                            FileType.IMAGE -> Icons.Default.Image
                            FileType.TEXT -> Icons.Default.TextFields
                            else -> Icons.Default.InsertDriveFile
                        },
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(uri.lastPathSegment ?: "File", fontWeight = FontWeight.Bold)
                        Text(fileTypeEnum.displayName, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Printer Card
            if (printer != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, tint = SuccessGreen)
                        Text("Printing to: ${printer!!.modelName}", fontWeight = FontWeight.Medium)
                    }
                }
            } else {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed)
                        Text("No printer connected", color = ErrorRed)
                    }
                }
            }

            // Print Settings
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Print Settings", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                        IconButton(onClick = { settingsExpanded.value = !settingsExpanded.value }) {
                            Icon(
                                if (settingsExpanded.value) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }
                    }

                    AnimatedVisibility(visible = settingsExpanded.value) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Divider()

                            // Copies
                            SettingRow("Copies") {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { if (settings.copies > 1) viewModel.updateSettings(settings.copy(copies = settings.copies - 1)) },
                                        enabled = settings.copies > 1
                                    ) { Icon(Icons.Default.Remove, contentDescription = "-") }
                                    Text("${settings.copies}", modifier = Modifier.padding(horizontal = 8.dp))
                                    IconButton(
                                        onClick = { viewModel.updateSettings(settings.copy(copies = settings.copies + 1)) }
                                    ) { Icon(Icons.Default.Add, contentDescription = "+") }
                                }
                            }

                            // Paper Size
                            SettingDropdown(
                                label = "Paper Size",
                                options = PaperSize.entries.map { it.displayName },
                                selected = settings.paperSize.displayName,
                                onSelect = { name ->
                                    val size = PaperSize.entries.firstOrNull { it.displayName == name } ?: PaperSize.A4
                                    viewModel.updateSettings(settings.copy(paperSize = size))
                                }
                            )

                            // Orientation
                            SettingRow("Orientation") {
                                Row {
                                    Orientation.entries.forEach { orientation ->
                                        FilterChip(
                                            selected = settings.orientation == orientation,
                                            onClick = { viewModel.updateSettings(settings.copy(orientation = orientation)) },
                                            label = { Text(orientation.displayName) },
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                    }
                                }
                            }

                            // Color Mode
                            SettingRow("Color Mode") {
                                Row {
                                    ColorMode.entries.forEach { mode ->
                                        FilterChip(
                                            selected = settings.colorMode == mode,
                                            onClick = { viewModel.updateSettings(settings.copy(colorMode = mode)) },
                                            label = { Text(mode.displayName) },
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                    }
                                }
                            }

                            // Quality
                            SettingDropdown(
                                label = "Quality",
                                options = PrintQuality.entries.map { it.displayName },
                                selected = settings.quality.displayName,
                                onSelect = { name ->
                                    val quality = PrintQuality.entries.firstOrNull { it.displayName == name } ?: PrintQuality.NORMAL
                                    viewModel.updateSettings(settings.copy(quality = quality))
                                }
                            )
                        }
                    }
                }
            }

            // Print Progress
            when (val state = printState) {
                is PrintPreviewViewModel.PrintState.Printing -> {
                    Card {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(state.message, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { state.progress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                is PrintPreviewViewModel.PrintState.Failed -> {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = ErrorRed)
                            Spacer(Modifier.width(8.dp))
                            Text("Print failed: ${state.error}", color = ErrorRed)
                        }
                    }
                }
                else -> {}
            }

            Spacer(Modifier.height(80.dp))  // FAB space
        }
    }
}

@Composable
private fun SettingRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingDropdown(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selected,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().width(160.dp),
                singleLine = true
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = { onSelect(option); expanded = false }
                    )
                }
            }
        }
    }
}
