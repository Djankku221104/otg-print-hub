package com.otgprinthub.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.otgprinthub.data.remote.DriverRepositoryApi
import com.otgprinthub.data.remote.OpenPrintingApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder()
        .setLenient()
        .create()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    @Provides
    @Singleton
    @Named("driver_retrofit")
    fun provideDriverRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://raw.githubusercontent.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    @Named("openprinting_retrofit")
    fun provideOpenPrintingRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://www.openprinting.org/")
            .client(okHttpClient)
            .build()
    }

    @Provides
    @Singleton
    fun provideDriverRepositoryApi(@Named("driver_retrofit") retrofit: Retrofit): DriverRepositoryApi {
        return retrofit.create(DriverRepositoryApi::class.java)
    }

    @Provides
    @Singleton
    fun provideOpenPrintingApi(@Named("openprinting_retrofit") retrofit: Retrofit): OpenPrintingApi {
        return retrofit.create(OpenPrintingApi::class.java)
    }
}
