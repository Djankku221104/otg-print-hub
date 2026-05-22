package com.otgprinthub.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface OpenPrintingApi {
    // Generic URL fetch — used for HTML scraping fallback
    @GET
    suspend fun getPrinterPage(@Url url: String): Response<ResponseBody>

    // OpenPrinting REST API — returns JSON
    @GET("api/1.0/json/printer_list")
    suspend fun searchPrinters(
        @Query("make") make: String,
        @Query("model") model: String,
        @Query("limit") limit: Int = 5
    ): Response<ResponseBody>

    // Foomatic driver lookup by printer make+model
    @GET("api/1.0/json/driver_list")
    suspend fun getDriversForPrinter(
        @Query("make") make: String,
        @Query("model") model: String,
        @Query("limit") limit: Int = 3
    ): Response<ResponseBody>
}

data class OpenPrintingPrinterInfo(
    val brand: String,
    val model: String,
    val driverName: String,
    val driverType: String,
    val ppdUrl: String?,
    val qualityRating: Int = 0
)
