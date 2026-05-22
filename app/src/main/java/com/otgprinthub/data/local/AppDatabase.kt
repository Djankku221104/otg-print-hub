package com.otgprinthub.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.otgprinthub.data.local.dao.DriverDao
import com.otgprinthub.data.local.dao.PrintJobDao
import com.otgprinthub.data.local.dao.PrinterDao
import com.otgprinthub.data.local.entity.DriverEntity
import com.otgprinthub.data.local.entity.PrintJobEntity
import com.otgprinthub.data.local.entity.PrinterEntity

@Database(
    entities = [
        PrinterEntity::class,
        DriverEntity::class,
        PrintJobEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun printerDao(): PrinterDao
    abstract fun driverDao(): DriverDao
    abstract fun printJobDao(): PrintJobDao

    companion object {
        const val DATABASE_NAME = "otg_print_hub.db"
    }
}
