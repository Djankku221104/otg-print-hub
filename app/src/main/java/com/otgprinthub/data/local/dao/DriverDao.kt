package com.otgprinthub.data.local.dao

import androidx.room.*
import com.otgprinthub.data.local.entity.DriverEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DriverDao {
    @Query("SELECT * FROM drivers ORDER BY downloadedAt DESC")
    fun getAllDrivers(): Flow<List<DriverEntity>>

    @Query("SELECT * FROM drivers WHERE vid = :vid AND pid = :pid LIMIT 1")
    suspend fun getDriverByVidPid(vid: String, pid: String): DriverEntity?

    @Query("SELECT * FROM drivers WHERE id = :id")
    suspend fun getDriverById(id: Long): DriverEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDriver(driver: DriverEntity): Long

    @Update
    suspend fun updateDriver(driver: DriverEntity)

    @Query("DELETE FROM drivers WHERE id = :id")
    suspend fun deleteDriver(id: Long)

    @Query("DELETE FROM drivers")
    suspend fun deleteAllDrivers()

    @Query("SELECT COUNT(*) FROM drivers")
    suspend fun getDriverCount(): Int
}
