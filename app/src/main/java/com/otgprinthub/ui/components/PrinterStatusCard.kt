package com.otgprinthub.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.otgprinthub.domain.model.Printer
import com.otgprinthub.domain.model.PrinterStatus
import com.otgprinthub.ui.theme.ErrorRed
import com.otgprinthub.ui.theme.SuccessGreen
import com.otgprinthub.ui.theme.WarningOrange

@Composable
fun PrinterStatusCard(
    printer: Printer?,
    onConnectClick: () -> Unit,
    onPrinterDetailsClick: (Printer) -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = printer != null && printer.status != PrinterStatus.DISCONNECTED && printer.status != PrinterStatus.ERROR
    val isReady = printer?.status == PrinterStatus.READY || printer?.status == PrinterStatus.DRIVER_FOUND

    val cardColor by animateColorAsState(
        targetValue = when {
            isReady -> MaterialTheme.colorScheme.primaryContainer
            isConnected -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        label = "card_color"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = when {
                        isReady -> Icons.Default.Print
                        isConnected -> Icons.Default.Usb
                        else -> Icons.Default.UsbOff
                    },
                    contentDescription = null,
                    tint = when {
                        isReady -> SuccessGreen
                        isConnected -> WarningOrange
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(32.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    if (printer != null && isConnected) {
                        Text(
                            text = printer.modelName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${printer.brandName} • ${printer.vid}:${printer.pid}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = getStatusText(printer.status),
                            style = MaterialTheme.typography.labelMedium,
                            color = when {
                                isReady -> SuccessGreen
                                printer.status == PrinterStatus.ERROR -> ErrorRed
                                else -> WarningOrange
                            }
                        )
                    } else {
                        Text(
                            text = "No Printer Connected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Connect a USB printer via OTG cable",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isConnected) {
                    Button(
                        onClick = onConnectClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Usb, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Connect Printer")
                    }
                } else {
                    OutlinedButton(
                        onClick = { printer?.let(onPrinterDetailsClick) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Details")
                    }
                    if (!isReady) {
                        Button(
                            onClick = onConnectClick,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Find Driver")
                        }
                    }
                }
            }
        }
    }
}

private fun getStatusText(status: PrinterStatus): String = when (status) {
    PrinterStatus.DETECTED -> "Detected - Searching driver..."
    PrinterStatus.REQUESTING_PERMISSION -> "Requesting USB permission..."
    PrinterStatus.PERMISSION_DENIED -> "Permission denied"
    PrinterStatus.SEARCHING_DRIVER -> "Searching for driver..."
    PrinterStatus.DRIVER_FOUND -> "Driver found - Ready to print"
    PrinterStatus.DRIVER_NOT_FOUND -> "No driver found - Using generic"
    PrinterStatus.READY -> "Ready to print"
    PrinterStatus.PRINTING -> "Printing..."
    PrinterStatus.ERROR -> "Error"
    PrinterStatus.DISCONNECTED -> "Disconnected"
}
