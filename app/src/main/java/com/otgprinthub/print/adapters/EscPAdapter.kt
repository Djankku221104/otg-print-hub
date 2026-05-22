package com.otgprinthub.print.adapters

import android.graphics.Bitmap
import android.graphics.Color
import com.otgprinthub.domain.model.Driver
import com.otgprinthub.domain.model.PrintSettings

class EscPAdapter(override val driver: Driver) : PrintAdapter {

    private val ESC = 0x1B.toByte()
    private val LF = 0x0A.toByte()
    private val FF = 0x0C.toByte()
    private val CR = 0x0D.toByte()

    override fun buildInitSequence(): ByteArray = byteArrayOf(ESC, 0x40.toByte())  // ESC @

    override fun buildResetSequence(): ByteArray = byteArrayOf(ESC, 0x40.toByte())

    override fun buildTextData(text: String, settings: PrintSettings): ByteArray {
        val result = mutableListOf<Byte>()
        result.addAll(buildInitSequence().toList())
        result.addAll(byteArrayOf(ESC, 0x33.toByte(), 24.toByte()).toList())  // 24/216 inch line spacing
        text.lines().forEach { line ->
            result.addAll(line.toByteArray(Charsets.ISO_8859_1).toList())
            result.add(CR)
            result.add(LF)
        }
        result.add(FF)
        return result.toByteArray()
    }

    override fun buildImageData(bitmap: Bitmap, settings: PrintSettings): ByteArray {
        val result = mutableListOf<Byte>()
        result.addAll(buildInitSequence().toList())

        // Set line spacing to 24 dots
        result.addAll(byteArrayOf(ESC, 0x33.toByte(), 24.toByte()).toList())

        val width = bitmap.width
        val height = bitmap.height
        val bandHeight = 24  // ESC/P uses 24-pin print heads

        var y = 0
        while (y < height) {
            val rowsInBand = minOf(bandHeight, height - y)
            val bytesPerCol = (rowsInBand + 7) / 8

            // ESC * - bit image mode
            // Mode 0: 8-dot single density
            // Mode 1: 8-dot double density
            // Mode 32: 24-dot low resolution
            // Mode 33: 24-dot high resolution
            val nL = (width % 256).toByte()
            val nH = (width / 256).toByte()
            result.addAll(byteArrayOf(ESC, 0x2A.toByte(), 0x21.toByte(), nL, nH).toList())  // ESC * 33

            for (x in 0 until width) {
                for (byteRow in 0 until 3) {  // 3 bytes = 24 pins
                    var byte = 0
                    for (pin in 0 until 8) {
                        val pixelY = y + byteRow * 8 + pin
                        if (pixelY < height) {
                            val pixel = bitmap.getPixel(x, pixelY)
                            val luminance = (0.299 * Color.red(pixel) +
                                    0.587 * Color.green(pixel) +
                                    0.114 * Color.blue(pixel)).toInt()
                            if (luminance < 128) {
                                byte = byte or (0x80 shr pin)
                            }
                        }
                    }
                    result.add(byte.toByte())
                }
            }

            result.add(CR)
            result.add(LF)
            y += bandHeight
        }

        result.add(FF)
        return result.toByteArray()
    }

    override fun buildPageBreak(): ByteArray = byteArrayOf(FF)

    override fun buildEndSequence(): ByteArray = byteArrayOf(ESC, 0x40.toByte())
}
