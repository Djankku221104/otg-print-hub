package com.otgprinthub.print.adapters

import android.graphics.Bitmap
import android.graphics.Color
import com.otgprinthub.domain.model.Driver
import com.otgprinthub.domain.model.PrintSettings
import com.otgprinthub.util.HexUtils
import javax.inject.Inject

class EscPosAdapter(override val driver: Driver) : PrintAdapter {

    // ESC/POS command bytes
    private val ESC = 0x1B.toByte()
    private val GS = 0x1D.toByte()
    private val LF = 0x0A.toByte()
    private val CR = 0x0D.toByte()
    private val NUL = 0x00.toByte()

    override fun buildInitSequence(): ByteArray {
        val initHex = driver.initCommands ?: "1B40"
        return HexUtils.hexToBytes(initHex)
    }

    override fun buildResetSequence(): ByteArray = byteArrayOf(ESC, 0x40.toByte())

    override fun buildTextData(text: String, settings: PrintSettings): ByteArray {
        val result = mutableListOf<Byte>()

        // Set character encoding
        result.addAll(byteArrayOf(ESC, 0x74.toByte(), 0x00.toByte()).toList())  // PC437 charset

        // Default to left align
        result.addAll(byteArrayOf(ESC, 0x61.toByte(), 0x00.toByte()).toList())

        // Add text content line by line
        text.lines().forEach { line ->
            result.addAll(line.toByteArray(Charsets.UTF_8).toList())
            result.add(LF)
        }

        // Feed and cut at end
        result.addAll(buildCutSequence().toList())

        return result.toByteArray()
    }

    override fun buildImageData(bitmap: Bitmap, settings: PrintSettings): ByteArray {
        val dotsPerLine = driver.dotsPerLine ?: 576
        val scaledBitmap = scaleBitmapToWidth(bitmap, dotsPerLine)
        val monoBitmap = convertToMonochrome(scaledBitmap)
        return encodeRasterImage(monoBitmap)
    }

    override fun buildPageBreak(): ByteArray {
        // Feed some lines then cut
        return byteArrayOf(LF, LF, LF) + buildCutSequence()
    }

    override fun buildEndSequence(): ByteArray = buildCutSequence()

    fun buildCutSequence(): ByteArray {
        val cutHex = driver.cutCommand ?: "1D564100"
        return HexUtils.hexToBytes(cutHex)
    }

    fun buildBoldOn(): ByteArray = byteArrayOf(ESC, 0x45.toByte(), 0x01.toByte())
    fun buildBoldOff(): ByteArray = byteArrayOf(ESC, 0x45.toByte(), 0x00.toByte())
    fun buildAlignLeft(): ByteArray = byteArrayOf(ESC, 0x61.toByte(), 0x00.toByte())
    fun buildAlignCenter(): ByteArray = byteArrayOf(ESC, 0x61.toByte(), 0x01.toByte())
    fun buildAlignRight(): ByteArray = byteArrayOf(ESC, 0x61.toByte(), 0x02.toByte())

    fun buildQrCode(data: String, moduleSize: Int = 6): ByteArray {
        val result = mutableListOf<Byte>()
        val qrData = data.toByteArray(Charsets.UTF_8)
        val pL = ((qrData.size + 3) % 256).toByte()
        val pH = ((qrData.size + 3) / 256).toByte()

        // Model
        result.addAll(byteArrayOf(GS, 0x28.toByte(), 0x6B.toByte(), 0x04.toByte(), 0x00.toByte(), 0x31.toByte(), 0x41.toByte(), 0x32.toByte(), 0x00.toByte()))
        // Size
        result.addAll(byteArrayOf(GS, 0x28.toByte(), 0x6B.toByte(), 0x03.toByte(), 0x00.toByte(), 0x31.toByte(), 0x43.toByte(), moduleSize.toByte()))
        // Error correction
        result.addAll(byteArrayOf(GS, 0x28.toByte(), 0x6B.toByte(), 0x03.toByte(), 0x00.toByte(), 0x31.toByte(), 0x45.toByte(), 0x30.toByte()))
        // Store data
        result.addAll(byteArrayOf(GS, 0x28.toByte(), 0x6B.toByte(), pL, pH, 0x31.toByte(), 0x50.toByte(), 0x30.toByte()))
        result.addAll(qrData.toList())
        // Print
        result.addAll(byteArrayOf(GS, 0x28.toByte(), 0x6B.toByte(), 0x03.toByte(), 0x00.toByte(), 0x31.toByte(), 0x51.toByte(), 0x30.toByte()))

        return result.toByteArray()
    }

    fun buildBarcode(data: String, barcodeType: Int = 0x04): ByteArray {
        val barcodeData = data.toByteArray(Charsets.US_ASCII)
        val result = mutableListOf<Byte>()
        result.addAll(byteArrayOf(GS, 0x6B.toByte(), barcodeType.toByte()))
        result.addAll(barcodeData.toList())
        result.add(NUL)
        return result.toByteArray()
    }

    private fun scaleBitmapToWidth(bitmap: Bitmap, targetWidth: Int): Bitmap {
        if (bitmap.width == targetWidth) return bitmap
        val ratio = targetWidth.toFloat() / bitmap.width.toFloat()
        val newHeight = (bitmap.height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, targetWidth, newHeight, true)
    }

    private fun convertToMonochrome(bitmap: Bitmap): Bitmap {
        // Apply Floyd-Steinberg dithering
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val grayPixels = FloatArray(width * height) { i ->
            val color = pixels[i]
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            (0.299f * r + 0.587f * g + 0.114f * b) / 255f
        }

        // Floyd-Steinberg dithering
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val oldVal = grayPixels[idx]
                val newVal = if (oldVal > 0.5f) 1f else 0f
                grayPixels[idx] = newVal
                val error = oldVal - newVal

                if (x + 1 < width) grayPixels[idx + 1] += error * 7f / 16f
                if (y + 1 < height) {
                    if (x > 0) grayPixels[(y + 1) * width + (x - 1)] += error * 3f / 16f
                    grayPixels[(y + 1) * width + x] += error * 5f / 16f
                    if (x + 1 < width) grayPixels[(y + 1) * width + (x + 1)] += error * 1f / 16f
                }
            }
        }

        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val v = if (grayPixels[y * width + x] >= 0.5f) 0xFF else 0x00
                resultBitmap.setPixel(x, y, Color.rgb(v, v, v))
            }
        }
        return resultBitmap
    }

    private fun encodeRasterImage(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val bytesPerLine = (width + 7) / 8

        val result = mutableListOf<Byte>()

        // GS v 0 - raster bit image
        val xL = (bytesPerLine % 256).toByte()
        val xH = (bytesPerLine / 256).toByte()
        val yL = (height % 256).toByte()
        val yH = (height / 256).toByte()

        result.addAll(byteArrayOf(GS, 0x76.toByte(), 0x30.toByte(), 0x00.toByte(), xL, xH, yL, yH))

        for (y in 0 until height) {
            for (byteIdx in 0 until bytesPerLine) {
                var byte = 0
                for (bit in 0 until 8) {
                    val x = byteIdx * 8 + bit
                    if (x < width) {
                        val pixel = bitmap.getPixel(x, y)
                        val brightness = Color.red(pixel)
                        if (brightness < 128) {
                            byte = byte or (0x80 shr bit)
                        }
                    }
                }
                result.add(byte.toByte())
            }
        }

        return result.toByteArray()
    }
}
