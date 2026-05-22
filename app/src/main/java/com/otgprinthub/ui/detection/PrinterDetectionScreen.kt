package com.otgprinthub.ui.detection

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.otgprinthub.domain.model.Printer
import com.otgprinthub.domain.model.PrinterStatus
import com.otgprinthub.domain.usecase.FindDriverUseCase
import com.otgprinthub.ui.navigation.Screen
import com.otgprinthub.ui.theme.ErrorRed
import com.otgprinthub.ui.theme.SuccessGreen
import com.otgprinthub.ui.theme.WarningOrange
import com.otgprinthub.usb.UsbPrinterManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterDetectionScreen(
    navController: NavController,
    viewModel: PrinterDetectionViewModel = hiltViewModel()
) {
    val printer by viewModel.connectedPrinter.collectAsStateWithLifecycle()
    val usbState by viewModel.usbState.collectAsStateWithLifecycle()
    val driverState by viewModel.driverSearchState.collectAsStateWithLifecycle()
    val driver by viewModel.currentDriver.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.scanForPrinters() }

    LaunchedEffect(printer) {
        printer?.let { p ->
            if (p.status == PrinterStatus.DETECTED && driverState == null) {
                viewModel.findDriver(p)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Printer Detection") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            when (usbState) {
                is UsbPrinterManager.UsbState.Idle -> {
                    NoPrinterView(onScan = { viewModel.scanForPrinters() })
                }
                is UsbPrinterManager.UsbState.DeviceDetected,
                is UsbPrinterManager.UsbState.RequestingPermission -> {
                    DetectingView()
                }
                is UsbPrinterManager.UsbState.PermissionDenied -> {
                    PermissionDeniedView()
                }
                is UsbPrinterManager.UsbState.PermissionGranted,
                is UsbPrinterManager.UsbState.Connected -> {
                    printer?.let { p ->
                        PrinterFoundView(
                            printer = p,
                            driverState = driverState,
                            driverName = driver?.let { "${it.brand} ${it.model}" },
                            driverProtocol = driver?.protocol?.displayName,
                            onContinue = { navController.navigate(Screen.Home.route) }
                        )
                    }
                }
                is UsbPrinterManager.UsbState.Disconnected -> {
                    NoPrinterView(onScan = { viewModel.scanForPrinters() })
                }
                is UsbPrinterManager.UsbState.Error -> {
                    val err = (usbState as UsbPrinterManager.UsbState.Error).message
                    ErrorView(message = err)
                }
            }
        }
    }
}

@Composable
private fun NoPrinterView(onScan: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Default.UsbOff,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text("No Printer Detected", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Connect a USB printer via OTG cable and tap Scan",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onScan) {
            Icon(Icons.Default.Search, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Scan for Printers")
        }
    }
}

@Composable
private fun DetectingView() {
    val rotation by rememberInfiniteTransition(label = "scan").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1500, easing = LinearEasing)),
        label = "rotate"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Default.Usb,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text("Printer Detected!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        Text("Requesting USB permission...", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PermissionDeniedView() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(80.dp), tint = ErrorRed)
        Spacer(Modifier.height(16.dp))
        Text("Permission Denied", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("USB permission is required to communicate with the printer.")
    }
}

@Composable
private fun PrinterFoundView(
    printer: Printer,
    driverState: FindDriverUseCase.DriverSearchState?,
    driverName: String?,
    driverProtocol: String?,
    onContinue: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Printer Detected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(printer.modelName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${printer.brandName}  VID: ${printer.vid}  PID: ${printer.pid}", style = MaterialTheme.typography.bodySmall)
            }
        }

        when (driverState) {
            is FindDriverUseCase.DriverSearchState.CheckingLocalCache ->
                DriverSearchStep("Checking local cache...")
            is FindDriverUseCase.DriverSearchState.SearchingGithubDb ->
                DriverSearchStep("Searching online driver database...")
            is FindDriverUseCase.DriverSearchState.CheckingOpenPrinting ->
                DriverSearchStep("Checking OpenPrinting.org...")
            is FindDriverUseCase.DriverSearchState.TryingGenericDrivers ->
                DriverSearchStep("Trying generic drivers...")
            is FindDriverUseCase.DriverSearchState.DriverFound -> {
                DriverFoundCard(driverName = driverName ?: "", protocol = driverProtocol ?: "")
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Print, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start Printing")
                }
            }
            is FindDriverUseCase.DriverSearchState.DriverNotFound -> {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text("No driver found. Will use raw text mode.", modifier = Modifier.padding(16.dp))
                }
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Continue Anyway") }
            }
            null -> CircularProgressIndicator()
            else -> {}
        }
    }
}

@Composable
private fun DriverSearchStep(message: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DriverFoundCard(driverName: String, protocol: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Driver Found!", fontWeight = FontWeight.Bold, color = SuccessGreen)
                Text(driverName, style = MaterialTheme.typography.bodyMedium)
                Text("Protocol: $protocol", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ErrorView(message: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Error, contentDescription = null, modifier = Modifier.size(64.dp), tint = ErrorRed)
        Spacer(Modifier.height(16.dp))
        Text("Error", style = MaterialTheme.typography.titleLarge)
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
