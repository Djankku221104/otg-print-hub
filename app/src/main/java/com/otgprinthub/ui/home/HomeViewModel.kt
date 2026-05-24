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
import com.otgprinthub.print.TestPageGenerator
import com.otgprinthub.ui.settings.dataStore
import com.otgprinthub.usb.UsbPrinterManager
import com.otgprinthub.util.AppLogger
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

    sealed class TestPrintState {
        data object Idle : TestPrintState()
        data object Sending : TestPrintState()
        data object Done : TestPrintState()
        data class Failed(val error: String) : TestPrintState()
    }

    private val _testPrintState = MutableStateFlow<TestPrintState>(TestPrintState.Idle)
    val testPrintState: StateFlow<TestPrintState> = _testPrintState.asStateFlow()

    fun printTestPage(type: TestPageGenerator.TestType) {
        val printer = usbPrinterManager.connectedPrinter.value ?: run {
            _testPrintState.value = TestPrintState.Failed("No printer connected")
            return
        }
        viewModelScope.launch {
            _testPrintState.value = TestPrintState.Sending
            AppLogger.separator("TestPage:${type.name}")
            AppLogger.i("HomeVM", "Printer: ${printer.modelName} VID=${printer.vid} PID=${printer.pid}")
            val transport = usbPrinterManager.openConnection(printer) ?: run {
                AppLogger.e("HomeVM", "Cannot open USB connection")
                _testPrintState.value = TestPrintState.Failed("Cannot open USB connection")
                return@launch
            }
            try {
                val bytes = TestPageGenerator.generate(type)
                AppLogger.i("HomeVM", "Test bytes: ${bytes.size} (${bytes.size / 1024} KB)")
                AppLogger.i("HomeVM", "Header: " + bytes.take(16).joinToString(" ") { "%02X".format(it.toInt() and 0xFF) })
                var failed = false
                transport.sendData(bytes).collect { result ->
                    when (result) {
                        is com.otgprinthub.usb.UsbPrinterTransport.TransferResult.Error -> {
                            failed = true
                            AppLogger.e("HomeVM", "USB Error: ${result.message}")
                            _testPrintState.value = TestPrintState.Failed(result.message)
                        }
                        is com.otgprinthub.usb.UsbPrinterTransport.TransferResult.Complete -> {
                            AppLogger.i("HomeVM", "USB send complete — check printer!")
                            if (!failed) _testPrintState.value = TestPrintState.Done
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("HomeVM", "Exception: ${e.message}")
                _testPrintState.value = TestPrintState.Failed(e.message ?: "Unknown error")
            } finally {
                transport.close()
            }
        }
    }

    fun resetTestPrintState() { _testPrintState.value = TestPrintState.Idle }

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
