package com.otgprinthub.domain.usecase

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.otgprinthub.domain.model.Printer
import com.otgprinthub.domain.model.PrinterStatus
import com.otgprinthub.domain.repository.PrinterRepository
import javax.inject.Inject

class DetectPrinterUseCase @Inject constructor(
    private val printerRepository: PrinterRepository
) {
    suspend operator fun invoke(
        usbDevice: UsbDevice,
        brandName: String = "Unknown"
    ): Printer {
        val existingPrinter = printerRepository.getPrinterByVidPid(
            "%04x".format(usbDevice.vendorId),
            "%04x".format(usbDevice.productId)
        )

        val printer = Printer(
            id = existingPrinter?.id ?: 0,
            deviceName = usbDevice.deviceName,
            vendorId = usbDevice.vendorId,
            productId = usbDevice.productId,
            brandName = brandName,
            modelName = usbDevice.productName ?: existingPrinter?.modelName ?: "Unknown Printer",
            manufacturerName = usbDevice.manufacturerName,
            serialNumber = usbDevice.serialNumber,
            usbClass = getPrinterInterface(usbDevice)?.interfaceClass ?: 7,
            usbSubclass = getPrinterInterface(usbDevice)?.interfaceSubclass ?: 1,
            usbProtocol = getPrinterInterface(usbDevice)?.interfaceProtocol ?: 1,
            status = PrinterStatus.DETECTED,
            hasDriver = existingPrinter?.hasDriver ?: false,
            driverId = existingPrinter?.driverId
        )

        val id = printerRepository.savePrinter(printer)
        return printer.copy(id = id)
    }

    fun isPrinterDevice(usbDevice: UsbDevice): Boolean {
        return getPrinterInterface(usbDevice) != null
    }

    private fun getPrinterInterface(usbDevice: UsbDevice) =
        (0 until usbDevice.interfaceCount)
            .map { usbDevice.getInterface(it) }
            .firstOrNull { it.interfaceClass == UsbManager.USB_CLASS_PRINTER }
}
