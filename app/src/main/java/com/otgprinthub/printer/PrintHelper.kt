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
 * Ties together document rendering, ESC/P-R job building, and USB transfer.
 *
 * Usage:
 *   val helper = PrintHelper(context)
 *   helper.printUri(uri, transport)
 *
 * Supported formats: PDF (first page), images (JPG/PNG/BMP), plain text.
 * Default settings: 360 DPI, A4, portrait, monochrome.
 */
class PrintHelper(private val context: Context) {

    private val TAG = "PrintHelper"

    // ── Print settings ────────────────────────────────────────────────────────

    val dpi        = 360
    val widthPx    = mmToPx(210.0, dpi)   // A4 width  = 2976 px at 360 DPI
    val heightPx   = mmToPx(297.0, dpi)   // A4 height = 4209 px at 360 DPI
    val bandLines  = 64                    // lines per ESC(e band (printer buffer safe)

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Render [uri] to bitmap, build full ESC/P-R job bytes, send over USB.
     * Call from a coroutine (IO dispatcher).
     */
    suspend fun printUri(
        uri: Uri,
        transport: UsbPrinterTransport,
        onProgress: (String) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            onProgress("Rendering document…")
            val bitmap = renderToBitmap(uri)
                ?: return@withContext Result.failure(Exception("Cannot render document"))

            onProgress("Building ESC/P-R job…")
            val jobBytes = buildEscprJob(bitmap)
            bitmap.recycle()

            onProgress("Sending ${jobBytes.size / 1024} KB to printer…")
            Log.i(TAG, "Sending ESC/P-R job: ${jobBytes.size} bytes")
            sendJob(jobBytes, transport, onProgress)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "printUri failed", e)
            Result.failure(e)
        }
    }

    /**
     * Diagnostic: print a small solid-black rectangle using ESC/P-R.
     * Use this to confirm the protocol works before trying real documents.
     * Produces ~100 solid black lines at 360 DPI (≈7mm tall).
     */
    suspend fun printTestBlock(transport: UsbPrinterTransport): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val testWidth = widthPx   // full A4 width
                val testLines = 100        // ~7mm at 360 DPI
                val rows = ImageToRasterConverter.solidBlackRows(testWidth, testLines)
                val jobBytes = buildEscprJobFromRows(rows, testWidth, testLines)
                Log.i(TAG, "TestBlock: ${jobBytes.size} bytes, ${testWidth}px wide, $testLines lines")
                sendJob(jobBytes, transport)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "printTestBlock failed", e)
                Result.failure(e)
            }
        }

    // ── Job builder ───────────────────────────────────────────────────────────

    /**
     * Build a complete ESC/P-R print job from a bitmap.
     * Converts to 1bpp monochrome, packages into bands.
     */
    fun buildEscprJob(bitmap: Bitmap): ByteArray {
        val bmp = if (bitmap.width == widthPx && bitmap.height == heightPx) bitmap
                  else scaleBitmap(bitmap)

        Log.d(TAG, "buildEscprJob: ${bmp.width}×${bmp.height} px")
        val rows = ImageToRasterConverter.toMonochromeRows(bmp)
        return buildEscprJobFromRows(rows, bmp.width, bmp.height)
    }

    private fun buildEscprJobFromRows(
        rows: List<ByteArray>,
        w: Int,
        h: Int
    ): ByteArray {
        val out = mutableListOf<Byte>()

        // ── Phase 2: Reset ────────────────────────────────────────────────────
        out += EscprProtocol.printerReset()

        // ── Phase 3: Remote mode ─────────────────────────────────────────────
        out += EscprProtocol.enterRemoteMode()
        out += EscprProtocol.timestamp()
        out += EscprProtocol.jobStart()
        out += EscprProtocol.pageParams(paperTypeCode = 0x03)   // A4
        out += EscprProtocol.dotParams(dpi)
        out += EscprProtocol.exitRemoteMode()

        // 100ms pause — printer needs time to process remote commands
        // (encoded as no-op; actual sleep happens in sendJob chunking)
        Log.d(TAG, "remote mode done, entering raster phase")

        // ── Phase 4: Raster mode ─────────────────────────────────────────────
        out += EscprProtocol.enterGraphicsMode()
        out += EscprProtocol.setUnit(dpi)
        out += EscprProtocol.setPageHeight(h)
        out += EscprProtocol.setVerticalPosition(0)

        // ── Bands ─────────────────────────────────────────────────────────────
        var line = 0
        while (line < h) {
            val count     = minOf(bandLines, h - line)
            val raster    = ImageToRasterConverter.packRows(rows, line, line + count)
            out += EscprProtocol.bandData(w, count, raster)
            line += count
        }
        Log.d(TAG, "bands written: $h lines in ${(h + bandLines - 1) / bandLines} bands")

        // ── Phase 4 end ───────────────────────────────────────────────────────
        out += EscprProtocol.formFeed()

        // ── Phase 5: Cleanup ──────────────────────────────────────────────────
        out += EscprProtocol.printerReset()

        return out.toByteArray()
    }

    // ── Document rendering ────────────────────────────────────────────────────

    private fun renderToBitmap(uri: Uri): Bitmap? {
        val mime = context.contentResolver.getType(uri) ?: inferMime(uri)
        Log.d(TAG, "renderToBitmap: mime=$mime uri=$uri")
        return when {
            mime?.contains("pdf")   == true -> renderPdf(uri)
            mime?.startsWith("image") == true -> renderImage(uri)
            mime?.startsWith("text")  == true -> renderText(uri)
            else -> {
                Log.w(TAG, "Unknown MIME, attempting image decode")
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
            toGrayscale(scaleBitmap(raw)).also { if (it !== raw) raw.recycle() }
        } catch (e: Exception) {
            Log.e(TAG, "Image render failed", e)
            null
        }
    }

    private fun renderText(uri: Uri): Bitmap? {
        return try {
            val text = openStream(uri)?.bufferedReader()?.readText() ?: return null
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
            color     = Color.BLACK
            textSize  = 28f
            typeface  = Typeface.MONOSPACE
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
        Log.i(TAG, "sendJob: $total bytes")
        transport.sendData(data).collect { result ->
            when (result) {
                is UsbPrinterTransport.TransferResult.Progress ->
                    onProgress("Sent ${result.bytesSent / 1024}/${total / 1024} KB")
                is UsbPrinterTransport.TransferResult.Error ->
                    throw Exception("USB error: ${result.message}")
                else -> {}
            }
        }
    }

    // ── Bitmap utilities ──────────────────────────────────────────────────────

    private fun scaleBitmap(src: Bitmap): Bitmap {
        if (src.width == widthPx && src.height == heightPx) return src
        val scaleX = widthPx.toFloat() / src.width
        val scaleY = heightPx.toFloat() / src.height
        val scale  = minOf(scaleX, scaleY)
        val dstW   = (src.width  * scale).toInt()
        val dstH   = (src.height * scale).toInt()
        val out    = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        out.eraseColor(Color.WHITE)
        val canvas = Canvas(out)
        val dx = (widthPx  - dstW) / 2f
        val dy = (heightPx - dstH) / 2f
        val dst = android.graphics.RectF(dx, dy, dx + dstW, dy + dstH)
        canvas.drawBitmap(src, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun toGrayscale(src: Bitmap): Bitmap {
        val out    = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint  = Paint().apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(
                android.graphics.ColorMatrix().also { it.setSaturation(0f) }
            )
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        if (out !== src) src.recycle()
        return out
    }

    // ── IO helpers ────────────────────────────────────────────────────────────

    private fun openFd(uri: Uri): ParcelFileDescriptor? = when (uri.scheme) {
        "file"    -> runCatching { ParcelFileDescriptor.open(File(uri.path!!), ParcelFileDescriptor.MODE_READ_ONLY) }.getOrNull()
        else      -> runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
    }

    private fun openStream(uri: Uri) = when (uri.scheme) {
        "file"    -> runCatching { File(uri.path!!).inputStream() }.getOrNull()
        else      -> runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
    }

    private fun inferMime(uri: Uri): String? {
        val path = uri.path ?: return null
        return when {
            path.endsWith(".pdf",  true) -> "application/pdf"
            path.endsWith(".jpg",  true) ||
            path.endsWith(".jpeg", true) -> "image/jpeg"
            path.endsWith(".png",  true) -> "image/png"
            path.endsWith(".txt",  true) -> "text/plain"
            else -> null
        }
    }

    private fun mmToPx(mm: Double, dpi: Int) = (mm / 25.4 * dpi).toInt()

    // ── Byte list helpers ─────────────────────────────────────────────────────

    private operator fun MutableList<Byte>.plusAssign(b: ByteArray) { addAll(b.toList()) }
    private fun MutableList<Byte>.toByteArray() = ByteArray(size) { this[it] }
}
