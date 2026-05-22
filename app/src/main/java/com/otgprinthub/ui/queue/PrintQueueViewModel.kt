package com.otgprinthub.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otgprinthub.domain.usecase.ManagePrintQueueUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PrintQueueViewModel @Inject constructor(
    private val managePrintQueueUseCase: ManagePrintQueueUseCase
) : ViewModel() {

    val jobs = managePrintQueueUseCase.getQueue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun cancelJob(jobId: Long) {
        viewModelScope.launch { managePrintQueueUseCase.cancelJob(jobId) }
    }

    fun retryJob(jobId: Long) {
        viewModelScope.launch { managePrintQueueUseCase.retryJob(jobId) }
    }

    fun clearHistory() {
        viewModelScope.launch { managePrintQueueUseCase.clearHistory() }
    }
}
