package com.otgprinthub.printer

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log

/**
 * Converts Android Bitmap to 1bpp monochrome raster rows for ESC/P-R printing.
 *
 * Output format (per ESC/P-R band spec):
 *  - 1 bit per pixel
 *  - MSB of each byte = leftmost pixel
 *  - 1 = ink (dark), 0 = no ink (white)
 *  - Row size = ceil(width / 8) bytes
 */
object ImageToRasterConverter {

    private const val TAG = "ImageToRasterConverter"

    /**
     * Convert bitmap to list of 1bpp monochrome raster rows.
     * Uses luminance threshold (< 128 = ink dot).
     *
     * @return List of ByteArray, one per row, each of size ceil(width/8)
     */
    fun toMonochromeRows(bitmap: Bitmap): List<ByteArray> {
        val width  = bitmap.width
        val height = bitmap.height
        val bytesPerRow = (width + 7) / 8

        Log.d(TAG, "toMonochromeRows: ${width}×${height} → ${bytesPerRow} bytes/row")

        val rows   = ArrayList<ByteArray>(height)
        val pixels = IntArray(width)

        for (y in 0 until height) {
            bitmap.getPixels(pixels, 0, width, 0, y, width, 1)
            val row = ByteArray(bytesPerRow)
            for (x in 0 until width) {
                val p = pixels[x]
                // Weighted luminance (ITU-R BT.601)
                val lum = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
                if (lum < 128) {  // dark pixel → ink dot → bit = 1
                    row[x / 8] = (row[x / 8].toInt() or (0x80 shr (x % 8))).toByte()
                }
            }
            rows.add(row)
        }
        return rows
    }

    /**
     * Pack a slice of rows into a contiguous byte array for use in bandData().
     *
     * @param rows    full list of raster rows
     * @param start   first row index (inclusive)
     * @param end     last row index (exclusive)
     */
    fun packRows(rows: List<ByteArray>, start: Int, end: Int): ByteArray {
        if (rows.isEmpty() || start >= end) return ByteArray(0)
        val rowSize  = rows[0].size
        val count    = end - start
        val result   = ByteArray(count * rowSize)
        for (i in 0 until count) {
            rows[start + i].copyInto(result, i * rowSize)
        }
        return result
    }

    /**
     * Generate a small all-black test block (diagnostic only).
     * Produces [lines] rows, each [widthPixels] wide, all pixels = ink.
     */
    fun solidBlackRows(widthPixels: Int, lines: Int): List<ByteArray> {
        val bytesPerRow = (widthPixels + 7) / 8
        val row = ByteArray(bytesPerRow) { 0xFF.toByte() }
        return List(lines) { row.copyOf() }
    }
}
