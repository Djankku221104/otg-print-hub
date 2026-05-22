package com.otgprinthub.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "print_jobs")
data class PrintJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val fileUri: String,
    val fileType: String,
    val printerVid: String,
    val printerPid: String,
    val printerName: String,
    val copies: Int = 1,
    val paperSize: String = "A4",
    val orientation: String = "PORTRAIT",
    val colorMode: String = "GRAYSCALE",
    val quality: String = "NORMAL",
    val fitMode: String = "FIT_TO_PAGE",
    val duplex: Boolean = false,
    val pageRange: String = "all",
    val totalPages: Int = 1,
    val status: String = "QUEUED",
    val progress: Int = 0,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val completedAt: Long? = null
)
