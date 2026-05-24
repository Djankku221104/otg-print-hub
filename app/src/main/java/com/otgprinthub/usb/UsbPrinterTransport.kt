package com.otgprinthub.usb

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.util.Log
import com.otgprinthub.util.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class UsbPrinterTransport(
    private val connection: UsbDeviceConnection,
    private val usbInterface: UsbInterface,
    private val bulkOut: UsbEndpoint,
    private val bulkIn: UsbEndpoint?
) {
    private val TAG = "UsbPrinterTransport"
    private val CHUNK_SIZE = 16384
    private val TIMEOUT_MS = 5000
    private val MAX_RETRIES = 3

    sealed class TransferResult {
        data class Success(val bytesTransferred: Int) : TransferResult()
        data class Progress(val bytesSent: Long, val totalBytes: Long) : TransferResult()
        data class Error(val message: String, val cause: Throwable? = null) : TransferResult()
        data object Complete : TransferResult()
    }

    /**
     * USB Printer Class control requests (per USB Printer Class spec 1.1).
     * These must be called before bulk data transfer — same as all working apps do.
     *
     * bmRequestType breakdown:
     *   0x21 = 0b00100001 = class | interface | host→device
     *   0xA1 = 0b10100001 = class | interface | device→host
     */
    private val REQ_GET_DEVICE_ID = 0x00
    private val REQ_GET_PORT_STATUS = 0x01
    private val REQ_SOFT_RESET = 0x02

    /**
     * Initialise the USB printer interface exactly as the USB Printer Class
     * spec requires. Other apps (PrintHand, etc.) all do this before sending data.
     *
     * Returns false if the interface cannot be claimed.
     */
    fun initPrinter(): Boolean {
        val claimed = connection.claimInterface(usbInterface, true)
        if (!claimed) {
            Log.e(TAG, "Cannot claim interface ${usbInterface.id}")
            AppLogger.e(TAG, "Cannot claim USB interface ${usbInterface.id}")
            return false
        }

        // 1. GET_DEVICE_ID — wakes up the printer's USB print stack, returns
        //    the IEEE 1284 device ID string (e.g. "MFG:EPSON;MDL:L1455;...")
        val idBuf = ByteArray(1024)
        val idLen = connection.controlTransfer(
            0xA1,                  // bmRequestType: class, interface, device→host
            REQ_GET_DEVICE_ID,     // bRequest
            0,                     // wValue (config index, always 0)
            usbInterface.id,       // wIndex (interface number)
            idBuf, idBuf.size,
            TIMEOUT_MS
        )
        if (idLen > 2) {
            val id = String(idBuf, 2, idLen - 2, Charsets.US_ASCII)
            Log.i(TAG, "Printer device ID: $id")
            AppLogger.i(TAG, "DeviceID: $id")
        } else {
            AppLogger.w(TAG, "GET_DEVICE_ID returned $idLen bytes (expected >2)")
        }

        // 2. SOFT_RESET — clears printer data path; essential after a failed job
        //    or when the printer is stuck. Equivalent to power-cycling the USB link.
        val resetResult = connection.controlTransfer(
            0x21,                  // bmRequestType: class, interface, host→device
            REQ_SOFT_RESET,        // bRequest
            0, usbInterface.id,
            null, 0,
            TIMEOUT_MS
        )
        Log.d(TAG, "SOFT_RESET result: $resetResult")
        AppLogger.i(TAG, "SOFT_RESET result: $resetResult")

        // Give the printer 200 ms to complete its internal reset
        Thread.sleep(200)

        // 3. GET_PORT_STATUS — reads 1-byte status; clears any pending error bits
        val statusBuf = ByteArray(1)
        connection.controlTransfer(
            0xA1, REQ_GET_PORT_STATUS,
            0, usbInterface.id,
            statusBuf, 1, TIMEOUT_MS
        )
        val portStatus = statusBuf[0].toInt() and 0xFF
        Log.d(TAG, "Port status: 0x${portStatus.toString(16)}")
        AppLogger.i(TAG, "Port status: 0x${portStatus.toString(16)} " +
            "(paperEmpty=${portStatus and 0x20 != 0}, select=${portStatus and 0x10 != 0}, notError=${portStatus and 0x08 != 0})")

        return true
    }

    fun sendData(data: ByteArray): Flow<TransferResult> = flow {
        val totalBytes = data.size.toLong()
        var bytesSent = 0L

        // Claim interface + initialise printer (SOFT_RESET + GET_DEVICE_ID)
        AppLogger.i(TAG, "sendData: ${totalBytes} bytes, chunk=${CHUNK_SIZE}")
        if (!initPrinter()) {
            emit(TransferResult.Error("Failed to claim USB interface"))
            return@flow
        }

        try {
            var offset = 0
            while (offset < data.size) {
                val chunkSize = minOf(CHUNK_SIZE, data.size - offset)
                val chunk = data.copyOfRange(offset, offset + chunkSize)

                var transferred = -1
                var retries = 0
                while (retries < MAX_RETRIES && transferred < 0) {
                    transferred = connection.bulkTransfer(bulkOut, chunk, chunk.size, TIMEOUT_MS)
                    if (transferred < 0) {
                        retries++
                        Log.w(TAG, "Transfer failed, retry $retries/$MAX_RETRIES")
                        AppLogger.w(TAG, "bulkTransfer fail retry $retries at offset $offset")
                    }
                }

                if (transferred < 0) {
                    AppLogger.e(TAG, "Transfer FAILED after $MAX_RETRIES retries at offset $offset")
                    emit(TransferResult.Error("Transfer failed after $MAX_RETRIES retries at offset $offset"))
                    return@flow
                }

                bytesSent += transferred
                offset += chunkSize
                if (bytesSent % (CHUNK_SIZE * 8) < CHUNK_SIZE) {
                    AppLogger.d(TAG, "USB: ${bytesSent}/$totalBytes bytes sent")
                }
                emit(TransferResult.Progress(bytesSent, totalBytes))
            }
            AppLogger.i(TAG, "USB sendData COMPLETE: $totalBytes bytes")
            emit(TransferResult.Complete)
        } catch (e: Exception) {
            Log.e(TAG, "USB transfer exception", e)
            AppLogger.e(TAG, "USB exception: ${e.message}")
            emit(TransferResult.Error("Transfer exception: ${e.message}", e))
        } finally {
            connection.releaseInterface(usbInterface)
        }
    }

    fun readStatus(maxBytes: Int = 1024): ByteArray? {
        val bulkInEndpoint = bulkIn ?: return null
        return try {
            val buffer = ByteArray(maxBytes)
            val read = connection.bulkTransfer(bulkInEndpoint, buffer, buffer.size, TIMEOUT_MS)
            if (read > 0) buffer.copyOf(read) else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read printer status", e)
            null
        }
    }

    fun sendAndReceive(data: ByteArray): ByteArray? {
        val claimed = connection.claimInterface(usbInterface, true)
        if (!claimed) return null
        return try {
            val transferred = connection.bulkTransfer(bulkOut, data, data.size, TIMEOUT_MS)
            if (transferred < 0) return null
            readStatus()
        } finally {
            connection.releaseInterface(usbInterface)
        }
    }

    fun close() {
        try {
            connection.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing USB connection", e)
        }
    }

    class Factory @Inject constructor(
        private val detector: UsbPrinterDetector
    ) {
        fun create(
            connection: UsbDeviceConnection,
            usbInterface: UsbInterface,
            bulkOut: UsbEndpoint,
            bulkIn: UsbEndpoint?
        ): UsbPrinterTransport {
            return UsbPrinterTransport(connection, usbInterface, bulkOut, bulkIn)
        }
    }
}
