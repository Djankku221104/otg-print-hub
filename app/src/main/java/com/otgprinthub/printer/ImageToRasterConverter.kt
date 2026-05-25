package com.otgprinthub.printer

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log

/**
 * Converts Android Bitmap to raster rows for ESC/P-R (ESCPR) printing.
 *
 * Pixel formats:
 *   toInkRows()         — CM.MONOCHROME: 1 byte/pixel, 0x00=white, 0xFF=black
 *   solidBlackInkRows() — diagnostic: all 0xFF (solid black block)
 *   toMonochromeRows()  — legacy 1bpp packed (for old ESC . tests, not ESCPR)
 */
object ImageToRasterConverter {

    private const val TAG = "RasterConv"

    /**
     * Convert bitmap to ESCPR CM.MONOCHROME rows: 1 byte per pixel.
     *
     * Ink density encoding: 0x00 = no ink (white paper), 0xFF = full ink (black).
     * Formula: inkByte = 255 - luminance
     *   White pixel (lum=255) → 0x00 (no ink) ✓
     *   Black pixel (lum=0)   → 0xFF (full ink) ✓
     *
     * @return List<ByteArray>, one per row, each width bytes long.
     */
    fun toInkRows(bitmap: Bitmap): List<ByteArray> {
        val width  = bitmap.width
        val height = bitmap.height
        Log.d(TAG, "toInkRows: ${width}x${height} → $width bytes/row (CM.MONO)")

        val rows   = ArrayList<ByteArray>(height)
        val pixels = IntArray(width)

        for (y in 0 until height) {
            bitmap.getPixels(pixels, 0, width, 0, y, width, 1)
            val row = ByteArray(width)
            for (x in 0 until width) {
                val p   = pixels[x]
                // CM.MONOCHROME: send raw luminance (same as RGB lightness)
                // 0x00 = dark pixel = printer applies full ink = black output
                // 0xFF = bright pixel = printer applies no ink = white output
                val lum = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
                row[x]  = lum.toByte()
            }
            rows.add(row)
        }
        return rows
    }

    /**
     * Solid black test rows for CM.MONOCHROME.
     * 0x00 = dark = full ink = black output.
     */

    /**
     * Convert bitmap to ESCPR CM.COLOR rows: 3 bytes per pixel (R, G, B).
     * Same luminance convention as mono: high value = bright = less ink.
     *   White (255,255,255) → [FF FF FF] → no ink → white output ✓
     *   Black (0,0,0)       → [00 00 00] → full ink → black output ✓
     *   Red   (255,0,0)     → [FF 00 00] → no red ink, full G+B ink → printed as red ✓
     */
    fun toColorInkRows(bitmap: Bitmap): List<ByteArray> {
        val width  = bitmap.width
        val height = bitmap.height
        Log.d(TAG, "toColorInkRows: ${width}x${height} → ${width * 3} bytes/row (CM.COLOR)")
        val rows   = ArrayList<ByteArray>(height)
        val pixels = IntArray(width)
        for (y in 0 until height) {
            bitmap.getPixels(pixels, 0, width, 0, y, width, 1)
            val row = ByteArray(width * 3)
            for (x in 0 until width) {
                val p = pixels[x]
                row[x * 3]     = Color.red(p).toByte()
                row[x * 3 + 1] = Color.green(p).toByte()
                row[x * 3 + 2] = Color.blue(p).toByte()
            }
            rows.add(row)
        }
        return rows
    }

    fun solidBlackInkRows(widthPx: Int, lines: Int): List<ByteArray> {
        Log.d(TAG, "solidBlackInkRows: ${widthPx}x${lines}")
        val row = ByteArray(widthPx) { 0x00.toByte() }  // 0x00 = black (not 0xFF!)
        return List(lines) { row.copyOf() }
    }

    // ── Legacy 1bpp format (for old T1-T5 test pages, not used by ESCPR) ─────

    /**
     * Convert bitmap to 1bpp packed rows for classic ESC/P Raster (ESC . command).
     * MSB = leftmost pixel, 1 = ink, 0 = no ink. Row size = ceil(width/8) bytes.
     */
    fun toMonochromeRows(bitmap: Bitmap): List<ByteArray> {
        val width       = bitmap.width
        val height      = bitmap.height
        val bytesPerRow = (width + 7) / 8
        Log.d(TAG, "toMonochromeRows: ${width}x${height} → $bytesPerRow bytes/row (1bpp)")

        val rows   = ArrayList<ByteArray>(height)
        val pixels = IntArray(width)

        for (y in 0 until height) {
            bitmap.getPixels(pixels, 0, width, 0, y, width, 1)
            val row = ByteArray(bytesPerRow)
            for (x in 0 until width) {
                val p   = pixels[x]
                val lum = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
                if (lum < 128) row[x / 8] = (row[x / 8].toInt() or (0x80 shr (x % 8))).toByte()
            }
            rows.add(row)
        }
        return rows
    }

    fun packRows(rows: List<ByteArray>, start: Int, end: Int): ByteArray {
        if (rows.isEmpty() || start >= end) return ByteArray(0)
        val rowSize = rows[0].size
        val count   = end - start
        val result  = ByteArray(count * rowSize)
        for (i in 0 until count) rows[start + i].copyInto(result, i * rowSize)
        return result
    }

    fun solidBlackRows(widthPixels: Int, lines: Int): List<ByteArray> {
        val bytesPerRow = (widthPixels + 7) / 8
        val row = ByteArray(bytesPerRow) { 0xFF.toByte() }
        return List(lines) { row.copyOf() }
    }
}
