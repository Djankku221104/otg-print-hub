package com.otgprinthub.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "printers")
data class PrinterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val vid: String,
    val pid: String,
    val brandName: String = "Unknown",
    val modelName: String = "Unknown Printer",
    val usbClass: Int = 7,
    val usbSubclass: Int = 1,
    val usbProtocol: Int = 1,
    val manufacturerName: String? = null,
    val serialNumber: String? = null,
    val status: String = "DETECTED",
    val driverId: Long? = null,
    val hasDriver: Boolean = false,
    val lastConnected: Long = System.currentTimeMillis()
)
