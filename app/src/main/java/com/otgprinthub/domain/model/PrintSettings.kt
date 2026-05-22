package com.otgprinthub.domain.model

data class PrintSettings(
    val paperSize: PaperSize = PaperSize.A4,
    val orientation: Orientation = Orientation.PORTRAIT,
    val copies: Int = 1,
    val colorMode: ColorMode = ColorMode.GRAYSCALE,
    val quality: PrintQuality = PrintQuality.NORMAL,
    val fitMode: FitMode = FitMode.FIT_TO_PAGE,
    val margins: Margins = Margins.DEFAULT,
    val duplex: Boolean = false,
    val pageRange: String = "all"
)

enum class PaperSize(val displayName: String, val widthMm: Float, val heightMm: Float) {
    A4("A4", 210f, 297f),
    A5("A5", 148f, 210f),
    A3("A3", 297f, 420f),
    LETTER("US Letter", 215.9f, 279.4f),
    LEGAL("US Legal", 215.9f, 355.6f),
    THERMAL_58MM("Thermal 58mm", 58f, Float.MAX_VALUE),
    THERMAL_80MM("Thermal 80mm", 80f, Float.MAX_VALUE),
    PHOTO_4X6("Photo 4x6", 101.6f, 152.4f),
    PHOTO_5X7("Photo 5x7", 127f, 177.8f),
    CUSTOM("Custom", 0f, 0f);

    val widthPx: Int get() = mmToPx(widthMm)
    val heightPx: Int get() = if (heightMm == Float.MAX_VALUE) 0 else mmToPx(heightMm)

    companion object {
        fun fromMm(widthMm: Float, heightMm: Float): PaperSize {
            return entries.firstOrNull {
                it != CUSTOM && it != THERMAL_58MM && it != THERMAL_80MM &&
                        kotlin.math.abs(it.widthMm - widthMm) < 2f &&
                        kotlin.math.abs(it.heightMm - heightMm) < 2f
            } ?: CUSTOM
        }

        private fun mmToPx(mm: Float, dpi: Int = 300): Int = (mm / 25.4f * dpi).toInt()
    }
}

enum class Orientation(val displayName: String) {
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

enum class ColorMode(val displayName: String) {
    COLOR("Color"),
    GRAYSCALE("Grayscale"),
    BLACK_AND_WHITE("Black & White")
}

enum class PrintQuality(val displayName: String, val dpi: Int) {
    DRAFT("Draft", 180),
    NORMAL("Normal", 360),
    HIGH("High", 720),
    BEST("Best", 720)
}

enum class FitMode(val displayName: String) {
    FIT_TO_PAGE("Fit to Page"),
    FILL_PAGE("Fill Page"),
    ACTUAL_SIZE("Actual Size"),
    CENTER("Center")
}

data class Margins(
    val topMm: Float = 10f,
    val bottomMm: Float = 10f,
    val leftMm: Float = 10f,
    val rightMm: Float = 10f
) {
    companion object {
        val DEFAULT = Margins(10f, 10f, 10f, 10f)
        val NONE = Margins(0f, 0f, 0f, 0f)
        val NARROW = Margins(5f, 5f, 5f, 5f)
    }
}
