package com.otgprinthub.domain.model

data class PrinterCapabilities(
    val printerName: String = "",
    val manufacturer: String = "",
    val modelName: String = "",
    val colorDevice: Boolean = false,
    val defaultResolution: Int = 300,
    val availableResolutions: List<Int> = listOf(300),
    val duplexSupported: Boolean = false,
    val supportedPageSizes: List<PageSizeInfo> = emptyList(),
    val defaultPageSize: String = "A4",
    val inputTrays: List<String> = emptyList(),
    val mediaTypes: List<String> = emptyList(),
    val postscriptLevel: Int? = null,
    val customMinWidth: Float? = null,
    val customMaxWidth: Float? = null,
    val customMinHeight: Float? = null,
    val customMaxHeight: Float? = null
)

data class PageSizeInfo(
    val name: String,
    val displayName: String,
    val widthPoints: Float,
    val heightPoints: Float
) {
    val widthMm: Float get() = widthPoints * 25.4f / 72f
    val heightMm: Float get() = heightPoints * 25.4f / 72f
}
