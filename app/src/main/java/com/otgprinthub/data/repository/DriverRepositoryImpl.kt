package com.otgprinthub.data.repository

import android.content.Context
import com.google.gson.Gson
import com.otgprinthub.data.local.dao.DriverDao
import com.otgprinthub.data.local.entity.DriverEntity
import com.otgprinthub.data.remote.DriverDatabaseResponse
import com.otgprinthub.data.remote.DriverRepositoryApi
import com.otgprinthub.data.remote.OpenPrintingApi
import com.otgprinthub.data.remote.RemoteDriverProfile
import com.otgprinthub.domain.model.Driver
import com.otgprinthub.domain.model.DriverSource
import com.otgprinthub.domain.model.PrintProtocol
import com.otgprinthub.domain.model.PrinterCapabilities
import com.otgprinthub.domain.repository.DriverRepository
import com.otgprinthub.driver.PpdParser
import com.otgprinthub.driver.VidPidDatabase
import com.otgprinthub.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject

class DriverRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val driverDao: DriverDao,
    private val driverRepositoryApi: DriverRepositoryApi,
    private val openPrintingApi: OpenPrintingApi,
    private val ppdParser: PpdParser,
    private val gson: Gson
) : DriverRepository {

    override fun getAllDrivers(): Flow<List<Driver>> =
        driverDao.getAllDrivers().map { it.map(::entityToModel) }

    override suspend fun getDriverByVidPid(vid: String, pid: String): Driver? =
        driverDao.getDriverByVidPid(vid, pid)?.let(::entityToModel)

    override suspend fun saveDriver(driver: Driver): Long =
        driverDao.insertDriver(modelToEntity(driver))

    override suspend fun updateDriver(driver: Driver) =
        driverDao.updateDriver(modelToEntity(driver))

    override suspend fun deleteDriver(driverId: Long) =
        driverDao.deleteDriver(driverId)

    override suspend fun clearAllDrivers() =
        driverDao.deleteAllDrivers()

    override suspend fun searchDriverInGithubDb(vid: String, pid: String): Driver? {
        return try {
            val response = driverRepositoryApi.fetchDriverDatabase(Constants.GITHUB_DRIVER_DB_URL)
            if (!response.isSuccessful) return null
            val db = response.body() ?: return null
            db.drivers.firstOrNull { it.vid.equals(vid, true) && it.pid.equals(pid, true) }
                ?.let { remoteProfileToDriver(it, DriverSource.GITHUB_DB) }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun searchDriverInOpenPrinting(brand: String, model: String): Driver? {
        return try {
            val cleanModel = model.replace(" ", "_").replace("/", "-")
            val url = "${Constants.OPENPRINTING_BASE_URL}printer/$brand/$cleanModel"
            val response = openPrintingApi.getPrinterPage(url)
            if (!response.isSuccessful) return null
            val html = response.body()?.string() ?: return null
            parseOpenPrintingPage(html, brand, model)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun downloadPpd(ppdUrl: String, vid: String, pid: String): String? {
        return try {
            val response = openPrintingApi.getPrinterPage(ppdUrl)
            if (!response.isSuccessful) return null
            val bytes = response.body()?.bytes() ?: return null
            val file = File(context.filesDir, "ppd/${vid}_${pid}.ppd")
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun parsePpd(ppdPath: String): PrinterCapabilities? {
        return try {
            ppdParser.parse(File(ppdPath))
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getGenericDrivers(): List<Driver> {
        return try {
            val json = context.assets.open("generic_drivers.json").bufferedReader().readText()
            val response = gson.fromJson(json, GenericDriversFile::class.java)
            response.generic_drivers.map { g ->
                Driver(
                    vid = "0000",
                    pid = "0000",
                    brand = "Generic",
                    model = g.name,
                    protocol = protocolFromString(g.protocol),
                    color = g.color,
                    thermal = g.thermal,
                    paperWidthMm = g.paper_width_mm,
                    dotsPerLine = g.dots_per_line,
                    maxDpi = g.max_dpi,
                    paperSizes = g.paper_sizes,
                    initCommands = g.init_commands,
                    cutCommand = g.cut_command,
                    feedCommand = g.feed_command,
                    source = DriverSource.GENERIC_FALLBACK,
                    notes = g.description
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getBundledDrivers(): List<Driver> {
        return try {
            val json = context.assets.open("default_printer_profiles.json").bufferedReader().readText()
            val response = gson.fromJson(json, DriverDatabaseResponse::class.java)
            response.drivers.map { remoteProfileToDriver(it, DriverSource.BUNDLED) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseOpenPrintingPage(html: String, brand: String, model: String): Driver? {
        // Use Jsoup to extract driver info from OpenPrinting HTML page
        return try {
            val doc = org.jsoup.Jsoup.parse(html)
            val driverSection = doc.select(".driver-recommended, .recommended-driver").firstOrNull()
            val driverName = driverSection?.select("a")?.firstOrNull()?.text()
            val ppdLink = doc.select("a[href*=ppd-o-matic]").firstOrNull()?.attr("href")
            if (driverName != null) {
                Driver(
                    vid = "0000",
                    pid = "0000",
                    brand = brand,
                    model = model,
                    protocol = inferProtocolFromDriverName(driverName),
                    ppdUrl = ppdLink,
                    source = DriverSource.OPENPRINTING,
                    notes = "Driver: $driverName from OpenPrinting.org"
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun inferProtocolFromDriverName(driverName: String): PrintProtocol {
        return when {
            driverName.contains("pcl", true) && driverName.contains("6") -> PrintProtocol.PCL6
            driverName.contains("pcl", true) -> PrintProtocol.PCL5
            driverName.contains("postscript", true) || driverName.contains("ps", true) -> PrintProtocol.POSTSCRIPT
            driverName.contains("escpos", true) || driverName.contains("esc/pos", true) -> PrintProtocol.ESCPOS
            driverName.contains("escp", true) -> PrintProtocol.ESCP
            else -> PrintProtocol.RAW
        }
    }

    private fun protocolFromString(protocol: String): PrintProtocol {
        return when (protocol.lowercase()) {
            "escpos" -> PrintProtocol.ESCPOS
            "pcl5" -> PrintProtocol.PCL5
            "pcl6" -> PrintProtocol.PCL6
            "pcl3" -> PrintProtocol.PCL3
            "escp" -> PrintProtocol.ESCP
            "escp2" -> PrintProtocol.ESCP2
            "postscript" -> PrintProtocol.POSTSCRIPT
            "postscript3" -> PrintProtocol.POSTSCRIPT3
            "direct_pdf" -> PrintProtocol.DIRECT_PDF
            "raw" -> PrintProtocol.RAW
            else -> PrintProtocol.RAW
        }
    }

    private fun remoteProfileToDriver(profile: RemoteDriverProfile, source: DriverSource) = Driver(
        vid = profile.vid,
        pid = profile.pid,
        brand = profile.brand,
        model = profile.model,
        protocol = protocolFromString(profile.protocol),
        color = profile.color,
        duplex = profile.duplex,
        thermal = profile.thermal,
        dotMatrix = profile.dot_matrix,
        paperWidthMm = profile.paper_width_mm,
        dotsPerLine = profile.dots_per_line,
        maxDpi = profile.max_dpi,
        paperSizes = profile.paper_sizes,
        initCommands = profile.init_commands,
        resetCommand = profile.reset_command,
        cutCommand = profile.cut_command,
        feedCommand = profile.feed_command,
        boldOn = profile.bold_on,
        boldOff = profile.bold_off,
        alignLeft = profile.align_left,
        alignCenter = profile.align_center,
        alignRight = profile.align_right,
        supportsBarcode = profile.supports_barcode,
        supportsQr = profile.supports_qr,
        ppdUrl = profile.ppd_url,
        notes = profile.notes,
        source = source
    )

    private fun entityToModel(entity: DriverEntity) = Driver(
        id = entity.id,
        vid = entity.vid,
        pid = entity.pid,
        brand = entity.brand,
        model = entity.model,
        protocol = runCatching { PrintProtocol.valueOf(entity.protocol) }.getOrDefault(PrintProtocol.RAW),
        color = entity.color,
        duplex = entity.duplex,
        thermal = entity.thermal,
        dotMatrix = entity.dotMatrix,
        paperWidthMm = entity.paperWidthMm,
        dotsPerLine = entity.dotsPerLine,
        maxDpi = entity.maxDpi,
        paperSizes = try { gson.fromJson(entity.paperSizes, Array<String>::class.java).toList() } catch (e: Exception) { listOf("A4") },
        initCommands = entity.initCommands,
        resetCommand = entity.resetCommand,
        cutCommand = entity.cutCommand,
        feedCommand = entity.feedCommand,
        boldOn = entity.boldOn,
        boldOff = entity.boldOff,
        alignLeft = entity.alignLeft,
        alignCenter = entity.alignCenter,
        alignRight = entity.alignRight,
        supportsBarcode = entity.supportsBarcode,
        supportsQr = entity.supportsQr,
        ppdUrl = entity.ppdUrl,
        ppdLocalPath = entity.ppdLocalPath,
        notes = entity.notes,
        source = runCatching { DriverSource.valueOf(entity.source) }.getOrDefault(DriverSource.BUNDLED),
        downloadedAt = entity.downloadedAt
    )

    private fun modelToEntity(model: Driver) = DriverEntity(
        id = model.id,
        vid = model.vid,
        pid = model.pid,
        brand = model.brand,
        model = model.model,
        protocol = model.protocol.name,
        color = model.color,
        duplex = model.duplex,
        thermal = model.thermal,
        dotMatrix = model.dotMatrix,
        paperWidthMm = model.paperWidthMm,
        dotsPerLine = model.dotsPerLine,
        maxDpi = model.maxDpi,
        paperSizes = gson.toJson(model.paperSizes),
        initCommands = model.initCommands,
        resetCommand = model.resetCommand,
        cutCommand = model.cutCommand,
        feedCommand = model.feedCommand,
        boldOn = model.boldOn,
        boldOff = model.boldOff,
        alignLeft = model.alignLeft,
        alignCenter = model.alignCenter,
        alignRight = model.alignRight,
        supportsBarcode = model.supportsBarcode,
        supportsQr = model.supportsQr,
        ppdUrl = model.ppdUrl,
        ppdLocalPath = model.ppdLocalPath,
        notes = model.notes,
        source = model.source.name,
        downloadedAt = model.downloadedAt
    )

    private data class GenericDriversFile(
        val version: String = "",
        val generic_drivers: List<GenericDriverJson> = emptyList()
    )

    private data class GenericDriverJson(
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
}
