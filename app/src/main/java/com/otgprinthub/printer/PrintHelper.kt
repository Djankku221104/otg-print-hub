package com.otgprinthub.printer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.otgprinthub.usb.UsbPrinterTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Ties together document rendering, ESCPR job building, and USB transfer.
 *
 * Protocol: ESC/P-R (ESCPR) — confirmed from python-epson + epson-inkjet-printer-escpr.
 * Pixel format: CM.MONOCHROME, 1 byte/pixel (0x00=white, 0xFF=black), per-line dsnd.
 *
 * For debugging, filter logcat: adb logcat -s ESCPR PrintHelper RasterConv
 */
class PrintHelper(private val context: Context) {

    private val TAG = "PrintHelper"

    val dpi      = 360
    val widthPx  = mmToPx(210.0, dpi)   // A4 = 2976 px at 360 DPI
    val heightPx = mmToPx(297.0, dpi)   // A4 = 4209 px at 360 DPI

    // ── Public API ────────────────────────────────────────────────────────────

    suspend fun printUri(
        uri: Uri,
        transport: UsbPrinterTransport,
        onProgress: (String) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "═══ printUri START ═══ ${widthPx}x${heightPx}px @${dpi}DPI")
            onProgress("Rendering document…")
            val bitmap = renderToBitmap(uri)
                ?: return@withContext Result.failure(Exception("Cannot render document"))

            Log.i(TAG, "Rendered bitmap: ${bitmap.width}x${bitmap.height}px")
            onProgress("Converting to ink data…")
            val rows = ImageToRasterConverter.toInkRows(bitmap)
            bitmap.recycle()
            Log.i(TAG, "Ink rows: ${rows.size} rows x ${rows.firstOrNull()?.size ?: 0} bytes")

            onProgress("Building ESCPR job…")
            val jobBytes = buildEscprJob(rows, widthPx, heightPx)
            Log.i(TAG, "Job built: ${jobBytes.size} bytes (${jobBytes.size / 1024} KB)")

            onProgress("Sending ${jobBytes.size / 1024} KB…")
            sendJob(jobBytes, transport, onProgress)

            Log.i(TAG, "═══ printUri DONE ═══")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "═══ printUri FAILED: ${e.message} ═══", e)
            Result.failure(e)
        }
    }

    /**
     * Diagnostic: 100 solid-black lines using ESCPR protocol.
     * Run this first to verify protocol before trying real documents.
     */
    suspend fun printTestBlock(transport: UsbPrinterTransport): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val testLines = 100
                Log.i(TAG, "═══ printTestBlock START ═══ ${widthPx}px x $testLines lines")
                val rows = ImageToRasterConverter.solidBlackInkRows(widthPx, testLines)
                val jobBytes = buildEscprJob(rows, widthPx, testLines)
                Log.i(TAG, "Test job: ${jobBytes.size} bytes")
                sendJob(jobBytes, transport)
                Log.i(TAG, "═══ printTestBlock DONE ═══")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "printTestBlock FAILED", e)
                Result.failure(e)
            }
        }

    // ── Job builder ───────────────────────────────────────────────────────────

    fun buildEscprJob(bitmap: Bitmap): ByteArray {
        val bmp = if (bitmap.width == widthPx && bitmap.height == heightPx) bitmap
                  else scaleBitmap(bitmap)
        val rows = ImageToRasterConverter.toInkRows(bmp)
        if (bmp !== bitmap) bmp.recycle()
        return buildEscprJob(rows, bmp.width, bmp.height)
    }

    /**
     * Build complete ESCPR job from ink rows.
     *
     * Sequence (per python-epson Job._start / print_pages / _end):
     *   exitPacketMode + printerReset
     *   REMOTE1: TI + JS + PP → exit
     *   enterEscprMode + setQuality(MONO) + setJob
     *   startPage + pageNumber(1) + [sendLine×H] + endPage(0)
     *   endJob
     *   printerReset + REMOTE1: LD + JE → exit
     */
    fun buildEscprJob(rows: List<ByteArray>, w: Int, h: Int): ByteArray {
        val chunks = mutableListOf<ByteArray>()

        chunks += EscprProtocol.exitPacketMode()
        Log.d(TAG, "[1] exitPacketMode")

        chunks += EscprProtocol.printerReset()
        Log.d(TAG, "[2] printerReset")

        chunks += EscprProtocol.enterRemote1()
        chunks += EscprProtocol.timestamp()
        chunks += EscprProtocol.jobStart()
        chunks += EscprProtocol.paperPath()
        chunks += EscprProtocol.exitRemote1()
        Log.d(TAG, "[3] REMOTE1 done")

        chunks += EscprProtocol.enterEscprMode()
        chunks += EscprProtocol.setQuality(mtid = 0, mqid = 1, cm = 1)
        chunks += EscprProtocol.setJob(w, h, dpi)
        Log.d(TAG, "[4] ESCPR mode, setq+setj for ${w}x${h}px")

        chunks += EscprProtocol.startPage()
        chunks += EscprProtocol.pageNumber(1)
        Log.i(TAG, "[5] Encoding $h lines @ $w bytes/line…")

        for (y in 0 until h) {
            chunks += EscprProtocol.sendLine(y, rows[y])
            if (y % 500 == 0) Log.d(TAG, "    line $y/$h")
        }
        Log.i(TAG, "[5] All $h lines done")

        chunks += EscprProtocol.endPage(0)
        chunks += EscprProtocol.endJob()
        Log.d(TAG, "[6] endPage + endJob")

        chunks += EscprProtocol.printerReset()
        chunks += EscprProtocol.enterRemote1()
        chunks += EscprProtocol.loadDefaults()
        chunks += EscprProtocol.jobEnd()
        chunks += EscprProtocol.exitRemote1()
        Log.d(TAG, "[7] cleanup done")

        val totalSize = chunks.sumOf { it.size }
        Log.i(TAG, "Total job size: $totalSize bytes (${totalSize / 1024} KB)")

        val result = ByteArray(totalSize)
        var offset = 0
        for (chunk in chunks) { chunk.copyInto(result, offset); offset += chunk.size }
        return result
    }

    // ── Document rendering ────────────────────────────────────────────────────

    private fun renderToBitmap(uri: Uri): Bitmap? {
        val mime = context.contentResolver.getType(uri) ?: inferMime(uri)
        Log.d(TAG, "renderToBitmap: mime=$mime uri=$uri")
        return when {
            mime?.contains("pdf")     == true -> renderPdf(uri)
            mime?.startsWith("image") == true -> renderImage(uri)
            mime?.startsWith("text")  == true -> renderText(uri)
            else -> {
                Log.w(TAG, "Unknown MIME, trying image then text")
                renderImage(uri) ?: renderText(uri)
            }
        }
    }

    private fun renderPdf(uri: Uri): Bitmap? {
        val fd = openFd(uri) ?: return null
        return try {
            PdfRenderer(fd).use { renderer ->
                if (renderer.pageCount == 0) return null
                renderer.openPage(0).use { page ->
                    Log.d(TAG, "PDF page ${page.width}x${page.height}pt → ${widthPx}x${heightPx}px")
                    val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    toGrayscale(bmp)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "PDF render failed", e)
            null
        }
    }

    private fun renderImage(uri: Uri): Bitmap? {
        return try {
            val stream = openStream(uri) ?: return null
            val raw = BitmapFactory.decodeStream(stream)
            stream.close()
            raw ?: return null
            Log.d(TAG, "Image: ${raw.width}x${raw.height}")
            toGrayscale(scaleBitmap(raw)).also { if (it !== raw) raw.recycle() }
        } catch (e: Exception) {
            Log.e(TAG, "Image render failed", e)
            null
        }
    }

    private fun renderText(uri: Uri): Bitmap? {
        return try {
            val text = openStream(uri)?.bufferedReader()?.readText() ?: return null
            Log.d(TAG, "Text: ${text.length} chars, ${text.lines().size} lines")
            renderTextToBitmap(text)
        } catch (e: Exception) {
            Log.e(TAG, "Text render failed", e)
            null
        }
    }

    private fun renderTextToBitmap(text: String): Bitmap {
        val bmp    = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        val canvas = Canvas(bmp)
        val paint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color    = Color.BLACK
            textSize = 28f
            typeface = Typeface.MONOSPACE
        }
        val margin     = 60f
        val lineHeight = paint.textSize * 1.4f
        var y          = margin + paint.textSize
        text.lines().forEach { line ->
            if (y + lineHeight > heightPx - margin) return@forEach
            canvas.drawText(line.take(120), margin, y, paint)
            y += lineHeight
        }
        return bmp
    }

    // ── USB send ──────────────────────────────────────────────────────────────

    private suspend fun sendJob(
        data: ByteArray,
        transport: UsbPrinterTransport,
        onProgress: (String) -> Unit = {}
    ) {
        val total = data.size
        Log.i(TAG, "sendJob: sending $total bytes via USB")
        transport.sendData(data).collect { result ->
            when (result) {
                is UsbPrinterTransport.TransferResult.Progress -> {
                    val pct = (result.bytesSent * 100 / total).toInt()
                    Log.d(TAG, "USB progress: ${result.bytesSent}/$total ($pct%)")
                    onProgress("Sent ${result.bytesSent / 1024}/${total / 1024} KB ($pct%)")
                }
                is UsbPrinterTransport.TransferResult.Error -> {
                    Log.e(TAG, "USB transfer error: ${result.message}")
                    throw Exception("USB error: ${result.message}")
                }
                is UsbPrinterTransport.TransferResult.Complete ->
                    Log.i(TAG, "USB transfer complete")
                else -> {}
            }
        }
    }

    // ── Bitmap utilities ──────────────────────────────────────────────────────

    private fun scaleBitmap(src: Bitmap): Bitmap {
        if (src.width == widthPx && src.height == heightPx) return src
        val scale = minOf(widthPx.toFloat() / src.width, heightPx.toFloat() / src.height)
        val dstW  = (src.width  * scale).toInt()
        val dstH  = (src.height * scale).toInt()
        val out   = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        out.eraseColor(Color.WHITE)
        val dx = (widthPx  - dstW) / 2f
        val dy = (heightPx - dstH) / 2f
        Canvas(out).drawBitmap(src, null, android.graphics.RectF(dx, dy, dx + dstW, dy + dstH), Paint(Paint.FILTER_BITMAP_FLAG))
        Log.d(TAG, "scaleBitmap: ${src.width}x${src.height} → ${dstW}x${dstH}")
        return out
    }

    private fun toGrayscale(src: Bitmap): Bitmap {
        val out    = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(src, 0f, 0f, Paint().apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(
                android.graphics.ColorMatrix().also { it.setSaturation(0f) }
            )
        })
        if (out !== src) src.recycle()
        return out
    }

    // ── IO helpers ────────────────────────────────────────────────────────────

    private fun openFd(uri: Uri): ParcelFileDescriptor? = when (uri.scheme) {
        "file" -> runCatching { ParcelFileDescriptor.open(File(uri.path!!), ParcelFileDescriptor.MODE_READ_ONLY) }.getOrNull()
        else   -> runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
    }

    private fun openStream(uri: Uri) = when (uri.scheme) {
        "file" -> runCatching { File(uri.path!!).inputStream() }.getOrNull()
        else   -> runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
    }

    private fun inferMime(uri: Uri): String? {
        val path = uri.path ?: return null
        return when {
            path.endsWith(".pdf",  true) -> "application/pdf"
            path.endsWith(".jpg",  true) || path.endsWith(".jpeg", true) -> "image/jpeg"
            path.endsWith(".png",  true) -> "image/png"
            path.endsWith(".txt",  true) -> "text/plain"
            else -> null
        }
    }

    private fun mmToPx(mm: Double, dpi: Int) = (mm / 25.4 * dpi).toInt()

    private operator fun MutableList<ByteArray>.plusAssign(b: ByteArray) { add(b) }
}
