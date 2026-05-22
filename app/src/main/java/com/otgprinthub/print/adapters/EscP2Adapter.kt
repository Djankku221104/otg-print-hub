package com.otgprinthub.print.adapters

import android.graphics.Bitmap
import android.graphics.Color
import com.otgprinthub.domain.model.Driver
import com.otgprinthub.domain.model.PrintSettings

/**
 * ESC/P Raster adapter for Epson inkjet printers (e.g. L1455, L3150, EcoTank series).
 *
 * Uses ESC/P Raster mode (ESC ( G) rather than the dot-matrix ESC * commands in EscPAdapter.
 * Each image row is sent as a 1-bit-per-pixel monochrome raster line via ESC . (0x1B 0x2E).
 * Inkjet colour planes are not supported here; the DocumentRenderer is expected to supply
 * a grayscale/B&W bitmap when colour mode is not COLOR, or a colour bitmap for colour mode
 * (which this adapter converts to monochrome K-channel via luminance threshold).
 */
class EscP2Adapter(override val driver: Driver) : PrintAdapter {

    private val ESC = 0x1B.toByte()
    private val FF  = 0x0C.toByte()
    private val CR  = 0x0D.toByte()
    private val LF  = 0x0A.toByte()

    // ESC @ → reset;  ESC ( G → enter raster graphics mode
    override fun buildInitSequence(): ByteArray = byteArrayOf(
        ESC, 0x40,
        ESC, 0x28.toByte(), 0x47, 0x01, 0x00, 0x01
    )

    override fun buildResetSequence(): ByteArray = byteArrayOf(ESC, 0x40)

    override fun buildTextData(text: String, settings: PrintSettings): ByteArray {
        val result = mutableListOf<Byte>()
        result += byteArrayOf(ESC, 0x40)                   // reset (text mode, no raster)
        result += byteArrayOf(ESC, 0x33, 30)               // 30/180 inch line spacing
        text.lines().forEach { line ->
            result += line.toByteArray(Charsets.ISO_8859_1)
            result += CR
            result += LF
        }
        result += FF
        return result.toByteArray()
    }

    override fun buildImageData(bitmap: Bitmap, settings: PrintSettings): ByteArray {
        val result = mutableListOf<Byte>()
        result += buildInitSequence()

        val width = bitmap.width
        val height = bitmap.height
        val bytesPerLine = (width + 7) / 8
        val nL = (width % 256).toByte()
        val nH = (width / 256).toByte()

        for (y in 0 until height) {
            val lineData = ByteArray(bytesPerLine)
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val lum = (Color.red(pixel) * 299 +
                           Color.green(pixel) * 587 +
                           Color.blue(pixel) * 114) / 1000
                if (lum < 128) {
                    lineData[x / 8] = (lineData[x / 8].toInt() or (0x80 shr (x % 8))).toByte()
                }
            }

            // ESC . compression v h m nL nH [data]
            //   compression = 0  (uncompressed)
            //   v = 1, h = 1     (1 base-unit per dot; ~180 DPI with default unit)
            //   m = 8            (monochrome, 8 dots per byte)
            result += byteArrayOf(ESC, 0x2E, 0x00, 0x01, 0x01, 0x08, nL, nH)
            result += lineData
        }

        result += FF
        return result.toByteArray()
    }

    override fun buildPageBreak(): ByteArray = byteArrayOf(FF)
    override fun buildEndSequence(): ByteArray = byteArrayOf(ESC, 0x40)

    // Helpers to keep the loops clean
    private operator fun MutableList<Byte>.plusAssign(bytes: ByteArray) {
        addAll(bytes.toList())
    }
    private operator fun MutableList<Byte>.plusAssign(byte: Byte) {
        add(byte)
    }
}
