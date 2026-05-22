package com.otgprinthub.ui.preview

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otgprinthub.domain.model.ColorMode
import com.otgprinthub.domain.model.Driver
import com.otgprinthub.domain.model.FileType
import com.otgprinthub.domain.model.FitMode
import com.otgprinthub.domain.model.JobStatus
import com.otgprinthub.domain.model.Orientation
import com.otgprinthub.domain.model.PaperSize
import com.otgprinthub.domain.model.PrintJob
import com.otgprinthub.domain.model.PrintQuality
import com.otgprinthub.domain.model.PrintSettings
import com.otgprinthub.domain.repository.DriverRepository
import com.otgprinthub.domain.usecase.PrintDocumentUseCase
import com.otgprinthub.print.PrintEngine
import com.otgprinthub.usb.UsbPrinterManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PrintPreviewViewModel @Inject constructor(
    private val usbPrinterManager: UsbPrinterManager,
    private val printDocumentUseCase: PrintDocumentUseCase,
    private val driverRepository: DriverRepository,
    private val printEngine: PrintEngine
) : ViewModel() {

    val connectedPrinter = usbPrinterManager.connectedPrinter

    private val _settings = MutableStateFlow(PrintSettings())
    val settings: StateFlow<PrintSettings> = _settings.asStateFlow()

    private val _printState = MutableStateFlow<PrintState>(PrintState.Idle)
    val printState: StateFlow<PrintState> = _printState.asStateFlow()

    sealed class PrintState {
        data object Idle : PrintState()
        data class Printing(val progress: Int, val message: String) : PrintState()
        data object Done : PrintState()
        data class Failed(val error: String) : PrintState()
    }

    fun updateSettings(settings: PrintSettings) { _settings.value = settings }

    fun print(fileUri: Uri, fileType: FileType) {
        val printer = connectedPrinter.value ?: run {
            _printState.value = PrintState.Failed("No printer connected")
            return
        }

        viewModelScope.launch {
            _printState.value = PrintState.Printing(0, "Preparing job...")
            try {
                val job = printDocumentUseCase(
                    fileUri = fileUri,
                    fileType = fileType,
                    printerVid = printer.vid,
                    printerPid = printer.pid,
                    printerName = printer.modelName,
                    settings = _settings.value
                )

                val driver = driverRepository.getDriverByVidPid(printer.vid, printer.pid)
                    ?: driverRepository.getGenericDrivers().firstOrNull()
                    ?: run {
                        _printState.value = PrintState.Failed("No driver available")
                        return@launch
                    }

                val transport = usbPrinterManager.openConnection(printer)
                    ?: run {
                        _printState.value = PrintState.Failed("Cannot open USB connection")
                        return@launch
                    }

                printEngine.print(job, driver, transport).collect { progress ->
                    when (progress) {
                        is PrintEngine.PrintProgress.Preparing ->
                            _printState.value = PrintState.Printing(5, progress.message)
                        is PrintEngine.PrintProgress.Rendering ->
                            _printState.value = PrintState.Printing(
                                (progress.page.toFloat() / progress.total * 50).toInt(), "Rendering page ${progress.page}/${progress.total}"
                            )
                        is PrintEngine.PrintProgress.Sending ->
                            _printState.value = PrintState.Printing(
                                (50 + progress.bytesSent.toFloat() / progress.totalBytes * 50).toInt(),
                                "Sending ${progress.bytesSent / 1024}/${progress.totalBytes / 1024} KB"
                            )
                        is PrintEngine.PrintProgress.Complete ->
                            _printState.value = PrintState.Done
                        is PrintEngine.PrintProgress.Failed ->
                            _printState.value = PrintState.Failed(progress.error)
                    }
                }
            } catch (e: Exception) {
                _printState.value = PrintState.Failed(e.message ?: "Unknown error")
            }
        }
    }

    fun resetPrintState() { _printState.value = PrintState.Idle }
}
