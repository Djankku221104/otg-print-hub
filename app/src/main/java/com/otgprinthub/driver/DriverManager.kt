package com.otgprinthub.driver

import com.otgprinthub.domain.model.Driver
import com.otgprinthub.domain.model.Printer
import com.otgprinthub.domain.model.PrinterStatus
import com.otgprinthub.domain.repository.DriverRepository
import com.otgprinthub.domain.repository.PrinterRepository
import com.otgprinthub.domain.usecase.FindDriverUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriverManager @Inject constructor(
    private val findDriverUseCase: FindDriverUseCase,
    private val driverRepository: DriverRepository,
    private val printerRepository: PrinterRepository
) {
    fun findAndCacheDriver(printer: Printer): Flow<FindDriverUseCase.DriverSearchState> {
        return findDriverUseCase(printer).onEach { state ->
            when (state) {
                is FindDriverUseCase.DriverSearchState.DriverFound -> {
                    printerRepository.updatePrinter(
                        printer.copy(
                            hasDriver = true,
                            driverId = state.driver.id,
                            status = PrinterStatus.DRIVER_FOUND
                        )
                    )
                    // Optionally download PPD if URL provided
                    if (state.driver.ppdUrl != null && state.driver.ppdLocalPath == null) {
                        downloadPpd(state.driver)
                    }
                }
                is FindDriverUseCase.DriverSearchState.DriverNotFound -> {
                    printerRepository.updatePrinter(
                        printer.copy(status = PrinterStatus.DRIVER_NOT_FOUND)
                    )
                }
                else -> {}
            }
        }
    }

    suspend fun getDriverForPrinter(printer: Printer): Driver? {
        return driverRepository.getDriverByVidPid(printer.vid, printer.pid)
    }

    suspend fun getAllDownloadedDrivers(): List<Driver> {
        var result = emptyList<Driver>()
        driverRepository.getAllDrivers().collect { result = it }
        return result
    }

    suspend fun deleteDriver(driverId: Long) {
        driverRepository.deleteDriver(driverId)
    }

    suspend fun clearAll() {
        driverRepository.clearAllDrivers()
    }

    private suspend fun downloadPpd(driver: Driver) {
        val ppdUrl = driver.ppdUrl ?: return
        val localPath = driverRepository.downloadPpd(ppdUrl, driver.vid, driver.pid) ?: return
        val capabilities = driverRepository.parsePpd(localPath)
        driverRepository.updateDriver(
            driver.copy(ppdLocalPath = localPath, capabilities = capabilities)
        )
    }
}
