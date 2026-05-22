package com.otgprinthub.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.otgprinthub.util.Constants
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class UsbBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var usbPrinterManager: UsbPrinterManager

    override fun onReceive(context: Context, intent: Intent) {
        val device = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }

        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                Log.d(TAG, "USB device attached: ${device?.deviceName}")
                device?.let { usbPrinterManager.onDeviceAttached(it) }
            }
            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                Log.d(TAG, "USB device detached: ${device?.deviceName}")
                device?.let { usbPrinterManager.onDeviceDetached(it) }
            }
            Constants.ACTION_USB_PERMISSION -> {
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                Log.d(TAG, "USB permission result: $granted for ${device?.deviceName}")
                device?.let { usbPrinterManager.onPermissionResult(it, granted) }
            }
        }
    }

    companion object {
        private const val TAG = "UsbBroadcastReceiver"
    }
}
