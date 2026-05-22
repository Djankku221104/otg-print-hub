package com.otgprinthub.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.otgprinthub.domain.model.JobStatus
import com.otgprinthub.domain.model.PrintJob
import com.otgprinthub.ui.theme.ErrorRed
import com.otgprinthub.ui.theme.SuccessGreen
import com.otgprinthub.ui.theme.WarningOrange
import com.otgprinthub.util.toFormattedDate

@Composable
fun PrintJobItem(
    job: PrintJob,
    onRetry: ((PrintJob) -> Unit)?,
    onCancel: ((PrintJob) -> Unit)?,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = when (job.status) {
                        JobStatus.COMPLETED -> Icons.Default.CheckCircle
                        JobStatus.FAILED -> Icons.Default.Error
                        JobStatus.PRINTING -> Icons.Default.Print
                        JobStatus.QUEUED -> Icons.Default.Schedule
                        JobStatus.CANCELLED -> Icons.Default.Cancel
                        else -> Icons.Default.HourglassEmpty
                    },
                    contentDescription = null,
                    tint = when (job.status) {
                        JobStatus.COMPLETED -> SuccessGreen
                        JobStatus.FAILED -> ErrorRed
                        JobStatus.PRINTING -> MaterialTheme.colorScheme.primary
                        else -> WarningOrange
                    }
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = job.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${job.status.displayName} • ${job.createdAt.toFormattedDate()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (job.status == JobStatus.FAILED && job.errorMessage != null) {
                        Text(
                            text = job.errorMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = ErrorRed,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row {
                    if (job.status == JobStatus.FAILED && onRetry != null) {
                        IconButton(onClick = { onRetry(job) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry")
                        }
                    }
                    if ((job.status == JobStatus.QUEUED || job.status == JobStatus.PRINTING) && onCancel != null) {
                        IconButton(onClick = { onCancel(job) }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    }
                }
            }

            if (job.status == JobStatus.PRINTING && job.progress > 0) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { job.progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
