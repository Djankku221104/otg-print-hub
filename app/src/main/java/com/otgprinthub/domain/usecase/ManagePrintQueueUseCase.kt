package com.otgprinthub.domain.usecase

import com.otgprinthub.domain.model.JobStatus
import com.otgprinthub.domain.model.PrintJob
import com.otgprinthub.domain.repository.PrintJobRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ManagePrintQueueUseCase @Inject constructor(
    private val printJobRepository: PrintJobRepository
) {
    fun getQueue(): Flow<List<PrintJob>> = printJobRepository.getAllJobs()
    fun getActiveJobs(): Flow<List<PrintJob>> = printJobRepository.getActiveJobs()

    suspend fun cancelJob(jobId: Long) {
        printJobRepository.updateJobStatus(jobId, JobStatus.CANCELLED)
    }

    suspend fun retryJob(jobId: Long) {
        val job = printJobRepository.getJobById(jobId) ?: return
        val newJob = job.copy(
            id = 0,
            status = JobStatus.QUEUED,
            progress = 0,
            errorMessage = null,
            createdAt = System.currentTimeMillis(),
            startedAt = null,
            completedAt = null
        )
        printJobRepository.saveJob(newJob)
    }

    suspend fun clearHistory() = printJobRepository.clearCompletedJobs()
    suspend fun clearAll() = printJobRepository.clearAllJobs()
}
