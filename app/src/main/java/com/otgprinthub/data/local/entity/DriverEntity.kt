package com.otgprinthub.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drivers")
data class DriverEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vid: String,
    val pid: String,
    val brand: String,
    val model: String,
    val protocol: String,
    val color: Boolean = false,
    val duplex: Boolean = false,
    val thermal: Boolean = false,
    val dotMatrix: Boolean = false,
    val paperWidthMm: Float? = null,
    val dotsPerLine: Int? = null,
    val maxDpi: Int = 300,
    val paperSizes: String = "A4",  // JSON array stored as string
    val initCommands: String? = null,
    val resetCommand: String? = null,
    val cutCommand: String? = null,
    val feedCommand: String? = null,
    val boldOn: String? = null,
    val boldOff: String? = null,
    val alignLeft: String? = null,
    val alignCenter: String? = null,
    val alignRight: String? = null,
    val supportsBarcode: Boolean = false,
    val supportsQr: Boolean = false,
    val ppdUrl: String? = null,
    val ppdLocalPath: String? = null,
    val notes: String? = null,
    val source: String = "BUNDLED",
    val downloadedAt: Long = System.currentTimeMillis()
)
