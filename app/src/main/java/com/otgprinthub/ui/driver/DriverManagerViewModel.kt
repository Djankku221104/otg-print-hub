package com.otgprinthub.ui.driver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otgprinthub.domain.model.Driver
import com.otgprinthub.domain.repository.DriverRepository
import com.otgprinthub.driver.DriverManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DriverManagerViewModel @Inject constructor(
    private val driverRepository: DriverRepository,
    private val driverManager: DriverManager
) : ViewModel() {

    val drivers: StateFlow<List<Driver>> = driverRepository.getAllDrivers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteDriver(driver: Driver) {
        viewModelScope.launch { driverRepository.deleteDriver(driver.id) }
    }

    fun clearAll() {
        viewModelScope.launch { driverManager.clearAll() }
    }
}
