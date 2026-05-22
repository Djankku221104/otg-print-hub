package com.otgprinthub.driver

import com.otgprinthub.domain.model.PageSizeInfo
import com.otgprinthub.domain.model.PrinterCapabilities
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PpdParser @Inject constructor() {

    fun parse(ppdFile: File): PrinterCapabilities? {
        if (!ppdFile.exists()) return null
        return try {
            val lines = ppdFile.readLines()
            parsePpdLines(lines)
        } catch (e: Exception) {
            null
        }
    }

    fun parseFromString(ppdContent: String): PrinterCapabilities? {
        return try {
            parsePpdLines(ppdContent.lines())
        } catch (e: Exception) {
            null
        }
    }

    private fun parsePpdLines(lines: List<String>): PrinterCapabilities {
        val keyValues = mutableMapOf<String, String>()
        val pageSizes = mutableListOf<PageSizeInfo>()
        val resolutions = mutableSetOf<Int>()

        for (line in lines) {
            if (line.startsWith("*%") || line.isBlank()) continue

            // Parse *Key Value: "translation" format
            val mainKeyMatch = Regex("""^\*(\w+)\s+([^/:"]+)(?:/[^:"]+)?:\s*"?([^"]*)"?\s*$""").find(line)
            val simpleKeyMatch = Regex("""^\*(\w+):\s*(.+)$""").find(line)

            when {
                mainKeyMatch != null -> {
                    val key = mainKeyMatch.groupValues[1]
                    val option = mainKeyMatch.groupValues[2].trim()
                    val value = mainKeyMatch.groupValues[3].trim()

                    when (key) {
                        "PageSize" -> {
                            val dims = parsePsPageSize(value)
                            if (dims != null) {
                                pageSizes.add(PageSizeInfo(
                                    name = option,
                                    displayName = option.replace("_", " "),
                                    widthPoints = dims.first,
                                    heightPoints = dims.second
                                ))
                            }
                        }
                        "Resolution" -> {
                            val dpi = option.replace("dpi", "").trim().toIntOrNull()
                            if (dpi != null) resolutions.add(dpi)
                        }
                    }
                }
                simpleKeyMatch != null -> {
                    val key = simpleKeyMatch.groupValues[1]
                    val value = simpleKeyMatch.groupValues[2].trim().trim('"')
                    keyValues[key] = value
                }
            }
        }

        val defaultResolution = keyValues["DefaultResolution"]
            ?.replace("dpi", "")?.trim()?.toIntOrNull() ?: 300
        if (defaultResolution > 0) resolutions.add(defaultResolution)

        return PrinterCapabilities(
            printerName = keyValues["PCFileName"]?.removeSuffix(".PPD") ?: "",
            manufacturer = keyValues["Manufacturer"] ?: "",
            modelName = keyValues["ModelName"] ?: keyValues["Product"]?.trim("()") ?: "",
            colorDevice = keyValues["ColorDevice"]?.equals("True", ignoreCase = true) ?: false,
            defaultResolution = defaultResolution,
            availableResolutions = resolutions.sorted(),
            duplexSupported = keyValues["Duplex"] != null || keyValues["DefaultDuplex"] != null,
            supportedPageSizes = pageSizes,
            defaultPageSize = keyValues["DefaultPageSize"] ?: "A4",
            postscriptLevel = keyValues["LanguageLevel"]?.toIntOrNull()
        )
    }

    private fun parsePsPageSize(psCommand: String): Pair<Float, Float>? {
        // Extract [width height] from PostScript setpagedevice command
        val match = Regex("""\[\s*(\d+\.?\d*)\s+(\d+\.?\d*)\s*\]""").find(psCommand)
        return match?.let {
            Pair(it.groupValues[1].toFloat(), it.groupValues[2].toFloat())
        }
    }
}
