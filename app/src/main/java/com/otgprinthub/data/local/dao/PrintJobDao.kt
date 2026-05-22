package com.otgprinthub.data.local.dao

import androidx.room.*
import com.otgprinthub.data.local.entity.PrintJobEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PrintJobDao {
    @Query("SELECT * FROM print_jobs ORDER BY createdAt DESC")
    fun getAllJobs(): Flow<List<PrintJobEntity>>

    @Query("SELECT * FROM print_jobs WHERE status IN ('QUEUED', 'PREPARING', 'PRINTING') ORDER BY createdAt ASC")
    fun getActiveJobs(): Flow<List<PrintJobEntity>>

    @Query("SELECT * FROM print_jobs WHERE status = :status ORDER BY createdAt DESC")
    fun getJobsByStatus(status: String): Flow<List<PrintJobEntity>>

    @Query("SELECT * FROM print_jobs WHERE id = :id")
    suspend fun getJobById(id: Long): PrintJobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: PrintJobEntity): Long

    @Update
    suspend fun updateJob(job: PrintJobEntity)

    @Query("UPDATE print_jobs SET status = :status, progress = :progress, errorMessage = :error WHERE id = :id")
    suspend fun updateJobStatus(id: Long, status: String, progress: Int = 0, error: String? = null)

    @Query("UPDATE print_jobs SET startedAt = :startedAt WHERE id = :id")
    suspend fun updateStartTime(id: Long, startedAt: Long)

    @Query("UPDATE print_jobs SET completedAt = :completedAt WHERE id = :id")
    suspend fun updateCompletedTime(id: Long, completedAt: Long)

    @Query("DELETE FROM print_jobs WHERE id = :id")
    suspend fun deleteJob(id: Long)

    @Query("DELETE FROM print_jobs WHERE status IN ('COMPLETED', 'CANCELLED', 'FAILED')")
    suspend fun clearCompletedJobs()

    @Query("DELETE FROM print_jobs")
    suspend fun clearAllJobs()
}
