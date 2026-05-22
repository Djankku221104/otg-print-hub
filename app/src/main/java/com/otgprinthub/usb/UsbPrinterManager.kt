package com.otgprinthub.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.otgprinthub.domain.model.Printer
import com.otgprinthub.domain.model.PrinterStatus
import com.otgprinthub.domain.repository.PrinterRepository
import com.otgprinthub.driver.VidPidDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsbPrinterManager @Inject constructor(
    private val usbManager: UsbManager,
    private val detector: UsbPrinterDetector,
    private val permissionHandler: UsbPermissionHandler,
    private val vidPidDatabase: VidPidDatabase,
    private val printerRepository: PrinterRepository
) {
    private val TAG = "UsbPrinterManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectedPrinter = MutableStateFlow<Printer?>(null)
    val connectedPrinter: StateFlow<Printer?> = _connectedPrinter.asStateFlow()

    private val _usbState = MutableStateFlow<UsbState>(UsbState.Idle)
    val usbState: StateFlow<UsbState> = _usbState.asStateFlow()

    sealed class UsbState {
        data object Idle : UsbState()
        data class DeviceDetected(val device: UsbDevice) : UsbState()
        data class RequestingPermission(val device: UsbDevice) : UsbState()
        data class PermissionGranted(val device: UsbDevice) : UsbState()
        data class PermissionDenied(val device: UsbDevice) : UsbState()
        data class Connected(val printer: Printer) : UsbState()
        data class Disconnected(val device: UsbDevice) : UsbState()
        data class Error(val message: String) : UsbState()
    }

    init {
        // Scan already-connected devices on startup
        scope.launch { scanConnectedDevices() }
    }

    fun scanConnectedDevices() {
        usbManager.deviceList.values
            .filter { detector.isPrinterDevice(it) }
            .forEach { device ->
                Log.d(TAG, "Found connected printer: ${device.deviceName}")
                onDeviceAttached(device)
            }
    }

    fun onDeviceAttached(device: UsbDevice) {
        if (!detector.isPrinterDevice(device)) {
            Log.d(TAG, "Ignoring non-printer USB device: ${device.deviceName}")
            return
        }

        Log.d(TAG, "Printer attached: VID=${"%04x".format(device.vendorId)} PID=${"%04x".format(device.productId)}")
        _usbState.value = UsbState.DeviceDetected(device)

        if (permissionHandler.hasPermission(device)) {
            onPermissionResult(device, true)
        } else {
            _usbState.value = UsbState.RequestingPermission(device)
            permissionHandler.requestPermission(device)
        }
    }

    fun onDeviceDetached(device: UsbDevice) {
        Log.d(TAG, "Printer detached: ${device.deviceName}")
        _usbState.value = UsbState.Disconnected(device)
        if (_connectedPrinter.value?.deviceName == device.deviceName) {
            scope.launch {
                _connectedPrinter.value?.let { printer ->
                    printerRepository.updatePrinter(printer.copy(status = PrinterStatus.DISCONNECTED))
                }
                _connectedPrinter.value = null
            }
        }
    }

    fun onPermissionResult(device: UsbDevice, granted: Boolean) {
        scope.launch {
            permissionHandler.onPermissionResult(device, granted)
            if (granted) {
                _usbState.value = UsbState.PermissionGranted(device)
                val brand = vidPidDatabase.getVendorName("%04x".format(device.vendorId))
                val printer = Printer(
                    deviceName = device.deviceName,
                    vendorId = device.vendorId,
                    productId = device.productId,
                    brandName = brand,
                    modelName = device.productName ?: "Unknown Printer",
                    manufacturerName = device.manufacturerName,
                    serialNumber = device.serialNumber,
                    status = PrinterStatus.DETECTED
                )
                val existingId = printerRepository.getPrinterByVidPid(
                    printer.vid, printer.pid
                )?.id ?: 0
                val savedId = printerRepository.savePrinter(printer.copy(id = existingId))
                val savedPrinter = printer.copy(id = savedId)
                _connectedPrinter.value = savedPrinter
                _usbState.value = UsbState.Connected(savedPrinter)
            } else {
                _usbState.value = UsbState.PermissionDenied(device)
            }
        }
    }

    fun openConnection(printer: Printer): UsbPrinterTransport? {
        val device = usbManager.deviceList.values
            .firstOrNull { it.deviceName == printer.deviceName } ?: return null
        val endpoints = detector.findPrinterEndpoints(device) ?: return null
        val connection = permissionHandler.openDevice(device) ?: return null
        return UsbPrinterTransport(
            connection,
            endpoints.usbInterface,
            endpoints.bulkOut,
            endpoints.bulkIn
        )
    }

    fun updateConnectedPrinterDriver(driverId: Long) {
        _connectedPrinter.value = _connectedPrinter.value?.copy(
            hasDriver = true,
            driverId = driverId,
            status = PrinterStatus.DRIVER_FOUND
        )
    }

    fun getDeviceDescriptor(printer: Printer): Map<String, Any>? {
        val device = usbManager.deviceList.values
            .firstOrNull { it.deviceName == printer.deviceName } ?: return null
        return detector.getDeviceDescriptor(device)
    }
}
