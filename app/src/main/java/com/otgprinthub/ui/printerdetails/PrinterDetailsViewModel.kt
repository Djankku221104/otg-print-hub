package com.otgprinthub.ui.printerdetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otgprinthub.domain.model.Driver
import com.otgprinthub.domain.model.Printer
import com.otgprinthub.domain.repository.DriverRepository
import com.otgprinthub.domain.repository.PrinterRepository
import com.otgprinthub.usb.UsbPrinterManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PrinterDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val printerRepository: PrinterRepository,
    private val driverRepository: DriverRepository,
    private val usbPrinterManager: UsbPrinterManager
) : ViewModel() {

    private val printerId: Long = checkNotNull(savedStateHandle["printerId"])

    private val _printer = MutableStateFlow<Printer?>(null)
    val printer: StateFlow<Printer?> = _printer.asStateFlow()

    private val _driver = MutableStateFlow<Driver?>(null)
    val driver: StateFlow<Driver?> = _driver.asStateFlow()

    val usbDescriptor = MutableStateFlow<Map<String, Any>?>(null)

    init {
        viewModelScope.launch {
            // Attempt to find by id - approximate via vid/pid scan
            printerRepository.getAllPrinters().collect { printers ->
                val found = printers.firstOrNull { it.id == printerId }
                _printer.value = found
                found?.let { p ->
                    _driver.value = driverRepository.getDriverByVidPid(p.vid, p.pid)
                    usbDescriptor.value = usbPrinterManager.getDeviceDescriptor(p)
                }
            }
        }
    }

    fun deletePrinter() {
        viewModelScope.launch {
            printerRepository.deletePrinter(printerId)
        }
    }
}
