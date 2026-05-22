package com.otgprinthub.usb

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.otgprinthub.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsbPermissionHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usbManager: UsbManager
) {
    private val _permissionResults = MutableSharedFlow<PermissionResult>(extraBufferCapacity = 10)
    val permissionResults: SharedFlow<PermissionResult> = _permissionResults

    data class PermissionResult(val device: UsbDevice, val granted: Boolean)

    fun hasPermission(device: UsbDevice): Boolean = usbManager.hasPermission(device)

    fun requestPermission(device: UsbDevice) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val permissionIntent = PendingIntent.getBroadcast(
            context,
            device.deviceId,
            Intent(Constants.ACTION_USB_PERMISSION).apply {
                setPackage(context.packageName)
            },
            flags
        )

        usbManager.requestPermission(device, permissionIntent)
    }

    suspend fun onPermissionResult(device: UsbDevice, granted: Boolean) {
        _permissionResults.emit(PermissionResult(device, granted))
    }

    fun openDevice(device: UsbDevice) = usbManager.openDevice(device)
}
