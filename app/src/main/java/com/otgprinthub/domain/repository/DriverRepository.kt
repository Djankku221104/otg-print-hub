package com.otgprinthub.domain.repository

import com.otgprinthub.domain.model.Driver
import com.otgprinthub.domain.model.PrinterCapabilities
import kotlinx.coroutines.flow.Flow

interface DriverRepository {
    fun getAllDrivers(): Flow<List<Driver>>
    suspend fun getDriverByVidPid(vid: String, pid: String): Driver?
    suspend fun saveDriver(driver: Driver): Long
    suspend fun updateDriver(driver: Driver)
    suspend fun deleteDriver(driverId: Long)
    suspend fun clearAllDrivers()

    // Remote search
    suspend fun searchDriverInGithubDb(vid: String, pid: String): Driver?
    suspend fun searchDriverInOpenPrinting(brand: String, model: String): Driver?
    suspend fun downloadPpd(ppdUrl: String, vid: String, pid: String): String?
    suspend fun parsePpd(ppdPath: String): PrinterCapabilities?

    // Generic drivers
    suspend fun getGenericDrivers(): List<Driver>
    suspend fun getBundledDrivers(): List<Driver>
}
