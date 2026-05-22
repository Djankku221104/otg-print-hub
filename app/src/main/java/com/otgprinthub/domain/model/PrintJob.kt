package com.otgprinthub.domain.model

data class PrintJob(
    val id: Long = 0,
    val fileName: String,
    val fileUri: String,
    val fileType: FileType,
    val printerVid: String,
    val printerPid: String,
    val printerName: String,
    val settings: PrintSettings = PrintSettings(),
    val totalPages: Int = 1,
    val status: JobStatus = JobStatus.QUEUED,
    val progress: Int = 0,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val completedAt: Long? = null
)

enum class JobStatus(val displayName: String) {
    QUEUED("Queued"),
    PREPARING("Preparing"),
    PRINTING("Printing"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    CANCELLED("Cancelled"),
    PAUSED("Paused")
}

enum class FileType(val displayName: String, val mimeTypes: List<String>) {
    PDF("PDF Document", listOf("application/pdf")),
    IMAGE("Image", listOf("image/jpeg", "image/png", "image/webp", "image/bmp", "image/gif")),
    TEXT("Text File", listOf("text/plain", "text/html")),
    UNKNOWN("Unknown", emptyList());

    companion object {
        fun fromMimeType(mimeType: String): FileType {
            return entries.firstOrNull { ft -> ft.mimeTypes.any { mimeType.startsWith(it) } }
                ?: UNKNOWN
        }

        fun fromExtension(extension: String): FileType {
            return when (extension.lowercase()) {
                "pdf" -> PDF
                "jpg", "jpeg", "png", "webp", "bmp", "gif", "tiff", "tif" -> IMAGE
                "txt", "text", "log", "csv", "html", "htm" -> TEXT
                else -> UNKNOWN
            }
        }
    }
}
