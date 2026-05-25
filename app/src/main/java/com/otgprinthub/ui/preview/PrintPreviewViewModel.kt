package com.otgprinthub.ui.preview

import android.content.Context
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
import com.otgprinthub.domain.model.PrintProtocol
import com.otgprinthub.domain.model.PrintQuality
import com.otgprinthub.domain.model.PrintSettings
import com.otgprinthub.domain.repository.DriverRepository
import com.otgprinthub.domain.usecase.PrintDocumentUseCase
import com.otgprinthub.print.PrintEngine
import com.otgprinthub.printer.PrintHelper
import com.otgprinthub.usb.UsbPrinterManager
import com.otgprinthub.usb.UsbPrinterTransport
import com.otgprinthub.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PrintPreviewViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
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

                if (driver.protocol == PrintProtocol.ESCP2) {
                    printViaEscpr(fileUri, transport, _settings.value)
                } else {
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
                }
            } catch (e: Exception) {
                _printState.value = PrintState.Failed(e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun printViaEscpr(fileUri: Uri, transport: UsbPrinterTransport, settings: PrintSettings = PrintSettings()) {
        val helper = PrintHelper(context)
        _printState.value = PrintState.Printing(5, "Rendering document…")

        val localUri = resolveToLocalUri(fileUri)
        AppLogger.i("PrintVM", "printViaEscpr: orig=${fileUri.scheme} local=${localUri.scheme}")

        val result = helper.printUri(localUri, transport, settings) { msg ->
            _printState.value = PrintState.Printing(50, msg)
        }
        _printState.value = if (result.isSuccess) PrintState.Done
                            else PrintState.Failed(result.exceptionOrNull()?.message ?: "Print failed")
        transport.close()
    }

    private suspend fun resolveToLocalUri(uri: Uri): Uri {
        if (uri.scheme == "file") return uri
        return withContext(Dispatchers.IO) {
            try {
                val mime = context.contentResolver.getType(uri) ?: ""
                val ext = when {
                    mime.contains("pdf")    -> ".pdf"
                    mime.startsWith("image") -> ".jpg"
                    mime.startsWith("text")  -> ".txt"
                    else -> {
                        val path = uri.path ?: ""
                        when {
                            path.endsWith(".pdf", true)  -> ".pdf"
                            path.endsWith(".png", true)  -> ".png"
                            path.endsWith(".jpg", true) || path.endsWith(".jpeg", true) -> ".jpg"
                            path.endsWith(".txt", true)  -> ".txt"
                            else -> ".bin"
                        }
                    }
                }
                val dest = File(context.cacheDir, "escpr_${System.currentTimeMillis()}$ext")
                context.contentResolver.openInputStream(uri)?.use { it.copyTo(dest.outputStream()) }
                if (dest.exists() && dest.length() > 0) {
                    AppLogger.i("PrintVM", "Copied to cache: ${dest.name} (${dest.length()} bytes)")
                    Uri.fromFile(dest)
                } else {
                    AppLogger.w("PrintVM", "Cache copy empty/failed, using original URI")
                    uri
                }
            } catch (e: Exception) {
                AppLogger.e("PrintVM", "resolveToLocalUri failed: ${e.message}")
                uri
            }
        }
    }

    fun resetPrintState() { _printState.value = PrintState.Idle }
}
