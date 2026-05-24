package com.otgprinthub.printer

import android.util.Log

/**
 * ESC/P-R protocol command builder for Epson EcoTank / InkTank inkjet printers.
 *
 * ESC/P-R is a packet-based binary protocol used by modern Epson inkjets (L1455,
 * L3150, ET series, etc.) over USB. It is completely different from classic ESC/P Raster.
 *
 * Sources:
 *  - Epson open-source driver: epson-inkjet-printer-escpr
 *  - Gutenprint: backend/epson-escpr.c
 *  - USB captures from working Linux CUPS sessions
 */
object EscprProtocol {

    private const val TAG = "EscprProtocol"

    private val ESC = 0x1B.toByte()
    private val FF  = 0x0C.toByte()

    // ── Phase 2: Printer Reset ────────────────────────────────────────────────

    /** ESC @ — reset printer to power-on defaults */
    fun printerReset(): ByteArray {
        Log.d(TAG, "printerReset")
        return byteArrayOf(ESC, 0x40)
    }

    // ── Phase 3: Remote Mode ─────────────────────────────────────────────────

    /**
     * ESC ( R 08 00 00 "REMOTE1"
     * Enters ESC/P-R Remote Mode for job setup commands (JS, PP, DP, etc.)
     */
    fun enterRemoteMode(): ByteArray {
        Log.d(TAG, "enterRemoteMode → REMOTE1")
        return byteArrayOf(
            ESC, 0x28, 0x52, 0x08, 0x00, 0x00,
            0x52, 0x45, 0x4D, 0x4F, 0x54, 0x45, 0x31  // "REMOTE1"
        )
    }

    /**
     * ESC 00 00 00 — exits remote mode, returns to normal ESC/P state.
     * Wait ~100ms after this before sending raster commands.
     */
    fun exitRemoteMode(): ByteArray {
        Log.d(TAG, "exitRemoteMode")
        return byteArrayOf(ESC, 0x00, 0x00, 0x00)
    }

    /**
     * Generic remote command builder.
     * Format: [name0][name1][nL][nH][data]
     * name = 2 ASCII chars, nL/nH = data length (little-endian)
     */
    private fun remoteCmd(name: String, data: ByteArray): ByteArray {
        Log.d(TAG, "remoteCmd: $name (${data.size} bytes)")
        val out = ByteArray(4 + data.size)
        out[0] = name[0].code.toByte()
        out[1] = name[1].code.toByte()
        out[2] = (data.size and 0xFF).toByte()
        out[3] = ((data.size shr 8) and 0xFF).toByte()
        data.copyInto(out, 4)
        return out
    }

    /**
     * TI — Timestamp (optional but some firmware expects it).
     * 4 bytes: seconds since epoch, big-endian. We use zero for simplicity.
     */
    fun timestamp(): ByteArray = remoteCmd("TI", byteArrayOf(0x00, 0x00, 0x00, 0x00))

    /**
     * JS — Job Start. Signals beginning of a print job.
     * 4 zero bytes (job ID placeholder).
     */
    fun jobStart(): ByteArray = remoteCmd("JS", byteArrayOf(0x00, 0x00, 0x00, 0x00))

    /**
     * PP — Page Parameters.
     * Format: paper_type(1) orientation(1) top_margin(2LE) bottom_margin(2LE)
     *
     * paper_type: 0x00=Letter, 0x03=A4, 0x06=A3
     * orientation: 0x00=portrait, 0x01=landscape
     * margins: in 1/3600-inch units (use 0 — bitmap fills printable area)
     */
    fun pageParams(
        paperTypeCode: Byte = 0x03,   // 0x03 = A4
        portrait: Boolean = true,
        topMargin: Int = 0,
        bottomMargin: Int = 0
    ): ByteArray {
        val data = ByteArray(6)
        data[0] = paperTypeCode
        data[1] = if (portrait) 0x00 else 0x01
        data[2] = (topMargin and 0xFF).toByte()
        data[3] = ((topMargin shr 8) and 0xFF).toByte()
        data[4] = (bottomMargin and 0xFF).toByte()
        data[5] = ((bottomMargin shr 8) and 0xFF).toByte()
        Log.d(TAG, "pageParams: paperType=0x${paperTypeCode.toInt().and(0xFF).toString(16)} portrait=$portrait")
        return remoteCmd("PP", data)
    }

    /**
     * DP — Dot Parameters (resolution / quality).
     * Format: xdpi(2 big-endian) ydpi(2 big-endian) 00 00 00 00
     * Note: DPI bytes are big-endian in DP (unlike most other LE fields).
     */
    fun dotParams(dpi: Int = 360): ByteArray {
        val data = ByteArray(8)
        data[0] = ((dpi shr 8) and 0xFF).toByte()
        data[1] = (dpi and 0xFF).toByte()
        data[2] = ((dpi shr 8) and 0xFF).toByte()
        data[3] = (dpi and 0xFF).toByte()
        // bytes 4-7 = 0x00 (quality flags, use printer default)
        Log.d(TAG, "dotParams: ${dpi} DPI")
        return remoteCmd("DP", data)
    }

    // ── Phase 4: Raster Mode ─────────────────────────────────────────────────

    /**
     * ESC ( G 01 00 01 — enter ESC/P-R graphics (raster) mode.
     * MUST be sent after exiting remote mode.
     */
    fun enterGraphicsMode(): ByteArray {
        Log.d(TAG, "enterGraphicsMode")
        return byteArrayOf(ESC, 0x28, 0x47, 0x01, 0x00, 0x01)
    }

    /**
     * ESC ( U 05 00 [unit × 5] — set unit for page-geometry commands.
     *
     * unit = 3600 / dpi (so 1 unit = 1/dpi inch = 1 pixel at that DPI).
     * Repeated 5 times as per Gutenprint epson-escpr backend.
     *
     * 360 DPI → unit=10 → 1B 28 55 05 00 0A 0A 0A 0A 0A
     * 180 DPI → unit=20 → 1B 28 55 05 00 14 14 14 14 14
     */
    fun setUnit(dpi: Int): ByteArray {
        val u = (3600 / dpi).toByte()
        Log.d(TAG, "setUnit: dpi=$dpi unit=0x${u.toInt().and(0xFF).toString(16)}")
        return byteArrayOf(ESC, 0x28, 0x55, 0x05, 0x00, u, u, u, u, u)
    }

    /**
     * ESC ( C 04 00 [height 32LE] — set page height.
     *
     * With unit=3600/dpi from setUnit(), 1 unit = 1 pixel at that DPI,
     * so heightPixels == heightUnits.
     *
     * CRITICAL: without this, printer waits for full default page height
     * and ignores FF — causing the "continuous blink / no eject" bug.
     */
    fun setPageHeight(heightPixels: Int): ByteArray {
        Log.d(TAG, "setPageHeight: $heightPixels px")
        return byteArrayOf(ESC, 0x28, 0x43, 0x04, 0x00) + int32LE(heightPixels)
    }

    /**
     * ESC ( V 04 00 [pos 32LE] — set absolute vertical print position.
     * Always 0 (start of page) for a single full page.
     */
    fun setVerticalPosition(posUnits: Int = 0): ByteArray {
        Log.d(TAG, "setVerticalPosition: $posUnits")
        return byteArrayOf(ESC, 0x28, 0x56, 0x04, 0x00) + int32LE(posUnits)
    }

    /**
     * ESC ( e [band_packet] — send a raster band.
     *
     * This is the core ESC/P-R raster command. Each call sends [lines] rows
     * of monochrome (1bpp) pixel data.
     *
     * Full byte sequence:
     *   1B 28 65                     ESC ( e
     *   [nL] [nH]                    total bytes after nL/nH = 8 + rasterData.size
     *   [color]                      0x00 = K (black)
     *   [compression]                0x00 = uncompressed
     *   [bpp_lo] [bpp_hi]            0x01 0x00 = 1 bit per pixel
     *   [width_lo] [width_hi]        pixels per raster line (LE)
     *   [lines_lo] [lines_hi]        number of lines in this band (LE)
     *   [rasterData]                 ceil(widthPixels/8) × lines bytes
     *
     * 1bpp format: MSB of byte = leftmost pixel, 1 = ink (dark), 0 = no ink (white).
     */
    fun bandData(
        widthPixels: Int,
        lines: Int,
        rasterData: ByteArray,
        color: Byte = 0x00,
        compression: Byte = 0x00
    ): ByteArray {
        val headerLen  = 8  // color + compression + bpp(2) + width(2) + lines(2)
        val totalLen   = headerLen + rasterData.size

        Log.d(TAG, "band: lines=$lines width=$widthPixels rasterBytes=${rasterData.size}")

        val out = mutableListOf<Byte>()
        out += byteArrayOf(ESC, 0x28, 0x65)    // ESC ( e
        out += int16LE(totalLen)                 // nL nH
        out.add(color)                           // color channel
        out.add(compression)                     // compression type
        out += int16LE(1)                        // bits per pixel = 1
        out += int16LE(widthPixels)              // pixels per line
        out += int16LE(lines)                    // number of lines
        out += rasterData                        // pixel data
        return out.toByteArray()
    }

    /** 0x0C — form feed, ejects page after last band */
    fun formFeed(): ByteArray {
        Log.d(TAG, "formFeed")
        return byteArrayOf(FF)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun int32LE(v: Int) = byteArrayOf(
        (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte()
    )

    fun int16LE(v: Int) = byteArrayOf(
        (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte()
    )

    private operator fun ByteArray.plus(b: ByteArray): ByteArray {
        val r = ByteArray(size + b.size); copyInto(r); b.copyInto(r, size); return r
    }

    private operator fun MutableList<Byte>.plusAssign(b: ByteArray) { addAll(b.toList()) }
    private fun MutableList<Byte>.toByteArray() = ByteArray(size) { this[it] }
}
