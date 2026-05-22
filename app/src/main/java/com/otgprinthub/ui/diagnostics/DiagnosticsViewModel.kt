package com.otgprinthub.ui.diagnostics

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.otgprinthub.domain.model.Printer
import com.otgprinthub.usb.UsbPrinterManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usbPrinterManager: UsbPrinterManager,
    private val gson: Gson
) : ViewModel() {

    val connectedPrinter = usbPrinterManager.connectedPrinter
    val usbState = usbPrinterManager.usbState

    private val _descriptor = MutableStateFlow<Map<String, Any>?>(null)
    val descriptor: StateFlow<Map<String, Any>?> = _descriptor.asStateFlow()

    private val _exportPath = MutableStateFlow<String?>(null)
    val exportPath: StateFlow<String?> = _exportPath.asStateFlow()

    init {
        viewModelScope.launch {
            usbPrinterManager.connectedPrinter.collect { printer ->
                printer?.let {
                    _descriptor.value = usbPrinterManager.getDeviceDescriptor(it)
                }
            }
        }
    }

    fun exportDiagnostics() {
        viewModelScope.launch {
            val data = mapOf(
                "timestamp" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                "printer" to (connectedPrinter.value?.let { gson.toJsonTree(it) } ?: "none"),
                "usb_state" to usbState.value.toString(),
                "usb_descriptor" to (descriptor.value ?: emptyMap<String, Any>())
            )
            val json = gson.toJson(data)
            val file = File(context.getExternalFilesDir(null), "diagnostics_${System.currentTimeMillis()}.json")
            file.writeText(json)
            _exportPath.value = file.absolutePath
        }
    }
}
