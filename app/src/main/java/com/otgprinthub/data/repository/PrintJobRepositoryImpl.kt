package com.otgprinthub.data.repository

import com.otgprinthub.data.local.dao.PrintJobDao
import com.otgprinthub.data.local.entity.PrintJobEntity
import com.otgprinthub.domain.model.*
import com.otgprinthub.domain.repository.PrintJobRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PrintJobRepositoryImpl @Inject constructor(
    private val printJobDao: PrintJobDao
) : PrintJobRepository {

    override fun getAllJobs(): Flow<List<PrintJob>> =
        printJobDao.getAllJobs().map { it.map(::entityToModel) }

    override fun getActiveJobs(): Flow<List<PrintJob>> =
        printJobDao.getActiveJobs().map { it.map(::entityToModel) }

    override fun getJobsByStatus(status: JobStatus): Flow<List<PrintJob>> =
        printJobDao.getJobsByStatus(status.name).map { it.map(::entityToModel) }

    override suspend fun getJobById(id: Long): PrintJob? =
        printJobDao.getJobById(id)?.let(::entityToModel)

    override suspend fun saveJob(job: PrintJob): Long =
        printJobDao.insertJob(modelToEntity(job))

    override suspend fun updateJob(job: PrintJob) =
        printJobDao.updateJob(modelToEntity(job))

    override suspend fun updateJobStatus(id: Long, status: JobStatus, progress: Int, error: String?) =
        printJobDao.updateJobStatus(id, status.name, progress, error)

    override suspend fun deleteJob(id: Long) =
        printJobDao.deleteJob(id)

    override suspend fun clearCompletedJobs() =
        printJobDao.clearCompletedJobs()

    override suspend fun clearAllJobs() =
        printJobDao.clearAllJobs()

    private fun entityToModel(entity: PrintJobEntity) = PrintJob(
        id = entity.id,
        fileName = entity.fileName,
        fileUri = entity.fileUri,
        fileType = runCatching { FileType.valueOf(entity.fileType) }.getOrDefault(FileType.UNKNOWN),
        printerVid = entity.printerVid,
        printerPid = entity.printerPid,
        printerName = entity.printerName,
        settings = PrintSettings(
            paperSize = runCatching { PaperSize.valueOf(entity.paperSize) }.getOrDefault(PaperSize.A4),
            orientation = runCatching { Orientation.valueOf(entity.orientation) }.getOrDefault(Orientation.PORTRAIT),
            copies = entity.copies,
            colorMode = runCatching { ColorMode.valueOf(entity.colorMode) }.getOrDefault(ColorMode.GRAYSCALE),
            quality = runCatching { PrintQuality.valueOf(entity.quality) }.getOrDefault(PrintQuality.NORMAL),
            fitMode = runCatching { FitMode.valueOf(entity.fitMode) }.getOrDefault(FitMode.FIT_TO_PAGE),
            duplex = entity.duplex,
            pageRange = entity.pageRange
        ),
        totalPages = entity.totalPages,
        status = runCatching { JobStatus.valueOf(entity.status) }.getOrDefault(JobStatus.QUEUED),
        progress = entity.progress,
        errorMessage = entity.errorMessage,
        createdAt = entity.createdAt,
        startedAt = entity.startedAt,
        completedAt = entity.completedAt
    )

    private fun modelToEntity(model: PrintJob) = PrintJobEntity(
        id = model.id,
        fileName = model.fileName,
        fileUri = model.fileUri,
        fileType = model.fileType.name,
        printerVid = model.printerVid,
        printerPid = model.printerPid,
        printerName = model.printerName,
        copies = model.settings.copies,
        paperSize = model.settings.paperSize.name,
        orientation = model.settings.orientation.name,
        colorMode = model.settings.colorMode.name,
        quality = model.settings.quality.name,
        fitMode = model.settings.fitMode.name,
        duplex = model.settings.duplex,
        pageRange = model.settings.pageRange,
        totalPages = model.totalPages,
        status = model.status.name,
        progress = model.progress,
        errorMessage = model.errorMessage,
        createdAt = model.createdAt,
        startedAt = model.startedAt,
        completedAt = model.completedAt
    )
}
