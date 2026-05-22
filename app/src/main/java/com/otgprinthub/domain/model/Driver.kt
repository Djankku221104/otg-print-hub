package com.otgprinthub.domain.model

data class Driver(
    val id: Long = 0,
    val vid: String,
    val pid: String,
    val brand: String,
    val model: String,
    val protocol: PrintProtocol,
    val color: Boolean = false,
    val duplex: Boolean = false,
    val thermal: Boolean = false,
    val dotMatrix: Boolean = false,
    val paperWidthMm: Float? = null,
    val dotsPerLine: Int? = null,
    val maxDpi: Int = 300,
    val paperSizes: List<String> = listOf("A4"),
    val initCommands: String? = null,
    val resetCommand: String? = null,
    val cutCommand: String? = null,
    val feedCommand: String? = null,
    val boldOn: String? = null,
    val boldOff: String? = null,
    val alignLeft: String? = null,
    val alignCenter: String? = null,
    val alignRight: String? = null,
    val supportsBarcode: Boolean = false,
    val supportsQr: Boolean = false,
    val ppdUrl: String? = null,
    val ppdLocalPath: String? = null,
    val notes: String? = null,
    val source: DriverSource = DriverSource.BUNDLED,
    val downloadedAt: Long = System.currentTimeMillis(),
    val capabilities: PrinterCapabilities? = null
)

enum class PrintProtocol(val displayName: String) {
    ESCPOS("ESC/POS (Thermal)"),
    PCL5("PCL5 (Laser)"),
    PCL6("PCL6/XL (Laser)"),
    PCL3("PCL3 (Inkjet)"),
    ESCP("ESC/P (Inkjet)"),
    ESCP2("ESC/P 2 (Inkjet)"),
    POSTSCRIPT("PostScript"),
    POSTSCRIPT3("PostScript 3"),
    DIRECT_PDF("Direct PDF"),
    RAW("Raw Text"),
    GENERIC("Generic Fallback"),
    UNKNOWN("Unknown")
}

enum class DriverSource {
    BUNDLED,
    LOCAL_CACHE,
    GITHUB_DB,
    OPENPRINTING,
    FOOMATIC,
    GENERIC_FALLBACK,
    USER_IMPORTED
}
