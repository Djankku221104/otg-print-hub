package com.otgprinthub.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface OpenPrintingApi {
    @GET
    suspend fun getPrinterPage(@Url url: String): Response<ResponseBody>

    @GET("ppd-o-matic.php")
    suspend fun downloadPpd(
        @Query("driver") driver: String,
        @Query("printer") printer: String,
        @Query("show") show: String = "0"
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
