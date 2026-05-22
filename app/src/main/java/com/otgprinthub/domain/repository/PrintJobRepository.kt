package com.otgprinthub.domain.repository

import com.otgprinthub.domain.model.JobStatus
import com.otgprinthub.domain.model.PrintJob
import kotlinx.coroutines.flow.Flow

interface PrintJobRepository {
    fun getAllJobs(): Flow<List<PrintJob>>
    fun getActiveJobs(): Flow<List<PrintJob>>
    fun getJobsByStatus(status: JobStatus): Flow<List<PrintJob>>
    suspend fun getJobById(id: Long): PrintJob?
    suspend fun saveJob(job: PrintJob): Long
    suspend fun updateJob(job: PrintJob)
    suspend fun updateJobStatus(id: Long, status: JobStatus, progress: Int = 0, error: String? = null)
    suspend fun deleteJob(id: Long)
    suspend fun clearCompletedJobs()
    suspend fun clearAllJobs()
}
