package com.otgprinthub.di

import android.content.Context
import androidx.room.Room
import com.otgprinthub.data.local.AppDatabase
import com.otgprinthub.data.local.dao.DriverDao
import com.otgprinthub.data.local.dao.PrintJobDao
import com.otgprinthub.data.local.dao.PrinterDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun providePrinterDao(db: AppDatabase): PrinterDao = db.printerDao()

    @Provides
    fun provideDriverDao(db: AppDatabase): DriverDao = db.driverDao()

    @Provides
    fun providePrintJobDao(db: AppDatabase): PrintJobDao = db.printJobDao()
}
