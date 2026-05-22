package com.otgprinthub.usb

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.util.Log
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

    fun sendData(data: ByteArray): Flow<TransferResult> = flow {
        val totalBytes = data.size.toLong()
        var bytesSent = 0L

        val claimed = connection.claimInterface(usbInterface, true)
        if (!claimed) {
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
                    }
                }

                if (transferred < 0) {
                    emit(TransferResult.Error("Transfer failed after $MAX_RETRIES retries at offset $offset"))
                    return@flow
                }

                bytesSent += transferred
                offset += chunkSize
                emit(TransferResult.Progress(bytesSent, totalBytes))
            }
            emit(TransferResult.Complete)
        } catch (e: Exception) {
            Log.e(TAG, "USB transfer exception", e)
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
        val connection = connection
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
