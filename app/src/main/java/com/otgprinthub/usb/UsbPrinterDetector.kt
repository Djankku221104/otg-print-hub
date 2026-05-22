package com.otgprinthub.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsbPrinterDetector @Inject constructor() {

    data class PrinterEndpoints(
        val usbInterface: UsbInterface,
        val bulkOut: UsbEndpoint,
        val bulkIn: UsbEndpoint?
    )

    fun isPrinterDevice(device: UsbDevice): Boolean {
        return findPrinterInterface(device) != null
    }

    fun findPrinterInterface(device: UsbDevice): UsbInterface? {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbManager.USB_CLASS_PRINTER) {
                return iface
            }
        }
        return null
    }

    fun findPrinterEndpoints(device: UsbDevice): PrinterEndpoints? {
        val printerInterface = findPrinterInterface(device) ?: return null
        var bulkOut: UsbEndpoint? = null
        var bulkIn: UsbEndpoint? = null

        for (i in 0 until printerInterface.endpointCount) {
            val endpoint = printerInterface.getEndpoint(i)
            if (endpoint.type == android.hardware.usb.UsbConstants.USB_ENDPOINT_XFER_BULK) {
                when (endpoint.direction) {
                    android.hardware.usb.UsbConstants.USB_DIR_OUT -> bulkOut = endpoint
                    android.hardware.usb.UsbConstants.USB_DIR_IN -> bulkIn = endpoint
                }
            }
        }

        return if (bulkOut != null) {
            PrinterEndpoints(printerInterface, bulkOut, bulkIn)
        } else {
            null
        }
    }

    fun getDeviceDescriptor(device: UsbDevice): Map<String, Any> {
        val descriptor = mutableMapOf<String, Any>()
        descriptor["deviceName"] = device.deviceName
        descriptor["vendorId"] = "0x%04X".format(device.vendorId)
        descriptor["productId"] = "0x%04X".format(device.productId)
        descriptor["deviceClass"] = device.deviceClass
        descriptor["deviceSubclass"] = device.deviceSubclass
        descriptor["deviceProtocol"] = device.deviceProtocol
        descriptor["manufacturerName"] = device.manufacturerName ?: "N/A"
        descriptor["productName"] = device.productName ?: "N/A"
        descriptor["serialNumber"] = device.serialNumber ?: "N/A"
        descriptor["interfaceCount"] = device.interfaceCount

        val interfaces = mutableListOf<Map<String, Any>>()
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            val ifaceInfo = mutableMapOf<String, Any>()
            ifaceInfo["id"] = iface.id
            ifaceInfo["class"] = iface.interfaceClass
            ifaceInfo["subclass"] = iface.interfaceSubclass
            ifaceInfo["protocol"] = iface.interfaceProtocol
            ifaceInfo["endpointCount"] = iface.endpointCount

            val endpoints = mutableListOf<Map<String, Any>>()
            for (j in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(j)
                endpoints.add(mapOf(
                    "address" to ep.address,
                    "type" to ep.type,
                    "direction" to ep.direction,
                    "maxPacketSize" to ep.maxPacketSize
                ))
            }
            ifaceInfo["endpoints"] = endpoints
            interfaces.add(ifaceInfo)
        }
        descriptor["interfaces"] = interfaces
        return descriptor
    }
}
