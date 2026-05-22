package com.otgprinthub.domain.repository

import com.otgprinthub.domain.model.Printer
import kotlinx.coroutines.flow.Flow

interface PrinterRepository {
    fun getConnectedPrinters(): Flow<List<Printer>>
    fun getAllPrinters(): Flow<List<Printer>>
    suspend fun getPrinterByVidPid(vid: String, pid: String): Printer?
    suspend fun savePrinter(printer: Printer): Long
    suspend fun updatePrinter(printer: Printer)
    suspend fun deletePrinter(printerId: Long)
    suspend fun getLastConnectedPrinter(): Printer?
}
