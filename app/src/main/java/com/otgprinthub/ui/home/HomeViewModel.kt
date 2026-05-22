package com.otgprinthub.ui.home

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otgprinthub.domain.model.PrintJob
import com.otgprinthub.domain.model.Printer
import com.otgprinthub.domain.repository.PrintJobRepository
import com.otgprinthub.domain.usecase.FindDriverUseCase
import com.otgprinthub.driver.DriverManager
import com.otgprinthub.ui.settings.dataStore
import com.otgprinthub.usb.UsbPrinterManager
import com.otgprinthub.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usbPrinterManager: UsbPrinterManager,
    private val driverManager: DriverManager,
    private val printJobRepository: PrintJobRepository
) : ViewModel() {

    val connectedPrinter: StateFlow<Printer?> = usbPrinterManager.connectedPrinter
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val recentJobs: StateFlow<List<PrintJob>> = printJobRepository.getAllJobs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val usbState = usbPrinterManager.usbState

    private val _autoDriverSearchState = MutableStateFlow<FindDriverUseCase.DriverSearchState?>(null)
    val autoDriverSearchState: StateFlow<FindDriverUseCase.DriverSearchState?> = _autoDriverSearchState.asStateFlow()

    private var lastAutoSearchedVidPid: String? = null

    init {
        viewModelScope.launch {
            usbPrinterManager.connectedPrinter.collect { printer ->
                if (printer != null) {
                    val key = "${printer.vid}:${printer.pid}"
                    if (key != lastAutoSearchedVidPid) {
                        lastAutoSearchedVidPid = key
                        autoSearchDriver(printer)
                    }
                }
            }
        }
    }

    private suspend fun autoSearchDriver(printer: Printer) {
        val prefs = context.dataStore.data.first()
        val autoDownload = prefs[booleanPreferencesKey(Constants.PREF_AUTO_DOWNLOAD_DRIVERS)] ?: true
        if (!autoDownload) return

        driverManager.findAndCacheDriver(printer).collect { state ->
            _autoDriverSearchState.value = state
            if (state is FindDriverUseCase.DriverSearchState.DriverFound) {
                usbPrinterManager.updateConnectedPrinterDriver(state.driver.id)
            }
        }
    }
}
