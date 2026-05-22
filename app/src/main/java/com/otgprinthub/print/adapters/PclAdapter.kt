package com.otgprinthub.print.adapters

import android.graphics.Bitmap
import android.graphics.Color
import com.otgprinthub.domain.model.Driver
import com.otgprinthub.domain.model.Orientation
import com.otgprinthub.domain.model.PaperSize
import com.otgprinthub.domain.model.PrintSettings
import com.otgprinthub.util.HexUtils

class PclAdapter(override val driver: Driver) : PrintAdapter {

    private val ESC = ""
    private val FF = ""

    override fun buildInitSequence(): ByteArray {
        // PCL reset + start job
        return "$ESC E".toByteArray(Charsets.US_ASCII)
    }

    override fun buildResetSequence(): ByteArray = "$ESC E".toByteArray(Charsets.US_ASCII)

    override fun buildTextData(text: String, settings: PrintSettings): ByteArray {
        val sb = StringBuilder()
        sb.append(buildPclHeader(settings))
        // Set font to Courier 12pt
        sb.append("${ESC}(s0p10h12v0s0b3T")
        // Text content
        sb.append(text)
        sb.append(FF)  // Form feed to eject page
        return sb.toString().toByteArray(Charsets.US_ASCII)
    }

    override fun buildImageData(bitmap: Bitmap, settings: PrintSettings): ByteArray {
        val result = mutableListOf<Byte>()
        result.addAll(buildPclHeader(settings).toByteArray(Charsets.US_ASCII).toList())

        val dpi = settings.quality.dpi
        result.addAll(buildRasterGraphics(bitmap, dpi).toList())

        result.addAll(FF.toByteArray(Charsets.US_ASCII).toList())
        return result.toByteArray()
    }

    override fun buildPageBreak(): ByteArray = FF.toByteArray(Charsets.US_ASCII)

    override fun buildEndSequence(): ByteArray = "$ESC E".toByteArray(Charsets.US_ASCII)

    private fun buildPclHeader(settings: PrintSettings): String {
        val sb = StringBuilder()
        // Paper size
        val pclPaperSize = getPclPaperSize(settings.paperSize)
        sb.append("${ESC}&l${pclPaperSize}A")
        // Orientation
        val orientation = if (settings.orientation == Orientation.LANDSCAPE) "1" else "0"
        sb.append("${ESC}&l${orientation}O")
        // Copies
        sb.append("${ESC}&l${settings.copies}X")
        // Resolution
        sb.append("${ESC}*t${settings.quality.dpi}R")
        return sb.toString()
    }

    private fun getPclPaperSize(paperSize: PaperSize): String {
        return when (paperSize) {
            PaperSize.A4 -> "26"
            PaperSize.LETTER -> "2"
            PaperSize.LEGAL -> "3"
            PaperSize.A5 -> "25"
            PaperSize.A3 -> "27"
            else -> "26"
        }
    }

    private fun buildRasterGraphics(bitmap: Bitmap, dpi: Int): ByteArray {
        val result = mutableListOf<Byte>()

        // Set raster graphics resolution
        result.addAll("${ESC}*t${dpi}R".toByteArray(Charsets.US_ASCII).toList())
        // Set raster graphics presentation mode
        result.addAll("${ESC}*r0F".toByteArray(Charsets.US_ASCII).toList())
        // Start raster graphics
        result.addAll("${ESC}*r1A".toByteArray(Charsets.US_ASCII).toList())

        val width = bitmap.width
        val height = bitmap.height
        val bytesPerRow = (width + 7) / 8

        for (y in 0 until height) {
            val rowData = ByteArray(bytesPerRow)
            for (byteIdx in 0 until bytesPerRow) {
                var byte = 0
                for (bit in 0 until 8) {
                    val x = byteIdx * 8 + bit
                    if (x < width) {
                        val pixel = bitmap.getPixel(x, y)
                        val luminance = (0.299 * Color.red(pixel) +
                                0.587 * Color.green(pixel) +
                                0.114 * Color.blue(pixel)).toInt()
                        if (luminance < 128) {
                            byte = byte or (0x80 shr bit)
                        }
                    }
                }
                rowData[byteIdx] = byte.toByte()
            }

            // PCL raster data transfer: ESC*b[byteCount]W[data]
            val rowCmd = "${ESC}*b${bytesPerRow}W"
            result.addAll(rowCmd.toByteArray(Charsets.US_ASCII).toList())
            result.addAll(rowData.toList())
        }

        // End raster graphics
        result.addAll("${ESC}*rB".toByteArray(Charsets.US_ASCII).toList())

        return result.toByteArray()
    }
}
