package com.otgprinthub.di

import com.otgprinthub.data.repository.DriverRepositoryImpl
import com.otgprinthub.data.repository.PrintJobRepositoryImpl
import com.otgprinthub.data.repository.PrinterRepositoryImpl
import com.otgprinthub.domain.repository.DriverRepository
import com.otgprinthub.domain.repository.PrintJobRepository
import com.otgprinthub.domain.repository.PrinterRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindPrinterRepository(impl: PrinterRepositoryImpl): PrinterRepository

    @Binds
    @Singleton
    abstract fun bindDriverRepository(impl: DriverRepositoryImpl): DriverRepository

    @Binds
    @Singleton
    abstract fun bindPrintJobRepository(impl: PrintJobRepositoryImpl): PrintJobRepository
}
