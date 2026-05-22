package com.otgprinthub.ui.queue

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.otgprinthub.domain.model.JobStatus
import com.otgprinthub.domain.model.PrintJob
import com.otgprinthub.ui.components.PrintJobItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintQueueScreen(
    navController: NavController,
    viewModel: PrintQueueViewModel = hiltViewModel()
) {
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()

    val activeJobs = jobs.filter { it.status in listOf(JobStatus.QUEUED, JobStatus.PREPARING, JobStatus.PRINTING) }
    val completedJobs = jobs.filter { it.status == JobStatus.COMPLETED }
    val failedJobs = jobs.filter { it.status == JobStatus.FAILED || it.status == JobStatus.CANCELLED }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Print Queue") },
                actions = {
                    if (completedJobs.isNotEmpty() || failedJobs.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearHistory() }) {
                            Text("Clear History")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (jobs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text("No Print Jobs", style = MaterialTheme.typography.titleMedium)
                    Text("Print jobs will appear here", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (activeJobs.isNotEmpty()) {
                    item {
                        SectionHeader("Active (${activeJobs.size})")
                    }
                    items(activeJobs) { job ->
                        PrintJobItem(
                            job = job,
                            onRetry = null,
                            onCancel = { viewModel.cancelJob(it.id) }
                        )
                    }
                }

                if (failedJobs.isNotEmpty()) {
                    item { SectionHeader("Failed / Cancelled (${failedJobs.size})") }
                    items(failedJobs) { job ->
                        PrintJobItem(
                            job = job,
                            onRetry = { viewModel.retryJob(it.id) },
                            onCancel = null
                        )
                    }
                }

                if (completedJobs.isNotEmpty()) {
                    item { SectionHeader("Completed (${completedJobs.size})") }
                    items(completedJobs) { job ->
                        PrintJobItem(job = job, onRetry = null, onCancel = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
