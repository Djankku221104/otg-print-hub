package com.otgprinthub.print.adapters

import android.graphics.Bitmap
import android.graphics.Color
import com.otgprinthub.domain.model.Driver
import com.otgprinthub.domain.model.PrintSettings

/**
 * ESC/P Raster adapter for Epson inkjet printers (L1455, L3150, EcoTank series).
 *
 * Minimum required sequence (per Epson ESC/P Raster specification):
 *   ESC @          — reset printer
 *   ESC ( G 01 00 01  — enter raster graphics mode
 *   [per row] ESC . 00 vh vh 01 nL nH [ceil(width/8) bytes]
 *   FF             — eject page
 *
 * ESC . parameters:
 *   compression = 0x00 (uncompressed)
 *   v = h = 720/dpi  (1/720 inch units; e.g. 360 DPI → 2, 180 DPI → 4)
 *   m = 0x01        (1 bit per dot = monochrome bitmask, 8 dots per byte)
 *   nL, nH          (number of dots wide, little-endian 16-bit)
 *   data            (ceil(nDots/8) bytes, MSB=leftmost dot, 1=dark)
 */
class EscP2Adapter(override val driver: Driver) : PrintAdapter {

    private val ESC = 0x1B.toByte()
    private val FF  = 0x0C.toByte()
    private val CR  = 0x0D.toByte()
    private val LF  = 0x0A.toByte()

    override fun buildInitSequence(): ByteArray = byteArrayOf(ESC, 0x40)

    override fun buildResetSequence(): ByteArray = byteArrayOf(ESC, 0x40)

    override fun buildTextData(text: String, settings: PrintSettings): ByteArray {
        val result = mutableListOf<Byte>()
        result += byteArrayOf(ESC, 0x40)        // reset (exits raster mode)
        result += byteArrayOf(ESC, 0x33, 30)    // 30/180 inch line spacing
        text.lines().forEach { line ->
            result += line.toByteArray(Charsets.ISO_8859_1)
            result += CR
            result += LF
        }
        result += FF
        return result.toByteArray()
    }

    override fun buildImageData(bitmap: Bitmap, settings: PrintSettings): ByteArray {
        val dpi  = snapDpi(settings.quality.dpi)
        val vh   = (720 / dpi).toByte()   // vertical/horizontal unit in 1/720 inch
        val width        = bitmap.width
        val height       = bitmap.height
        val bytesPerLine = (width + 7) / 8
        val nL = (width and 0xFF).toByte()
        val nH = ((width shr 8) and 0xFF).toByte()

        val result = mutableListOf<Byte>()

        // ── 1. Reset ─────────────────────────────────────────────────────────
        result += byteArrayOf(ESC, 0x40)

        // ── 2. Enter ESC/P Raster mode ───────────────────────────────────────
        result += byteArrayOf(ESC, 0x28, 0x47, 0x01, 0x00, 0x01)

        // ── 3. Raster lines ──────────────────────────────────────────────────
        val rowPixels = IntArray(width)
        for (y in 0 until height) {
            bitmap.getPixels(rowPixels, 0, width, 0, y, width, 1)

            val lineData = ByteArray(bytesPerLine)
            for (x in 0 until width) {
                val p = rowPixels[x]
                val lum = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
                if (lum < 128) lineData[x / 8] = (lineData[x / 8].toInt() or (0x80 shr (x % 8))).toByte()
            }

            // ESC . compression v h m nL nH [data]
            result += byteArrayOf(ESC, 0x2E, 0x00, vh, vh, 0x01, nL, nH)
            result += lineData
        }

        // ── 4. Eject page ─────────────────────────────────────────────────────
        result += FF

        return result.toByteArray()
    }

    override fun buildPageBreak(): ByteArray = byteArrayOf(FF)

    override fun buildEndSequence(): ByteArray = byteArrayOf(ESC, 0x40)

    // Each page from buildImageData already has ESC@ + raster data + FF.
    // Don't wrap with extra init/breaks to avoid double FF (blank page between pages).
    override fun buildFullPrintJob(pages: List<ByteArray>): ByteArray {
        val result = mutableListOf<Byte>()
        pages.forEach { page -> result += page }
        result += byteArrayOf(ESC, 0x40)  // final reset
        return result.toByteArray()
    }

    // Snap to nearest Epson-valid DPI: 720/dpi must be a whole number
    private fun snapDpi(requested: Int): Int = when {
        requested <= 180 -> 180
        requested <= 360 -> 360
        else             -> 720
    }

    private operator fun MutableList<Byte>.plusAssign(bytes: ByteArray) { addAll(bytes.toList()) }
    private operator fun MutableList<Byte>.plusAssign(byte: Byte) { add(byte) }
}
