package com.otgprinthub.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

interface DriverRepositoryApi {
    @GET
    suspend fun fetchDriverDatabase(@Url url: String): Response<DriverDatabaseResponse>
}

data class DriverDatabaseResponse(
    val version: String = "",
    val last_updated: String = "",
    val drivers: List<RemoteDriverProfile> = emptyList(),
    val generic_drivers: List<RemoteGenericDriver> = emptyList()
)

data class RemoteDriverProfile(
    val vid: String = "",
    val pid: String = "",
    val brand: String = "",
    val model: String = "",
    val protocol: String = "raw",
    val color: Boolean = false,
    val duplex: Boolean = false,
    val thermal: Boolean = false,
    val dot_matrix: Boolean = false,
    val paper_width_mm: Float? = null,
    val dots_per_line: Int? = null,
    val max_dpi: Int = 300,
    val paper_sizes: List<String> = listOf("A4"),
    val init_commands: String? = null,
    val reset_command: String? = null,
    val cut_command: String? = null,
    val feed_command: String? = null,
    val bold_on: String? = null,
    val bold_off: String? = null,
    val align_left: String? = null,
    val align_center: String? = null,
    val align_right: String? = null,
    val supports_barcode: Boolean = false,
    val supports_qr: Boolean = false,
    val ppd_url: String? = null,
    val notes: String? = null
)

data class RemoteGenericDriver(
    val id: String = "",
    val protocol: String = "raw",
    val name: String = "",
    val description: String = "",
    val paper_width_mm: Float? = null,
    val dots_per_line: Int? = null,
    val max_dpi: Int = 300,
    val paper_sizes: List<String> = listOf("A4"),
    val init_commands: String? = null,
    val cut_command: String? = null,
    val feed_command: String? = null,
    val color: Boolean = false,
    val thermal: Boolean = false
)
