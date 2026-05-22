package com.otgprinthub.data.repository

import com.otgprinthub.data.local.dao.PrinterDao
import com.otgprinthub.data.local.entity.PrinterEntity
import com.otgprinthub.domain.model.Printer
import com.otgprinthub.domain.model.PrinterStatus
import com.otgprinthub.domain.repository.PrinterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PrinterRepositoryImpl @Inject constructor(
    private val printerDao: PrinterDao
) : PrinterRepository {

    override fun getConnectedPrinters(): Flow<List<Printer>> =
        printerDao.getConnectedPrinters().map { it.map(::entityToModel) }

    override fun getAllPrinters(): Flow<List<Printer>> =
        printerDao.getAllPrinters().map { it.map(::entityToModel) }

    override suspend fun getPrinterByVidPid(vid: String, pid: String): Printer? =
        printerDao.getPrinterByVidPid(vid, pid)?.let(::entityToModel)

    override suspend fun savePrinter(printer: Printer): Long =
        printerDao.insertPrinter(modelToEntity(printer))

    override suspend fun updatePrinter(printer: Printer) =
        printerDao.updatePrinter(modelToEntity(printer))

    override suspend fun deletePrinter(printerId: Long) =
        printerDao.deletePrinter(printerId)

    override suspend fun getLastConnectedPrinter(): Printer? =
        printerDao.getLastConnectedPrinter()?.let(::entityToModel)

    private fun entityToModel(entity: PrinterEntity) = Printer(
        id = entity.id,
        deviceName = entity.deviceName,
        vendorId = entity.vendorId,
        productId = entity.productId,
        vid = entity.vid,
        pid = entity.pid,
        brandName = entity.brandName,
        modelName = entity.modelName,
        usbClass = entity.usbClass,
        usbSubclass = entity.usbSubclass,
        usbProtocol = entity.usbProtocol,
        manufacturerName = entity.manufacturerName,
        serialNumber = entity.serialNumber,
        status = runCatching { PrinterStatus.valueOf(entity.status) }.getOrDefault(PrinterStatus.DISCONNECTED),
        driverId = entity.driverId,
        hasDriver = entity.hasDriver,
        lastConnected = entity.lastConnected
    )

    private fun modelToEntity(model: Printer) = PrinterEntity(
        id = model.id,
        deviceName = model.deviceName,
        vendorId = model.vendorId,
        productId = model.productId,
        vid = model.vid,
        pid = model.pid,
        brandName = model.brandName,
        modelName = model.modelName,
        usbClass = model.usbClass,
        usbSubclass = model.usbSubclass,
        usbProtocol = model.usbProtocol,
        manufacturerName = model.manufacturerName,
        serialNumber = model.serialNumber,
        status = model.status.name,
        driverId = model.driverId,
        hasDriver = model.hasDriver,
        lastConnected = model.lastConnected
    )
}
