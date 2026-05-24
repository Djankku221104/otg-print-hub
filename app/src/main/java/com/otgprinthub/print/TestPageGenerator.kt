package com.otgprinthub.print

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import com.otgprinthub.print.adapters.EscP2Adapter
import com.otgprinthub.printer.EscprProtocol
import com.otgprinthub.printer.ImageToRasterConverter
import com.otgprinthub.domain.model.ColorMode
import com.otgprinthub.domain.model.Driver
import com.otgprinthub.domain.model.PrintProtocol
import com.otgprinthub.domain.model.PrintQuality
import com.otgprinthub.domain.model.PrintSettings

/**
 * Generates self-contained test pages for diagnosing Epson L1455 print issues.
 *
 * Each test targets a different layer:
 *   T1 RAW MINIMAL   — 30 black lines, tiny width, raw ESC/P bytes. No rendering code.
 *   T2 RAW STRIPES   — 3 thick stripes full A4 width, raw ESC/P bytes.
 *   T3 TEXT MODE     — Plain ASCII text via ESC/P text commands, zero raster.
 *   T4 BITMAP SIMPLE — Programmatic bitmap → EscP2Adapter (tests adapter pipeline).
 *   T5 BITMAP INFO   — Full info page: printer name, date, lines, checkerboard.
 */
object TestPageGenerator {

    private val ESC = 0x1B.toByte()
    private val FF  = 0x0C.toByte()
    private val CR  = 0x0D.toByte()
    private val LF  = 0x0A.toByte()

    enum class TestType(val displayName: String, val description: String) {
        RAW_MINIMAL(
            "1. Raw Minimal",
            "30 black lines, tiny. Proves basic raster works."
        ),
        RAW_STRIPES(
            "2. Raw Stripes",
            "3 thick black bands, A4 width. Tests full-width transfer."
        ),
        TEXT_MODE(
            "3. Text Mode",
            "Plain ASCII text — no raster at all."
        ),
        BITMAP_SIMPLE(
            "4. Bitmap Simple",
            "Checkerboard via normal adapter pipeline."
        ),
        BITMAP_INFO(
            "5. Bitmap Info Page",
            "Full info: date, lines, boxes."
        ),
        ESCPR_BLOCK(
            "6. ESCPR Block ★",
            "Correct ESCPR protocol: 100 solid-black lines via dsnd. USE THIS FIRST."
        )
    }

    // ── Public API ────────────────────────────────────────────────────────────────

    private const val TAG = "TestPageGen"

    fun generate(type: TestType): ByteArray = when (type) {
        TestType.RAW_MINIMAL   -> rawMinimal()
        TestType.RAW_STRIPES   -> rawStripes()
        TestType.TEXT_MODE     -> textMode()
        TestType.BITMAP_SIMPLE -> bitmapSimple()
        TestType.BITMAP_INFO   -> bitmapInfo()
        TestType.ESCPR_BLOCK   -> escprBlock()
    }

    // ── Test 1: Raw Minimal ───────────────────────────────────────────────────────
    // 200 dots wide, 30 all-black lines, 180 DPI.
    // ESC(U + ESC(C tell printer exactly how tall the page is so FF is respected.
    // Without ESC(C, printer waits for full A4 (2104 lines) and ignores FF → blinks.
    private fun rawMinimal(): ByteArray {
        val dpi   = 180
        val lines = 30
        val width = 200
        val bytesPerLine = (width + 7) / 8
        val nL = (width and 0xFF).toByte()
        val nH = ((width shr 8) and 0xFF).toByte()
        val vh    = (720 / dpi).toByte()    // 4  — ESC . unit: 1/720 inch
        val unitD = (3600 / dpi).toByte()   // 20 — ESC(U unit: 1/180 inch per unit → 1 unit = 1 line

        val result = mutableListOf<Byte>()
        result += byteArrayOf(ESC, 0x40)                               // reset
        result += byteArrayOf(ESC, 0x28, 0x47, 0x01, 0x00, 0x01)      // raster mode
        result += byteArrayOf(ESC, 0x28, 0x4B, 0x02, 0x00, 0x00, 0x00) // ESC(K monochrome
        result += byteArrayOf(ESC, 0x28, 0x55, 0x01, 0x00, unitD)     // set unit = 1/dpi inch
        result += byteArrayOf(ESC, 0x28, 0x43, 0x04, 0x00)            // page height:
        result += int32LE(lines)                                        //   30 units = 30 lines

        repeat(lines) {
            result += byteArrayOf(ESC, 0x2E, 0x00, vh, vh, 0x01, nL, nH)
            result += ByteArray(bytesPerLine) { 0xFF.toByte() }        // all black
        }
        result += FF
        return result.toByteArray()
    }

    // ── Test 2: Raw Stripes ───────────────────────────────────────────────────────
    // A4 width at 180 DPI = 1488 dots. 3 thick black bands + ESC(C for correct eject.
    private fun rawStripes(): ByteArray {
        val dpi   = 180
        val vh    = (720 / dpi).toByte()
        val unitD = (3600 / dpi).toByte()
        val width = mmToDots(210, dpi)
        val bytesPerLine = (width + 7) / 8
        val nL = (width and 0xFF).toByte()
        val nH = ((width shr 8) and 0xFF).toByte()
        val totalLines = 40 + 20 + 40 + 20 + 40  // 160

        val allBlack = ByteArray(bytesPerLine) { 0xFF.toByte() }
        val allWhite = ByteArray(bytesPerLine) { 0x00.toByte() }

        val result = mutableListOf<Byte>()
        result += byteArrayOf(ESC, 0x40)
        result += byteArrayOf(ESC, 0x28, 0x47, 0x01, 0x00, 0x01)
        result += byteArrayOf(ESC, 0x28, 0x4B, 0x02, 0x00, 0x00, 0x00) // ESC(K monochrome
        result += byteArrayOf(ESC, 0x28, 0x55, 0x01, 0x00, unitD)
        result += byteArrayOf(ESC, 0x28, 0x43, 0x04, 0x00)
        result += int32LE(totalLines)

        fun addLine(lineData: ByteArray) {
            result += byteArrayOf(ESC, 0x2E, 0x00, vh, vh, 0x01, nL, nH)
            result += lineData
        }

        repeat(40) { addLine(allBlack) }
        repeat(20) { addLine(allWhite) }
        repeat(40) { addLine(allBlack) }
        repeat(20) { addLine(allWhite) }
        repeat(40) { addLine(allBlack) }

        result += FF
        return result.toByteArray()
    }

    // ── Test 3: Text Mode ─────────────────────────────────────────────────────────
    // ESC/P text commands only — no raster. Uses character ROM of printer.
    // If this prints but raster tests don't, raster protocol is the issue.
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
        result += byteArrayOf(ESC, 0x40)            // reset (enters text mode)
        result += byteArrayOf(ESC, 0x33, 30)        // 30/180 inch line spacing

        lines.forEach { line ->
            result += line.toByteArray(Charsets.ISO_8859_1)
            result += CR
            result += LF
        }
        result += FF
        return result.toByteArray()
    }

    // ── Test 4: Bitmap Simple ─────────────────────────────────────────────────────
    // Generates a 200×200 checkerboard bitmap and sends via EscP2Adapter.
    // Tests: bitmap.getPixels → luminance → ESC . data path.
    private fun bitmapSimple(): ByteArray {
        val size = 200
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        val cellSize = 10
        for (y in 0 until size) {
            for (x in 0 until size) {
                val isBlack = ((x / cellSize) + (y / cellSize)) % 2 == 0
                bitmap.setPixel(x, y, if (isBlack) Color.BLACK else Color.WHITE)
            }
        }

        val adapter = EscP2Adapter(dummyDriver())
        val settings = PrintSettings(quality = PrintQuality.DRAFT)  // 180 DPI
        val data = adapter.buildImageData(bitmap, settings)
        bitmap.recycle()
        return data
    }

    // ── Test 5: Bitmap Info Page ─────────────────────────────────────────────────
    // Full A4 bitmap drawn with Canvas: text, lines, boxes, checkerboard corner.
    // This is the closest to a real print job — tests the complete pipeline.
    private fun bitmapInfo(): ByteArray {
        val dpi = 180
        val width  = mmToDots(210, dpi)   // 1488 px
        val height = mmToDots(297, dpi)   // 2104 px

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.MONOSPACE
        }

        var y = 80f

        // Title
        paint.textSize = 60f
        paint.isFakeBoldText = true
        canvas.drawText("OTG Print Hub - Test Page", 60f, y, paint)
        y += 80f

        // Separator line
        paint.isFakeBoldText = false
        paint.strokeWidth = 4f
        canvas.drawLine(60f, y, (width - 60).toFloat(), y, paint)
        y += 50f

        // Info lines
        paint.textSize = 36f
        val infos = listOf(
            "Test 5: Full Bitmap Info Page",
            "Resolution : 180 DPI",
            "Paper      : A4 (210 x 297 mm)",
            "Bitmap     : ${width} x ${height} px",
            "Protocol   : ESC/P Raster",
            "Adapter    : EscP2Adapter",
            "",
            "If this page prints correctly,",
            "the full pipeline is working.",
        )
        infos.forEach { line ->
            canvas.drawText(line, 60f, y, paint)
            y += 52f
        }

        y += 30f
        // Thick separator
        paint.strokeWidth = 6f
        canvas.drawLine(60f, y, (width - 60).toFloat(), y, paint)
        y += 50f

        // Horizontal line test (thin, medium, thick)
        paint.textSize = 32f
        canvas.drawText("Line thickness test:", 60f, y, paint)
        y += 50f
        for (stroke in listOf(1f, 3f, 6f, 12f)) {
            paint.strokeWidth = stroke
            canvas.drawLine(60f, y, (width - 60).toFloat(), y, paint)
            y += 35f
        }

        y += 30f
        // Checkerboard block (10×10 cells of 15px each)
        paint.strokeWidth = 0f
        canvas.drawText("Checkerboard block:", 60f, y, paint)
        y += 20f
        val cellPx = 18
        for (row in 0 until 12) {
            for (col in 0 until 20) {
                val isBlack = (row + col) % 2 == 0
                paint.color = if (isBlack) Color.BLACK else Color.WHITE
                canvas.drawRect(
                    60f + col * cellPx,
                    y + row * cellPx,
                    60f + (col + 1) * cellPx,
                    y + (row + 1) * cellPx,
                    paint
                )
            }
        }
        paint.color = Color.BLACK
        y += 12 * cellPx + 50f

        // Vertical lines / columns test
        canvas.drawText("Vertical lines test:", 60f, y, paint)
        y += 40f
        paint.strokeWidth = 2f
        for (col in 0..15) {
            val x = 60f + col * 60
            canvas.drawLine(x, y, x, y + 120f, paint)
        }
        y += 180f

        // Border box
        paint.strokeWidth = 4f
        paint.style = Paint.Style.STROKE
        canvas.drawRect(40f, 40f, (width - 40).toFloat(), (height - 40).toFloat(), paint)
        paint.style = Paint.Style.FILL

        val adapter = EscP2Adapter(dummyDriver())
        val settings = PrintSettings(quality = PrintQuality.DRAFT)
        val data = adapter.buildImageData(bitmap, settings)
        bitmap.recycle()
        return data
    }

    // ── Test 6: ESCPR Block ───────────────────────────────────────────────────────
    // Correct ESCPR protocol: exitPacketMode → REMOTE1 → ESCPR mode → dsnd per-line
    // Uses EscprProtocol + ImageToRasterConverter. THIS IS THE CORRECT PROTOCOL FOR L1455.
    private fun escprBlock(): ByteArray {
        val dpi       = 360
        val widthPx   = (210.0 / 25.4 * dpi).toInt()  // 2976
        val testLines = 100

        Log.i(TAG, "escprBlock: ${widthPx}px wide, $testLines lines, ESCPR CM.MONO")

        val rows = ImageToRasterConverter.solidBlackInkRows(widthPx, testLines)
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
        chunks += EscprProtocol.setJob(widthPx, testLines, dpi)

        chunks += EscprProtocol.startPage()
        chunks += EscprProtocol.pageNumber(1)
        for (y in 0 until testLines) chunks += EscprProtocol.sendLine(y, rows[y])
        chunks += EscprProtocol.endPage(0)

        chunks += EscprProtocol.endJob()
        chunks += EscprProtocol.printerReset()
        chunks += EscprProtocol.enterRemote1()
        chunks += EscprProtocol.loadDefaults()
        chunks += EscprProtocol.jobEnd()
        chunks += EscprProtocol.exitRemote1()

        val totalSize = chunks.sumOf { it.size }
        Log.i(TAG, "escprBlock: $totalSize bytes total")
        val result = ByteArray(totalSize)
        var offset = 0
        for (chunk in chunks) { chunk.copyInto(result, offset); offset += chunk.size }
        return result
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    private fun mmToDots(mm: Int, dpi: Int): Int = (mm.toFloat() / 25.4f * dpi).toInt()

    private fun int32LE(v: Int) = byteArrayOf(
        (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte()
    )

    private fun dummyDriver() = Driver(
        id = 0, vid = "04b8", pid = "1113",
        brand = "Epson", model = "L1455",
        protocol = PrintProtocol.ESCP2,
        color = true, duplex = false,
        maxDpi = 720, paperSizes = listOf("A4")
    )

    private operator fun MutableList<Byte>.plusAssign(bytes: ByteArray) { addAll(bytes.toList()) }
    private operator fun MutableList<Byte>.plusAssign(byte: Byte) { add(byte) }
    private fun List<Byte>.toByteArray() = ByteArray(size) { this[it] }
}
