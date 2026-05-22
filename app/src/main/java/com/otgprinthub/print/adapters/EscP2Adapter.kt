package com.otgprinthub.print.adapters

import android.graphics.Bitmap
import android.graphics.Color
import com.otgprinthub.domain.model.Driver
import com.otgprinthub.domain.model.PrintQuality
import com.otgprinthub.domain.model.PrintSettings

/**
 * ESC/P Raster adapter for Epson inkjet printers (L1455, L3150, EcoTank series).
 *
 * Protocol reference: Epson ESC/P Raster command set (same as Gutenprint escp2 driver).
 *
 * Key facts:
 *   - ESC . v h m nL nH [data]:  v/h are in units of 1/720 inch (NOT affected by ESC(U).
 *     m=0x01 = 1 bit per dot (monochrome bitmask, 8 dots per byte). NEVER use m=0x08.
 *   - ESC(U sets the unit for page-geometry commands (ESC(C, ESC(S, ESC(c, ESC(V) only.
 *   - ESC(D sets the print resolution: base=1440, so for 360 DPI → nRes=4.
 *   - Only Epson-valid DPI values work: 180, 360, 720 (divide evenly into 720 and 1440).
 */
class EscP2Adapter(override val driver: Driver) : PrintAdapter {

    private val ESC = 0x1B.toByte()
    private val FF  = 0x0C.toByte()
    private val CR  = 0x0D.toByte()
    private val LF  = 0x0A.toByte()

    // ── Init / reset ────────────────────────────────────────────────────────────

    override fun buildInitSequence(): ByteArray = byteArrayOf(
        ESC, 0x40              // ESC @ — reset printer to defaults
    )

    override fun buildResetSequence(): ByteArray = byteArrayOf(ESC, 0x40)

    // ── Text (non-raster, plain ESC/P text mode) ────────────────────────────────

    override fun buildTextData(text: String, settings: PrintSettings): ByteArray {
        val result = mutableListOf<Byte>()
        result += byteArrayOf(ESC, 0x40)          // reset
        result += byteArrayOf(ESC, 0x33, 30)      // 30/180 inch line spacing
        text.lines().forEach { line ->
            result += line.toByteArray(Charsets.ISO_8859_1)
            result += CR
            result += LF
        }
        result += FF
        return result.toByteArray()
    }

    // ── Image (raster mode) ─────────────────────────────────────────────────────

    override fun buildImageData(bitmap: Bitmap, settings: PrintSettings): ByteArray {
        val dpi   = snapDpi(settings.quality.dpi)
        val width  = bitmap.width
        val height = bitmap.height

        // v/h for ESC . : units of 1/720 inch  (e.g. 360 DPI → 720/360 = 2)
        val vh    = (720 / dpi).toByte()
        // nRes for ESC(D: base 1440              (e.g. 360 DPI → 1440/360 = 4)
        val nRes  = (1440 / dpi)

        // ESC(U unit: 2 = 1/360 inch (we always use 1/360 as base unit for page commands)
        val unitVal = 2

        // Page height in units of 1/360 inch
        val pageHeightUnits = (height.toFloat() / dpi * 360).toInt()
        // Page width in units of 1/360 inch
        val pageWidthUnits  = (width.toFloat()  / dpi * 360).toInt()

        val bytesPerLine = (width + 7) / 8
        val nL = (width % 256).toByte()
        val nH = (width / 256).toByte()

        val result = mutableListOf<Byte>()

        // ── 1. Reset ──────────────────────────────────────────────────────────
        result += byteArrayOf(ESC, 0x40)

        // ── 2. Enter ESC/P Raster mode ───────────────────────────────────────
        // ESC ( G  01 00  01
        result += byteArrayOf(ESC, 0x28, 0x47, 0x01, 0x00, 0x01)

        // ── 3. Monochrome (K channel only) ───────────────────────────────────
        // ESC ( K  02 00  00 00   (colour mode = monochrome)
        result += byteArrayOf(ESC, 0x28, 0x4B, 0x02, 0x00, 0x00, 0x00)

        // ── 4. Set unit for page commands: 1/360 inch ────────────────────────
        // ESC ( U  01 00  unitVal
        result += byteArrayOf(ESC, 0x28, 0x55, 0x01, 0x00, unitVal.toByte())

        // ── 5. Set resolution ────────────────────────────────────────────────
        // ESC ( D  06 00  base(16LE) nRes(16LE) nRes(16LE)
        //   base = 1440 = 0x05A0
        result += byteArrayOf(
            ESC, 0x28, 0x44, 0x06, 0x00,
            0xA0.toByte(), 0x05,                   // base = 1440 little-endian
            (nRes and 0xFF).toByte(), ((nRes shr 8) and 0xFF).toByte(),  // h res
            (nRes and 0xFF).toByte(), ((nRes shr 8) and 0xFF).toByte()   // v res
        )

        // ── 6. Page height ────────────────────────────────────────────────────
        // ESC ( C  04 00  height(32LE)
        result += byteArrayOf(ESC, 0x28, 0x43, 0x04, 0x00)
        result += int32LE(pageHeightUnits)

        // ── 7. Paper size ─────────────────────────────────────────────────────
        // ESC ( S  08 00  width(32LE) height(32LE)
        result += byteArrayOf(ESC, 0x28, 0x53, 0x08, 0x00)
        result += int32LE(pageWidthUnits)
        result += int32LE(pageHeightUnits)

        // ── 8. Print area ─────────────────────────────────────────────────────
        // ESC ( c  08 00  top(32LE) bottom(32LE)   (0 = top of page)
        result += byteArrayOf(ESC, 0x28, 0x63, 0x08, 0x00)
        result += int32LE(0)
        result += int32LE(pageHeightUnits)

        // ── 9. Vertical position = 0 ─────────────────────────────────────────
        // ESC ( V  04 00  0(32LE)
        result += byteArrayOf(ESC, 0x28, 0x56, 0x04, 0x00)
        result += int32LE(0)

        // ── 10. Raster lines ──────────────────────────────────────────────────
        val rowPixels = IntArray(width)
        for (y in 0 until height) {
            bitmap.getPixels(rowPixels, 0, width, 0, y, width, 1)

            val lineData = ByteArray(bytesPerLine)
            for (x in 0 until width) {
                val pixel = rowPixels[x]
                val lum = (Color.red(pixel) * 299 +
                           Color.green(pixel) * 587 +
                           Color.blue(pixel) * 114) / 1000
                if (lum < 128) {
                    lineData[x / 8] = (lineData[x / 8].toInt() or (0x80 shr (x % 8))).toByte()
                }
            }

            // ESC . compression v h m nL nH [data]
            //   compression = 0  (uncompressed)
            //   m = 1            (1 bit per dot → monochrome bitmask)
            result += byteArrayOf(ESC, 0x2E, 0x00, vh, vh, 0x01, nL, nH)
            result += lineData
        }

        // ── 11. Form feed / end page ─────────────────────────────────────────
        result += FF

        return result.toByteArray()
    }

    // ── Multi-page helpers ──────────────────────────────────────────────────────

    override fun buildPageBreak(): ByteArray = byteArrayOf(FF)

    override fun buildEndSequence(): ByteArray = byteArrayOf(ESC, 0x40)

    // Each page from buildImageData already begins with ESC@ and ends with FF,
    // so we just concatenate and add a final reset — no extra wrapping needed.
    override fun buildFullPrintJob(pages: List<ByteArray>): ByteArray {
        val result = mutableListOf<Byte>()
        pages.forEach { page -> result += page }
        result += byteArrayOf(ESC, 0x40)  // final reset
        return result.toByteArray()
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    // Snap to nearest Epson-valid DPI so 720/dpi and 1440/dpi are always integers
    private fun snapDpi(requested: Int): Int = when {
        requested <= 180 -> 180
        requested <= 360 -> 360
        else             -> 720
    }

    private fun int32LE(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte()
    )

    private operator fun MutableList<Byte>.plusAssign(bytes: ByteArray) { addAll(bytes.toList()) }
    private operator fun MutableList<Byte>.plusAssign(byte: Byte) { add(byte) }
}
