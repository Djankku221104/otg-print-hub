package com.otgprinthub.ui.detection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otgprinthub.domain.model.Driver
import com.otgprinthub.domain.model.Printer
import com.otgprinthub.domain.usecase.FindDriverUseCase
import com.otgprinthub.driver.DriverManager
import com.otgprinthub.usb.UsbPrinterManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PrinterDetectionViewModel @Inject constructor(
    private val usbPrinterManager: UsbPrinterManager,
    private val driverManager: DriverManager
) : ViewModel() {

    val connectedPrinter = usbPrinterManager.connectedPrinter
    val usbState = usbPrinterManager.usbState

    private val _driverSearchState = MutableStateFlow<FindDriverUseCase.DriverSearchState?>(null)
    val driverSearchState: StateFlow<FindDriverUseCase.DriverSearchState?> = _driverSearchState.asStateFlow()

    private val _currentDriver = MutableStateFlow<Driver?>(null)
    val currentDriver: StateFlow<Driver?> = _currentDriver.asStateFlow()

    fun scanForPrinters() {
        usbPrinterManager.scanConnectedDevices()
    }

    fun findDriver(printer: Printer) {
        viewModelScope.launch {
            driverManager.findAndCacheDriver(printer).collect { state ->
                _driverSearchState.value = state
                if (state is FindDriverUseCase.DriverSearchState.DriverFound) {
                    _currentDriver.value = state.driver
                }
            }
        }
    }
}
