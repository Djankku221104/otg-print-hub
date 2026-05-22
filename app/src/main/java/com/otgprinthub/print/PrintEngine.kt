package com.otgprinthub.print

import android.content.Context
import android.net.Uri
import com.otgprinthub.domain.model.Driver
import com.otgprinthub.domain.model.FileType
import com.otgprinthub.domain.model.JobStatus
import com.otgprinthub.domain.model.PrintJob
import com.otgprinthub.domain.model.PrintProtocol
import com.otgprinthub.domain.model.PrintSettings
import com.otgprinthub.domain.repository.PrintJobRepository
import com.otgprinthub.print.adapters.DirectPdfAdapter
import com.otgprinthub.print.adapters.EscPAdapter
import com.otgprinthub.print.adapters.EscPosAdapter
import com.otgprinthub.print.adapters.GenericFallbackAdapter
import com.otgprinthub.print.adapters.PclAdapter
import com.otgprinthub.print.adapters.PostScriptAdapter
import com.otgprinthub.print.adapters.PrintAdapter
import com.otgprinthub.print.adapters.RawTextAdapter
import com.otgprinthub.print.renderer.DocumentRenderer
import com.otgprinthub.usb.UsbPrinterTransport
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrintEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentRenderer: DocumentRenderer,
    private val printJobRepository: PrintJobRepository
) {
    sealed class PrintProgress {
        data class Preparing(val message: String) : PrintProgress()
        data class Rendering(val page: Int, val total: Int) : PrintProgress()
        data class Sending(val bytesSent: Long, val totalBytes: Long) : PrintProgress()
        data object Complete : PrintProgress()
        data class Failed(val error: String) : PrintProgress()
    }

    fun print(
        job: PrintJob,
        driver: Driver,
        transport: UsbPrinterTransport
    ): Flow<PrintProgress> = flow {
        try {
            printJobRepository.updateJobStatus(job.id, JobStatus.PREPARING, 0)
            emit(PrintProgress.Preparing("Selecting protocol: ${driver.protocol.displayName}"))

            val adapter = selectAdapter(driver, context)
            val fileUri = Uri.parse(job.fileUri)

            emit(PrintProgress.Preparing("Rendering document..."))
            printJobRepository.updateJobStatus(job.id, JobStatus.PRINTING, 5)

            val printData = when (job.fileType) {
                FileType.PDF -> {
                    if (driver.protocol == PrintProtocol.DIRECT_PDF) {
                        // Send PDF bytes directly
                        (adapter as DirectPdfAdapter).readPdfBytes(fileUri) ?: throw Exception("Cannot read PDF file")
                    } else {
                        renderPdfToPrintData(fileUri, adapter, job.settings, job.id)
                    }
                }
                FileType.IMAGE -> renderImageToPrintData(fileUri, adapter, job.settings)
                FileType.TEXT -> {
                    val text = if (fileUri.scheme == "file")
                        java.io.File(fileUri.path!!).readText()
                    else
                        context.contentResolver.openInputStream(fileUri)?.bufferedReader()?.readText()
                            ?: throw Exception("Cannot read text file")
                    adapter.buildTextData(text, job.settings)
                }
                FileType.UNKNOWN -> throw Exception("Unsupported file type")
            }

            val totalBytes = printData.size.toLong()
            emit(PrintProgress.Preparing("Sending ${totalBytes / 1024} KB to printer..."))

            transport.sendData(printData).collect { result ->
                when (result) {
                    is UsbPrinterTransport.TransferResult.Progress -> {
                        val progress = ((result.bytesSent.toFloat() / result.totalBytes) * 90 + 10).toInt()
                        printJobRepository.updateJobStatus(job.id, JobStatus.PRINTING, progress)
                        emit(PrintProgress.Sending(result.bytesSent, result.totalBytes))
                    }
                    is UsbPrinterTransport.TransferResult.Complete -> {
                        printJobRepository.updateJobStatus(job.id, JobStatus.COMPLETED, 100)
                        emit(PrintProgress.Complete)
                    }
                    is UsbPrinterTransport.TransferResult.Error -> {
                        printJobRepository.updateJobStatus(job.id, JobStatus.FAILED, 0, result.message)
                        emit(PrintProgress.Failed(result.message))
                    }
                    else -> {}
                }
            }
        } catch (e: Exception) {
            printJobRepository.updateJobStatus(job.id, JobStatus.FAILED, 0, e.message)
            emit(PrintProgress.Failed(e.message ?: "Unknown error"))
        } finally {
            transport.close()
        }
    }

    private suspend fun renderPdfToPrintData(
        uri: Uri,
        adapter: PrintAdapter,
        settings: PrintSettings,
        jobId: Long
    ): ByteArray {
        val pages = documentRenderer.renderPdf(uri, settings)
        val pageData = mutableListOf<ByteArray>()
        pages.forEachIndexed { index, bitmap ->
            pageData.add(adapter.buildImageData(bitmap, settings))
            bitmap.recycle()
        }
        return adapter.buildFullPrintJob(pageData)
    }

    private fun renderImageToPrintData(
        uri: Uri,
        adapter: PrintAdapter,
        settings: PrintSettings
    ): ByteArray {
        val bitmap = documentRenderer.renderImage(uri, settings)
        val imageData = adapter.buildImageData(bitmap, settings)
        bitmap.recycle()
        return adapter.buildInitSequence() + imageData + adapter.buildEndSequence()
    }

    fun selectAdapter(driver: Driver, context: Context? = null): PrintAdapter {
        return when (driver.protocol) {
            PrintProtocol.ESCPOS -> EscPosAdapter(driver)
            PrintProtocol.PCL5, PrintProtocol.PCL6, PrintProtocol.PCL3 -> PclAdapter(driver)
            PrintProtocol.ESCP, PrintProtocol.ESCP2 -> EscPAdapter(driver)
            PrintProtocol.POSTSCRIPT, PrintProtocol.POSTSCRIPT3 -> PostScriptAdapter(driver)
            PrintProtocol.DIRECT_PDF -> DirectPdfAdapter(driver, context)
            PrintProtocol.RAW -> RawTextAdapter(driver)
            else -> GenericFallbackAdapter(driver)
        }
    }
}
