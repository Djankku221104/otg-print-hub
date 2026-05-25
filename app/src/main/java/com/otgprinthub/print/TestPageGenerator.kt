package com.otgprinthub.print

import android.util.Log
import com.otgprinthub.printer.EscprProtocol

/**
 * ESCPR test pages for Epson L1455 (EcoTank/InkTank — ESCPR7 protocol only).
 *
 *   T1 ESCPR Solid    — 100 solid-black lines. Protocol baseline test.
 *   T2 ESCPR Gray     — 100 lines at 50% gray. Tests mid-tone ink control.
 *   T3 Text Mode      — Plain ASCII via ESC/P text commands. No raster.
 *   T4 ESCPR Stripes  — Alternating 30-line black/white bands. Tests band accuracy.
 *   T5 ESCPR Nozzle   — 12 evenly-spaced vertical lines. Tests nozzle alignment.
 *   T6 ESCPR Full     — 500 black lines (~1/3 page). Tests sustained data transfer.
 */
object TestPageGenerator {

    private val ESC = 0x1B.toByte()
    private val FF  = 0x0C.toByte()
    private val CR  = 0x0D.toByte()
    private val LF  = 0x0A.toByte()

    private const val TAG = "TestPageGen"

    // A4 @ 360 DPI
    private const val DPI      = 360
    private const val WIDTH_PX = 2976   // 210mm
    private const val HEIGHT_PX = 4209  // 297mm

    enum class TestType(val displayName: String, val description: String) {
        ESCPR_SOLID(
            "1. ESCPR Solid Black ★",
            "100 solid-black lines at top. Protocol baseline — run first."
        ),
        ESCPR_GRAY(
            "2. ESCPR 50% Gray",
            "100 lines at half-density. Tests mid-tone ink control."
        ),
        TEXT_MODE(
            "3. Text Mode",
            "Plain ASCII text — no raster. Tests basic ESC/P text path."
        ),
        ESCPR_STRIPES(
            "4. ESCPR Stripes",
            "Alternating 30-line black/white bands. Tests band accuracy."
        ),
        ESCPR_NOZZLE(
            "5. ESCPR Nozzle Check",
            "12 vertical lines across width. Checks nozzle alignment."
        ),
        ESCPR_FULL(
            "6. ESCPR 500 Lines",
            "500 solid-black lines (~1/3 page). Tests sustained transfer."
        )
    }

    fun generate(type: TestType): ByteArray = when (type) {
        TestType.ESCPR_SOLID   -> escprSolid()
        TestType.ESCPR_GRAY    -> escprGray()
        TestType.TEXT_MODE     -> textMode()
        TestType.ESCPR_STRIPES -> escprStripes()
        TestType.ESCPR_NOZZLE  -> escprNozzle()
        TestType.ESCPR_FULL    -> escprFull()
    }

    // ── T1: ESCPR Solid Black ─────────────────────────────────────────────────────
    private fun escprSolid(): ByteArray {
        val lines = 100
        Log.i(TAG, "escprSolid: $lines black lines @ $WIDTH_PX px")
        val blackRow = ByteArray(WIDTH_PX) { 0x00 }
        return buildEscprJob(lines) { y -> blackRow }
    }

    // ── T2: ESCPR 50% Gray ────────────────────────────────────────────────────────
    private fun escprGray(): ByteArray {
        val lines = 100
        Log.i(TAG, "escprGray: $lines gray lines @ $WIDTH_PX px")
        val grayRow = ByteArray(WIDTH_PX) { 0x80.toByte() }   // 50% luminance = 50% ink
        return buildEscprJob(lines) { y -> grayRow }
    }

    // ── T3: Text Mode ─────────────────────────────────────────────────────────────
    private fun textMode(): ByteArray {
        val lines = listOf(
            "================================",
            "   OTG PRINT HUB - TEST PAGE   ",
            "================================",
            "",
            "Test 3: ESC/P Text Mode",
            "No raster - pure text commands",
            "",
            "abcdefghijklmnopqrstuvwxyz",
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
            "0123456789 !@#\$%^&*()",
            "",
            "If you can read this, text",
            "mode is working correctly.",
            "",
            "================================",
        )
        val result = mutableListOf<Byte>()
        result += byteArrayOf(ESC, 0x40)
        result += byteArrayOf(ESC, 0x33, 30)
        lines.forEach { line ->
            result += line.toByteArray(Charsets.ISO_8859_1)
            result += CR
            result += LF
        }
        result += FF
        return result.toByteArray()
    }

    // ── T4: ESCPR Stripes ─────────────────────────────────────────────────────────
    private fun escprStripes(): ByteArray {
        val bandHeight = 30
        val bands = 6                            // 3 black + 3 white = 180 lines total
        val totalLines = bandHeight * bands
        Log.i(TAG, "escprStripes: $bands bands x $bandHeight lines = $totalLines lines")
        val blackRow = ByteArray(WIDTH_PX) { 0x00 }
        val whiteRow = ByteArray(WIDTH_PX) { 0xFF.toByte() }
        return buildEscprJob(totalLines) { y ->
            if ((y / bandHeight) % 2 == 0) blackRow else whiteRow
        }
    }

    // ── T5: ESCPR Nozzle Check ────────────────────────────────────────────────────
    private fun escprNozzle(): ByteArray {
        val lines = 200
        val numLines = 12
        val lineWidth = 8   // px per vertical line
        val spacing = WIDTH_PX / (numLines + 1)

        Log.i(TAG, "escprNozzle: $numLines vertical lines, spacing=${spacing}px, $lines rows")

        // Pre-build the nozzle row (same for every y)
        val nozzleRow = ByteArray(WIDTH_PX) { 0xFF.toByte() }   // white base
        for (n in 1..numLines) {
            val start = (spacing * n) - lineWidth / 2
            for (x in start until (start + lineWidth)) {
                if (x in 0 until WIDTH_PX) nozzleRow[x] = 0x00   // black line
            }
        }

        return buildEscprJob(lines) { nozzleRow }
    }

    // ── T6: ESCPR Full 500 Lines ──────────────────────────────────────────────────
    private fun escprFull(): ByteArray {
        val lines = 500
        Log.i(TAG, "escprFull: $lines black lines @ $WIDTH_PX px")
        val blackRow = ByteArray(WIDTH_PX) { 0x00 }
        return buildEscprJob(lines) { blackRow }
    }

    // ── ESCPR job builder ─────────────────────────────────────────────────────────

    private fun buildEscprJob(printLines: Int, rowProvider: (Int) -> ByteArray): ByteArray {
        val chunks = mutableListOf<ByteArray>()

        chunks += EscprProtocol.exitPacketMode()
        chunks += EscprProtocol.printerReset()
        chunks += EscprProtocol.enterRemote1()
        chunks += EscprProtocol.timestamp()
        chunks += EscprProtocol.jobStart()
        chunks += EscprProtocol.paperPath()
        chunks += EscprProtocol.exitRemote1()

        chunks += EscprProtocol.enterEscprMode()
        chunks += EscprProtocol.setQuality(mtid = 0, mqid = 1, cm = 1)
        chunks += EscprProtocol.setJob(WIDTH_PX, HEIGHT_PX, DPI)   // always full A4 — no size warning

        chunks += EscprProtocol.startPage()
        chunks += EscprProtocol.pageNumber(1)
        for (y in 0 until printLines) chunks += EscprProtocol.sendLine(y, rowProvider(y))
        // No endPage(0) — endJob finalizes and ejects without pre-loading a blank sheet
        chunks += EscprProtocol.endJob()

        val totalSize = chunks.sumOf { it.size }
        Log.i(TAG, "buildEscprJob: $printLines lines → $totalSize bytes (${totalSize / 1024} KB)")
        val result = ByteArray(totalSize)
        var offset = 0
        for (chunk in chunks) { chunk.copyInto(result, offset); offset += chunk.size }
        return result
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    private operator fun MutableList<Byte>.plusAssign(bytes: ByteArray) { addAll(bytes.toList()) }
    private operator fun MutableList<Byte>.plusAssign(byte: Byte) { add(byte) }
    private fun List<Byte>.toByteArray() = ByteArray(size) { this[it] }
}
