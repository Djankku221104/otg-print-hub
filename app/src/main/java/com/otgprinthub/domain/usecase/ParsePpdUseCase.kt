package com.otgprinthub.domain.usecase

import com.otgprinthub.domain.model.PrinterCapabilities
import com.otgprinthub.domain.repository.DriverRepository
import javax.inject.Inject

class ParsePpdUseCase @Inject constructor(
    private val driverRepository: DriverRepository
) {
    suspend operator fun invoke(ppdPath: String): PrinterCapabilities? {
        return driverRepository.parsePpd(ppdPath)
    }
}
