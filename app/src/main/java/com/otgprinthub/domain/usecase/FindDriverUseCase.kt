package com.otgprinthub.domain.usecase

import com.otgprinthub.domain.model.Driver
import com.otgprinthub.domain.model.DriverSource
import com.otgprinthub.domain.model.Printer
import com.otgprinthub.domain.model.PrintProtocol
import com.otgprinthub.domain.repository.DriverRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class FindDriverUseCase @Inject constructor(
    private val driverRepository: DriverRepository
) {
    sealed class DriverSearchState {
        data object CheckingLocalCache : DriverSearchState()
        data object SearchingGithubDb : DriverSearchState()
        data object CheckingOpenPrinting : DriverSearchState()
        data object TryingGenericDrivers : DriverSearchState()
        data class DriverFound(val driver: Driver) : DriverSearchState()
        data class DriverNotFound(val vid: String, val pid: String) : DriverSearchState()
        data class Error(val message: String) : DriverSearchState()
    }

    operator fun invoke(printer: Printer): Flow<DriverSearchState> = flow {
        val vid = printer.vid
        val pid = printer.pid

        // Layer 1: Local cache
        emit(DriverSearchState.CheckingLocalCache)
        driverRepository.getDriverByVidPid(vid, pid)?.let {
            emit(DriverSearchState.DriverFound(it))
            return@flow
        }

        // Check bundled drivers
        driverRepository.getBundledDrivers()
            .firstOrNull { it.vid == vid && it.pid == pid }
            ?.let {
                val saved = driverRepository.saveDriver(it.copy(source = DriverSource.BUNDLED))
                emit(DriverSearchState.DriverFound(it.copy(id = saved)))
                return@flow
            }

        // Layer 2: GitHub DB
        emit(DriverSearchState.SearchingGithubDb)
        runCatching {
            driverRepository.searchDriverInGithubDb(vid, pid)
        }.getOrNull()?.let { driver ->
            val id = driverRepository.saveDriver(driver)
            emit(DriverSearchState.DriverFound(driver.copy(id = id)))
            return@flow
        }

        // Layer 3: OpenPrinting.org
        emit(DriverSearchState.CheckingOpenPrinting)
        if (printer.brandName != "Unknown") {
            runCatching {
                driverRepository.searchDriverInOpenPrinting(printer.brandName, printer.modelName)
            }.getOrNull()?.let { driver ->
                val id = driverRepository.saveDriver(driver)
                emit(DriverSearchState.DriverFound(driver.copy(id = id)))
                return@flow
            }
        }

        // Layer 4: Generic fallback
        emit(DriverSearchState.TryingGenericDrivers)
        val genericDriver = driverRepository.getGenericDrivers().firstOrNull()
        if (genericDriver != null) {
            val fallback = genericDriver.copy(
                vid = vid,
                pid = pid,
                brand = printer.brandName,
                model = printer.modelName,
                source = DriverSource.GENERIC_FALLBACK
            )
            val id = driverRepository.saveDriver(fallback)
            emit(DriverSearchState.DriverFound(fallback.copy(id = id)))
        } else {
            emit(DriverSearchState.DriverNotFound(vid, pid))
        }
    }
}
