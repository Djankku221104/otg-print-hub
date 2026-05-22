package com.otgprinthub.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otgprinthub.domain.model.PrintJob
import com.otgprinthub.domain.model.Printer
import com.otgprinthub.domain.repository.PrintJobRepository
import com.otgprinthub.usb.UsbPrinterManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val usbPrinterManager: UsbPrinterManager,
    private val printJobRepository: PrintJobRepository
) : ViewModel() {

    val connectedPrinter: StateFlow<Printer?> = usbPrinterManager.connectedPrinter
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val recentJobs: StateFlow<List<PrintJob>> = printJobRepository.getAllJobs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val usbState = usbPrinterManager.usbState
}
