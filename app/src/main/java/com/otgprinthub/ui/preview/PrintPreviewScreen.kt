package com.otgprinthub.ui.preview

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.otgprinthub.domain.model.*
import com.otgprinthub.ui.components.GlassCard
import com.otgprinthub.ui.components.GlassCardHighlight
import com.otgprinthub.ui.components.GlassTopBar
import com.otgprinthub.ui.theme.*
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
    val previewBitmap by viewModel.previewBitmap.collectAsStateWithLifecycle()
    val previewLoading by viewModel.previewLoading.collectAsStateWithLifecycle()

    val settingsExpanded = remember { mutableStateOf(false) }

    LaunchedEffect(printState) {
        if (printState is PrintPreviewViewModel.PrintState.Done) {
            navController.popBackStack()
        }
    }
    LaunchedEffect(uri) { viewModel.generatePreview(uri) }
    LaunchedEffect(settings.paperSize, settings.orientation, settings.colorMode, settings.fitMode) {
        viewModel.generatePreview(uri)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = "Print Preview",
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back",
                            tint = GlassOnSurface)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (printState is PrintPreviewViewModel.PrintState.Idle)
                        viewModel.print(uri, fileTypeEnum)
                },
                icon = { Icon(Icons.Default.Print, contentDescription = "Print") },
                text = { Text("Print") },
                containerColor = GlassPrimary,
                contentColor = Color(0xFF001A60),
                expanded = true
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Preview card ──────────────────────────────────────────────────
            val safeH = settings.paperSize.heightMm.takeIf { it < 10_000f } ?: 297f
            val paperAspectRatio = if (settings.orientation == Orientation.LANDSCAPE)
                (safeH / settings.paperSize.widthMm).coerceIn(0.3f, 3f)
            else
                (settings.paperSize.widthMm / safeH).coerceIn(0.3f, 3f)

            val previewShape = RoundedCornerShape(16.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(paperAspectRatio)
                    .clip(previewShape)
                    .background(Color.White.copy(alpha = 0.95f))
                    .border(1.dp, GlassBorder, previewShape),
                contentAlignment = Alignment.Center
            ) {
                val bmp = previewBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Print preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (previewLoading) {
                    CircularProgressIndicator(color = GlassPrimary)
                } else {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.Gray
                    )
                }
            }

            // ── File info ─────────────────────────────────────────────────────
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = when (fileTypeEnum) {
                            FileType.PDF    -> Icons.Default.PictureAsPdf
                            FileType.IMAGE  -> Icons.Default.Image
                            FileType.TEXT   -> Icons.Default.TextFields
                            else            -> Icons.Default.InsertDriveFile
                        },
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = GlassPrimary
                    )
                    Column {
                        Text(
                            uri.lastPathSegment ?: "File",
                            fontWeight = FontWeight.Bold,
                            color = GlassOnSurface
                        )
                        Text(
                            fileTypeEnum.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = GlassOnSurfaceVar
                        )
                    }
                }
            }

            // ── Printer status ────────────────────────────────────────────────
            if (printer != null) {
                GlassCardHighlight(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = GlassSuccess
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, tint = GlassSuccess)
                        Text(
                            "Printing to: ${printer!!.modelName}",
                            fontWeight = FontWeight.Medium,
                            color = GlassOnSurface
                        )
                    }
                }
            } else {
                GlassCardHighlight(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = GlassError
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = GlassError)
                        Text("No printer connected", color = GlassError, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // ── Print Settings ────────────────────────────────────────────────
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Print Settings",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall,
                        color = GlassOnSurface
                    )
                    IconButton(onClick = { settingsExpanded.value = !settingsExpanded.value }) {
                        Icon(
                            if (settingsExpanded.value) Icons.Default.ExpandLess
                            else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = GlassOnSurface
                        )
                    }
                }

                AnimatedVisibility(visible = settingsExpanded.value) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Divider(color = GlassBorderSubtle)

                        GlassSettingDropdown(
                            label = "Paper Size",
                            options = PaperSize.entries.map { it.displayName },
                            selected = settings.paperSize.displayName,
                            onSelect = { name ->
                                val size = PaperSize.entries.firstOrNull {
                                    it.displayName == name
                                } ?: PaperSize.A4
                                viewModel.updateSettings(settings.copy(paperSize = size))
                            }
                        )

                        GlassSettingRow("Orientation") {
                            Row {
                                Orientation.entries.forEach { orientation ->
                                    FilterChip(
                                        selected = settings.orientation == orientation,
                                        onClick = {
                                            viewModel.updateSettings(
                                                settings.copy(orientation = orientation)
                                            )
                                        },
                                        label = {
                                            Text(orientation.displayName, color = GlassOnSurface)
                                        },
                                        modifier = Modifier.padding(end = 8.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = GlassPrimary.copy(alpha = 0.25f),
                                            selectedLabelColor = GlassPrimary,
                                            containerColor = Color.Transparent,
                                            labelColor = GlassOnSurfaceVar
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = settings.orientation == orientation,
                                            borderColor = GlassBorderSubtle,
                                            selectedBorderColor = GlassPrimary.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                            }
                        }

                        GlassSettingRow("Color Mode") {
                            Row {
                                ColorMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = settings.colorMode == mode,
                                        onClick = {
                                            viewModel.updateSettings(settings.copy(colorMode = mode))
                                        },
                                        label = {
                                            Text(mode.displayName, color = GlassOnSurface)
                                        },
                                        modifier = Modifier.padding(end = 4.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = GlassSecondary.copy(alpha = 0.25f),
                                            selectedLabelColor = GlassSecondary,
                                            containerColor = Color.Transparent,
                                            labelColor = GlassOnSurfaceVar
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = settings.colorMode == mode,
                                            borderColor = GlassBorderSubtle,
                                            selectedBorderColor = GlassSecondary.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                            }
                        }

                        GlassSettingDropdown(
                            label = "Quality",
                            options = PrintQuality.entries.map { it.displayName },
                            selected = settings.quality.displayName,
                            onSelect = { name ->
                                val quality = PrintQuality.entries.firstOrNull {
                                    it.displayName == name
                                } ?: PrintQuality.NORMAL
                                viewModel.updateSettings(settings.copy(quality = quality))
                            }
                        )

                        GlassSettingDropdown(
                            label = "Fit Mode",
                            options = FitMode.entries.map { it.displayName },
                            selected = settings.fitMode.displayName,
                            onSelect = { name ->
                                val mode = FitMode.entries.firstOrNull {
                                    it.displayName == name
                                } ?: FitMode.FIT_TO_PAGE
                                viewModel.updateSettings(settings.copy(fitMode = mode))
                            }
                        )

                        GlassSettingRow("Copies") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (settings.copies > 1)
                                            viewModel.updateSettings(
                                                settings.copy(copies = settings.copies - 1)
                                            )
                                    },
                                    enabled = settings.copies > 1
                                ) {
                                    Icon(Icons.Default.Remove, "-",
                                        tint = if (settings.copies > 1) GlassOnSurface
                                        else GlassOnSurfaceDim)
                                }
                                Text(
                                    "${settings.copies}",
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    color = GlassOnSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                                IconButton(
                                    onClick = {
                                        viewModel.updateSettings(
                                            settings.copy(copies = settings.copies + 1)
                                        )
                                    }
                                ) {
                                    Icon(Icons.Default.Add, "+", tint = GlassOnSurface)
                                }
                            }
                        }
                    }
                }
            }

            // ── Print Progress / Error ────────────────────────────────────────
            when (val state = printState) {
                is PrintPreviewViewModel.PrintState.Printing -> {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = GlassOnSurface)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = GlassPrimary,
                            trackColor = GlassBorderSubtle
                        )
                    }
                }
                is PrintPreviewViewModel.PrintState.Failed -> {
                    GlassCardHighlight(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = GlassError
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null,
                                tint = GlassError)
                            Spacer(Modifier.width(8.dp))
                            Text("Print failed: ${state.error}", color = GlassError)
                        }
                    }
                }
                else -> {}
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun GlassSettingRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = GlassOnSurfaceVar)
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassSettingDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = GlassOnSurfaceVar)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selected,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .width(160.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = GlassOnSurface,
                    unfocusedTextColor = GlassOnSurface,
                    focusedBorderColor = GlassPrimary,
                    unfocusedBorderColor = GlassBorderSubtle,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTrailingIconColor = GlassPrimary,
                    unfocusedTrailingIconColor = GlassOnSurfaceVar
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
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
