package com.otgprinthub.data.local.dao

import androidx.room.*
import com.otgprinthub.data.local.entity.PrinterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PrinterDao {
    @Query("SELECT * FROM printers ORDER BY lastConnected DESC")
    fun getAllPrinters(): Flow<List<PrinterEntity>>

    @Query("SELECT * FROM printers WHERE status != 'DISCONNECTED' ORDER BY lastConnected DESC")
    fun getConnectedPrinters(): Flow<List<PrinterEntity>>

    @Query("SELECT * FROM printers WHERE vid = :vid AND pid = :pid LIMIT 1")
    suspend fun getPrinterByVidPid(vid: String, pid: String): PrinterEntity?

    @Query("SELECT * FROM printers ORDER BY lastConnected DESC LIMIT 1")
    suspend fun getLastConnectedPrinter(): PrinterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrinter(printer: PrinterEntity): Long

    @Update
    suspend fun updatePrinter(printer: PrinterEntity)

    @Query("DELETE FROM printers WHERE id = :id")
    suspend fun deletePrinter(id: Long)

    @Query("SELECT * FROM printers WHERE id = :id")
    suspend fun getPrinterById(id: Long): PrinterEntity?
}
