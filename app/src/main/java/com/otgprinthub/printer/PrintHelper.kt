package com.otgprinthub.printer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.otgprinthub.domain.model.ColorMode
import com.otgprinthub.domain.model.FitMode
import com.otgprinthub.domain.model.Orientation
import com.otgprinthub.domain.model.PaperSize
import com.otgprinthub.domain.model.PrintQuality
import com.otgprinthub.domain.model.PrintSettings
import com.otgprinthub.usb.UsbPrinterTransport
import com.otgprinthub.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PrintHelper(private val context: Context) {

    private val TAG = "PrintHelper"

    // ── Public API ────────────────────────────────────────────────────────────

    suspend fun renderPreviewBitmap(uri: Uri, settings: PrintSettings): android.graphics.Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val dpi   = 72
                val paperW = paperWidthPx(settings.paperSize, settings.orientation, dpi)
                val paperH = paperHeightPx(settings.paperSize, settings.orientation, dpi)
                AppLogger.i(TAG, "renderPreviewBitmap: ${paperW}x${paperH}px @${dpi}DPI")
                renderToBitmap(uri, paperW, paperH, settings)
            } catch (e: Exception) {
                AppLogger.e(TAG, "renderPreviewBitmap failed: ${e.message}")
                null
            }
        }

    suspend fun printUri(
        uri: Uri,
        transport: UsbPrinterTransport,
        settings: PrintSettings = PrintSettings(),
        onProgress: (String) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dpi     = escprDpi(settings.quality)
            val paperW  = paperWidthPx(settings.paperSize, settings.orientation, dpi)
            val paperH  = paperHeightPx(settings.paperSize, settings.orientation, dpi)
            val isColor = settings.colorMode == ColorMode.COLOR
            val copies  = settings.copies.coerceIn(1, 99)

            AppLogger.separator("printUri")
            AppLogger.i(TAG, "Settings: size=${settings.paperSize.name} orient=${settings.orientation.name} color=${settings.colorMode.name} quality=${settings.quality.name} fit=${settings.fitMode.name} copies=$copies")
            AppLogger.i(TAG, "Computed: paperW=${paperW}px paperH=${paperH}px @${dpi}DPI cm=0(always)")
            Log.i(TAG, "═══ printUri START ═══ ${paperW}x${paperH}px @${dpi}DPI color=$isColor copies=$copies")

            onProgress("Rendering document…")
            val bitmap = renderToBitmap(uri, paperW, paperH, settings)
                ?: return@withContext Result.failure(Exception("Cannot render document"))

            Log.i(TAG, "Rendered bitmap: ${bitmap.width}x${bitmap.height}px")
            onProgress("Converting to ink data…")

            // Always use cm=0 (COLOR, 3 bytes/pixel RGB).
            // For GRAYSCALE/B&W: renderToBitmap already calls toGrayscale() so each pixel
            // has R=G=B, giving correct grayscale output. cm=1 (MONO) fills only ~1/3 of the
            // page width on L1455 — do NOT use it.
            val rows = ImageToRasterConverter.toColorInkRows(bitmap)
            bitmap.recycle()
            AppLogger.i(TAG, "Ink rows: ${rows.size} rows x ${rows.firstOrNull()?.size ?: 0} bytes/row")

            onProgress("Building ESCPR job…")
            val jobBytes = buildEscprJob(rows, paperW, paperH, dpi, settings)
            AppLogger.i(TAG, "Job built: ${jobBytes.size / 1024} KB")

            onProgress("Sending ${jobBytes.size / 1024} KB…")
            sendJob(jobBytes, transport, onProgress)

            AppLogger.i(TAG, "═══ printUri DONE ═══")
            Log.i(TAG, "═══ printUri DONE ═══")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "printUri FAILED: ${e.message}")
            Log.e(TAG, "═══ printUri FAILED: ${e.message} ═══", e)
            Result.failure(e)
        }
    }

    // ── Job builder ───────────────────────────────────────────────────────────

    fun buildEscprJob(
        rows: List<ByteArray>,
        w: Int,
        h: Int,
        dpi: Int = 360,
        settings: PrintSettings = PrintSettings()
    ): ByteArray {
        val isColor = settings.colorMode == ColorMode.COLOR
        val cm      = 0  // always COLOR encoding — cm=1 (MONO) fills only ~1/3 of page on L1455
        val mqid    = when (settings.quality) {
            PrintQuality.DRAFT  -> 0
            PrintQuality.NORMAL -> 1
            PrintQuality.HIGH, PrintQuality.BEST -> 2
        }
        val copies  = settings.copies.coerceIn(1, 99)
        val chunks  = mutableListOf<ByteArray>()

        // ── Init ──────────────────────────────────────────────────────────────
        chunks += EscprProtocol.exitPacketMode()
        chunks += EscprProtocol.printerReset()
        chunks += EscprProtocol.enterRemote1()
        chunks += EscprProtocol.timestamp()
        chunks += EscprProtocol.jobStart()
        chunks += EscprProtocol.paperPath()
        chunks += EscprProtocol.exitRemote1()

        chunks += EscprProtocol.enterEscprMode()
        chunks += EscprProtocol.setQuality(mtid = 0, mqid = mqid, cm = cm)
        chunks += EscprProtocol.setJob(w, h, dpi)
        AppLogger.i(TAG, "setq: mqid=$mqid cm=COLOR(cm=0,${if (isColor) "rgb" else "gray"}) | setj: ${w}x${h}@${dpi}DPI")

        // ── Pages (copies) — startPage per copy matches python-epson sequence ─
        for (copy in 0 until copies) {
            chunks += EscprProtocol.startPage()
            chunks += EscprProtocol.pageNumber(copy + 1)
            for (y in 0 until h) {
                chunks += EscprProtocol.sendLine(y, rows[y])
                if (y % 1000 == 0) Log.d(TAG, "    line $y/$h (copy ${copy + 1}/$copies)")
            }
            val pagesLeft = copies - 1 - copy
            chunks += EscprProtocol.endPage(pagesLeft)
            Log.d(TAG, "endPage: pagesLeft=$pagesLeft")
        }

        // ── Cleanup: endJob exits ESCPR mode. No printerReset/REMOTE1 — they
        //    cause the printer to eject a blank page after the content page. ──
        chunks += EscprProtocol.endJob()

        val totalSize = chunks.sumOf { it.size }
        AppLogger.i(TAG, "Total job size: $totalSize bytes (${totalSize / 1024} KB)")

        val result = ByteArray(totalSize)
        var offset = 0
        for (chunk in chunks) { chunk.copyInto(result, offset); offset += chunk.size }
        return result
    }

    // ── Document rendering ────────────────────────────────────────────────────

    private fun renderToBitmap(uri: Uri, w: Int, h: Int, settings: PrintSettings): Bitmap? {
        val mime = context.contentResolver.getType(uri) ?: inferMime(uri)
        val isColor = settings.colorMode == ColorMode.COLOR
        AppLogger.i(TAG, "renderToBitmap: scheme=${uri.scheme} mime=$mime color=$isColor")
        val result = when {
            mime?.contains("pdf")     == true -> renderPdf(uri, w, h, settings)
            mime?.startsWith("image") == true -> renderImage(uri, w, h, settings)
            mime?.startsWith("text")  == true -> renderText(uri, w, h)
            else -> {
                AppLogger.w(TAG, "Unknown MIME '$mime', trying image then text")
                renderImage(uri, w, h, settings) ?: renderText(uri, w, h)
            }
        }
        if (result == null) AppLogger.e(TAG, "renderToBitmap NULL — mime=$mime uri=$uri")
        return result
    }

    private fun renderPdf(uri: Uri, w: Int, h: Int, settings: PrintSettings): Bitmap? {
        val fd = openFd(uri)
        if (fd == null) {
            AppLogger.e(TAG, "renderPdf: openFd null for ${uri.scheme}")
            return null
        }
        return try {
            PdfRenderer(fd).use { renderer ->
                if (renderer.pageCount == 0) { AppLogger.e(TAG, "renderPdf: 0 pages"); return null }
                renderer.openPage(0).use { page ->
                    AppLogger.i(TAG, "PDF page: ${page.width}x${page.height}pt, target ${w}x${h}px")

                    // Calculate destination rect that fits the PDF page within the paper bitmap
                    val dstRect = fitRect(page.width.toFloat(), page.height.toFloat(), w, h, settings)

                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, dstRect, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                    if (settings.colorMode == ColorMode.COLOR) bmp else toGrayscale(bmp)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "renderPdf exception: ${e.message}")
            null
        }
    }

    private fun renderImage(uri: Uri, w: Int, h: Int, settings: PrintSettings): Bitmap? {
        return try {
            val stream = openStream(uri)
            if (stream == null) { AppLogger.e(TAG, "renderImage: openStream null"); return null }
            val raw = BitmapFactory.decodeStream(stream)
            stream.close()
            if (raw == null) { AppLogger.e(TAG, "renderImage: BitmapFactory null"); return null }
            AppLogger.i(TAG, "Image decoded: ${raw.width}x${raw.height}")

            val scaled = scaleBitmap(raw, w, h, settings)
            if (scaled !== raw) raw.recycle()

            if (settings.colorMode == ColorMode.COLOR) scaled else toGrayscale(scaled)
        } catch (e: Exception) {
            AppLogger.e(TAG, "renderImage exception: ${e.message}")
            null
        }
    }

    private fun renderText(uri: Uri, w: Int, h: Int): Bitmap? {
        return try {
            val stream = openStream(uri)
            if (stream == null) { AppLogger.e(TAG, "renderText: openStream null"); return null }
            val text = stream.bufferedReader().readText()
            AppLogger.i(TAG, "Text: ${text.length} chars, ${text.lines().size} lines")
            renderTextToBitmap(text, w, h)
        } catch (e: Exception) {
            AppLogger.e(TAG, "renderText exception: ${e.message}")
            null
        }
    }

    private fun renderTextToBitmap(text: String, w: Int, h: Int): Bitmap {
        val bmp    = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        val canvas = Canvas(bmp)
        val marginPx = (w * 0.05f)          // 5% margin on each side
        val availW   = w - 2 * marginPx
        val paint    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color    = Color.BLACK
            textSize = (w / 80f).coerceIn(20f, 40f)   // scale text to paper width
            typeface = Typeface.MONOSPACE
        }
        val charsPerLine = (availW / (paint.textSize * 0.6f)).toInt().coerceAtLeast(1)
        val lineHeight   = paint.textSize * 1.5f
        var y            = marginPx + paint.textSize

        text.lines().forEach { line ->
            if (y + lineHeight > h - marginPx) return@forEach
            // Wrap long lines
            var remaining = line
            while (remaining.isNotEmpty() && y + lineHeight <= h - marginPx) {
                val chunk = remaining.take(charsPerLine)
                canvas.drawText(chunk, marginPx, y, paint)
                remaining = remaining.drop(charsPerLine)
                y += lineHeight
            }
        }
        return bmp
    }

    // ── Bitmap scaling ────────────────────────────────────────────────────────

    private fun scaleBitmap(src: Bitmap, w: Int, h: Int, settings: PrintSettings): Bitmap {
        if (src.width == w && src.height == h) return src
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.eraseColor(Color.WHITE)
        val dstRect = fitRect(src.width.toFloat(), src.height.toFloat(), w, h, settings)
        Canvas(out).drawBitmap(src, null, RectF(dstRect), Paint(Paint.FILTER_BITMAP_FLAG))
        AppLogger.i(TAG, "scaled: ${src.width}x${src.height} → ${dstRect.width()}x${dstRect.height()} in ${w}x${h}")
        return out
    }

    /**
     * Compute destination Rect that places content within [targetW x targetH]
     * according to FitMode (fit/fill/center/actual-size).
     */
    private fun fitRect(srcW: Float, srcH: Float, targetW: Int, targetH: Int, settings: PrintSettings): Rect {
        val scale = when (settings.fitMode) {
            FitMode.FIT_TO_PAGE  -> minOf(targetW / srcW, targetH / srcH)
            FitMode.FILL_PAGE    -> maxOf(targetW / srcW, targetH / srcH)
            FitMode.ACTUAL_SIZE  -> 1f
            FitMode.CENTER       -> minOf(1f, minOf(targetW / srcW, targetH / srcH))
        }
        val dstW = (srcW * scale).toInt().coerceAtMost(targetW)
        val dstH = (srcH * scale).toInt().coerceAtMost(targetH)
        val dx   = (targetW - dstW) / 2
        val dy   = (targetH - dstH) / 2
        return Rect(dx, dy, dx + dstW, dy + dstH)
    }

    private fun toGrayscale(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, 0f, 0f, Paint().apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(
                android.graphics.ColorMatrix().also { it.setSaturation(0f) }
            )
        })
        if (out !== src) src.recycle()
        return out
    }

    // ── USB send ──────────────────────────────────────────────────────────────

    private suspend fun sendJob(
        data: ByteArray,
        transport: UsbPrinterTransport,
        onProgress: (String) -> Unit = {}
    ) {
        val total = data.size
        transport.sendData(data).collect { result ->
            when (result) {
                is UsbPrinterTransport.TransferResult.Progress -> {
                    val pct = (result.bytesSent * 100 / total).toInt()
                    onProgress("Sent ${result.bytesSent / 1024}/${total / 1024} KB ($pct%)")
                }
                is UsbPrinterTransport.TransferResult.Error -> {
                    AppLogger.e(TAG, "USB ERROR: ${result.message}")
                    throw Exception("USB error: ${result.message}")
                }
                is UsbPrinterTransport.TransferResult.Complete -> {
                    AppLogger.i(TAG, "USB transfer COMPLETE")
                }
                else -> {}
            }
        }
    }

    // ── Paper size helpers ────────────────────────────────────────────────────

    private fun escprDpi(quality: PrintQuality): Int = when (quality) {
        PrintQuality.HIGH, PrintQuality.BEST -> 360   // keep 360 — 720 DPI bitmaps are ~200MB
        else -> 360
    }

    private fun paperWidthPx(size: PaperSize, orientation: Orientation, dpi: Int): Int {
        val wMm = if (orientation == Orientation.LANDSCAPE) size.heightMm else size.widthMm
        return mmToPx(wMm.toDouble(), dpi)
    }

    private fun paperHeightPx(size: PaperSize, orientation: Orientation, dpi: Int): Int {
        val hMm = if (orientation == Orientation.LANDSCAPE) size.widthMm else size.heightMm
        val mm  = if (hMm == Float.MAX_VALUE) 297.0 else hMm.toDouble()
        return mmToPx(mm, dpi)
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
