package com.otgprinthub.domain.usecase

import android.content.Context
import android.net.Uri
import com.otgprinthub.domain.model.FileType
import com.otgprinthub.domain.model.JobStatus
import com.otgprinthub.domain.model.PrintJob
import com.otgprinthub.domain.model.PrintSettings
import com.otgprinthub.domain.repository.PrintJobRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class PrintDocumentUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val printJobRepository: PrintJobRepository
) {
    suspend operator fun invoke(
        fileUri: Uri,
        fileType: FileType,
        printerVid: String,
        printerPid: String,
        printerName: String,
        settings: PrintSettings = PrintSettings()
    ): PrintJob {
        val fileName = getFileName(fileUri)

        val job = PrintJob(
            fileName = fileName,
            fileUri = fileUri.toString(),
            fileType = fileType,
            printerVid = printerVid,
            printerPid = printerPid,
            printerName = printerName,
            settings = settings,
            status = JobStatus.QUEUED
        )

        val id = printJobRepository.saveJob(job)
        return job.copy(id = id)
    }

    private fun getFileName(uri: Uri): String {
        if (uri.scheme == "file") return java.io.File(uri.path!!).name
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "Unknown File"
    }

}
