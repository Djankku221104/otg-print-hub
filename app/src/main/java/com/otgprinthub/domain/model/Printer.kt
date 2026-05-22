package com.otgprinthub.domain.model

data class Printer(
    val id: Long = 0,
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val vid: String = "%04x".format(vendorId),
    val pid: String = "%04x".format(productId),
    val brandName: String = "Unknown",
    val modelName: String = "Unknown Printer",
    val usbClass: Int = 7,
    val usbSubclass: Int = 1,
    val usbProtocol: Int = 1,
    val manufacturerName: String? = null,
    val serialNumber: String? = null,
    val status: PrinterStatus = PrinterStatus.DETECTED,
    val driverId: Long? = null,
    val hasDriver: Boolean = false,
    val lastConnected: Long = System.currentTimeMillis()
)

enum class PrinterStatus {
    DETECTED,
    REQUESTING_PERMISSION,
    PERMISSION_DENIED,
    SEARCHING_DRIVER,
    DRIVER_FOUND,
    DRIVER_NOT_FOUND,
    READY,
    PRINTING,
    ERROR,
    DISCONNECTED
}
