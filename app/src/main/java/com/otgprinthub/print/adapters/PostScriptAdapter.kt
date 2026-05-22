package com.otgprinthub.print.adapters

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.otgprinthub.domain.model.Driver
import com.otgprinthub.domain.model.PaperSize
import com.otgprinthub.domain.model.PrintSettings
import java.io.ByteArrayOutputStream

class PostScriptAdapter(override val driver: Driver) : PrintAdapter {

    override fun buildInitSequence(): ByteArray = byteArrayOf()

    override fun buildResetSequence(): ByteArray = byteArrayOf()

    override fun buildTextData(text: String, settings: PrintSettings): ByteArray {
        val (pageWidth, pageHeight) = getPageDimensions(settings.paperSize)
        val sb = StringBuilder()
        sb.appendLine("%!PS-Adobe-3.0")
        sb.appendLine("%%BoundingBox: 0 0 $pageWidth $pageHeight")
        sb.appendLine("%%Pages: 1")
        sb.appendLine("%%EndComments")
        sb.appendLine("%%Page: 1 1")
        sb.appendLine("/Courier findfont 12 scalefont setfont")

        var yPos = pageHeight - 50  // Start near top
        text.lines().forEach { line ->
            val escaped = line.replace("(", "\\(").replace(")", "\\)")
            sb.appendLine("72 $yPos moveto ($escaped) show")
            yPos -= 14  // Line height
        }

        sb.appendLine("showpage")
        sb.appendLine("%%EOF")
        return sb.toString().toByteArray(Charsets.US_ASCII)
    }

    override fun buildImageData(bitmap: Bitmap, settings: PrintSettings): ByteArray {
        val (pageWidth, pageHeight) = getPageDimensions(settings.paperSize)
        val bitmapWidth = bitmap.width
        val bitmapHeight = bitmap.height

        val sb = StringBuilder()
        sb.appendLine("%!PS-Adobe-3.0")
        sb.appendLine("%%BoundingBox: 0 0 $pageWidth $pageHeight")
        sb.appendLine("%%Pages: 1")
        sb.appendLine("%%EndComments")
        sb.appendLine("%%Page: 1 1")

        // Scale image to fit page with margins
        val margin = 36  // 0.5 inch
        val availWidth = pageWidth - 2 * margin
        val availHeight = pageHeight - 2 * margin
        val scale = minOf(availWidth.toFloat() / bitmapWidth, availHeight.toFloat() / bitmapHeight)
        val scaledWidth = (bitmapWidth * scale).toInt()
        val scaledHeight = (bitmapHeight * scale).toInt()
        val xOffset = margin + (availWidth - scaledWidth) / 2
        val yOffset = margin + (availHeight - scaledHeight) / 2

        sb.appendLine("$xOffset $yOffset translate")
        sb.appendLine("$scaledWidth $scaledHeight scale")
        sb.appendLine("/picstr $bitmapWidth string def")
        sb.appendLine("$bitmapWidth $bitmapHeight 8")
        sb.appendLine("[${bitmapWidth} 0 0 -${bitmapHeight} 0 ${bitmapHeight}]")
        sb.appendLine("{ currentfile picstr readhexstring pop } image")

        // Encode bitmap as hex
        val hexData = bitmapToGrayscaleHex(bitmap)
        var charCount = 0
        for (hexChar in hexData) {
            sb.append(hexChar)
            charCount++
            if (charCount % 80 == 0) sb.appendLine()
        }

        sb.appendLine()
        sb.appendLine("showpage")
        sb.appendLine("%%EOF")
        return sb.toString().toByteArray(Charsets.US_ASCII)
    }

    override fun buildPageBreak(): ByteArray = "showpage\n".toByteArray(Charsets.US_ASCII)

    override fun buildEndSequence(): ByteArray = byteArrayOf()

    private fun getPageDimensions(paperSize: PaperSize): Pair<Int, Int> {
        return when (paperSize) {
            PaperSize.A4 -> Pair(595, 842)
            PaperSize.LETTER -> Pair(612, 792)
            PaperSize.LEGAL -> Pair(612, 1008)
            PaperSize.A5 -> Pair(420, 595)
            PaperSize.A3 -> Pair(842, 1191)
            else -> Pair(595, 842)
        }
    }

    private fun bitmapToGrayscaleHex(bitmap: Bitmap): String {
        val sb = StringBuilder()
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val color = bitmap.getPixel(x, y)
                val gray = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)).toInt()
                sb.append("%02X".format(gray))
            }
        }
        return sb.toString()
    }
}
