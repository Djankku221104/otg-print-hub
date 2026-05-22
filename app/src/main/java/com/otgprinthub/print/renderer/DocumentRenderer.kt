package com.otgprinthub.print.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.InputStream
import com.otgprinthub.domain.model.ColorMode
import com.otgprinthub.domain.model.FitMode
import com.otgprinthub.domain.model.Orientation
import com.otgprinthub.domain.model.PaperSize
import com.otgprinthub.domain.model.PrintSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRenderer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private fun openDescriptor(uri: Uri): ParcelFileDescriptor? =
        if (uri.scheme == "file")
            runCatching { ParcelFileDescriptor.open(File(uri.path!!), ParcelFileDescriptor.MODE_READ_ONLY) }.getOrNull()
        else
            runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()

    private fun openStream(uri: Uri): InputStream? =
        if (uri.scheme == "file")
            runCatching { File(uri.path!!).inputStream() }.getOrNull()
        else
            runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()

    suspend fun renderPdf(uri: Uri, settings: PrintSettings): List<Bitmap> {
        val pages = mutableListOf<Bitmap>()
        val descriptor = openDescriptor(uri) ?: return pages

        descriptor.use { fd ->
            PdfRenderer(fd).use { renderer ->
                val targetWidth = getTargetWidth(settings)
                val targetHeight = getTargetHeight(settings)

                val pageRange = parsePageRange(settings.pageRange, renderer.pageCount)

                for (pageIndex in pageRange) {
                    if (pageIndex >= renderer.pageCount) break
                    renderer.openPage(pageIndex).use { page ->
                        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        pages.add(applyColorMode(bitmap, settings.colorMode))
                    }
                }
            }
        }
        return pages
    }

    fun renderImage(uri: Uri, settings: PrintSettings): Bitmap {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        openStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        val targetWidth = getTargetWidth(settings)
        val targetHeight = getTargetHeight(settings)

        val inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight)
        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }

        val rawBitmap = openStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return createWhiteBitmap(targetWidth, targetHeight)

        val fitBitmap = fitBitmapToPage(rawBitmap, targetWidth, targetHeight, settings.fitMode)
        rawBitmap.recycle()

        return applyColorMode(fitBitmap, settings.colorMode)
    }

    fun renderText(text: String, settings: PrintSettings): Bitmap {
        val targetWidth = getTargetWidth(settings)
        val targetHeight = if (settings.paperSize == PaperSize.THERMAL_80MM || settings.paperSize == PaperSize.THERMAL_58MM) {
            // Dynamic height for thermal
            calculateTextHeight(text, targetWidth, settings)
        } else {
            getTargetHeight(settings)
        }

        val bitmap = createWhiteBitmap(targetWidth, targetHeight)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 24f
            typeface = android.graphics.Typeface.MONOSPACE
        }

        val margin = 30f
        var y = margin + paint.textSize
        val lineHeight = paint.textSize * 1.5f

        text.lines().forEach { line ->
            canvas.drawText(line, margin, y, paint)
            y += lineHeight
        }

        return bitmap
    }

    private fun fitBitmapToPage(bitmap: Bitmap, targetWidth: Int, targetHeight: Int, fitMode: FitMode): Bitmap {
        val result = createWhiteBitmap(targetWidth, targetHeight)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val matrix = Matrix()
        when (fitMode) {
            FitMode.FIT_TO_PAGE -> {
                val scaleX = targetWidth.toFloat() / bitmap.width
                val scaleY = targetHeight.toFloat() / bitmap.height
                val scale = minOf(scaleX, scaleY)
                val dx = (targetWidth - bitmap.width * scale) / 2f
                val dy = (targetHeight - bitmap.height * scale) / 2f
                matrix.setScale(scale, scale)
                matrix.postTranslate(dx, dy)
            }
            FitMode.FILL_PAGE -> {
                val scaleX = targetWidth.toFloat() / bitmap.width
                val scaleY = targetHeight.toFloat() / bitmap.height
                val scale = maxOf(scaleX, scaleY)
                val dx = (targetWidth - bitmap.width * scale) / 2f
                val dy = (targetHeight - bitmap.height * scale) / 2f
                matrix.setScale(scale, scale)
                matrix.postTranslate(dx, dy)
            }
            FitMode.CENTER -> {
                val dx = (targetWidth - bitmap.width) / 2f
                val dy = (targetHeight - bitmap.height) / 2f
                matrix.postTranslate(dx, dy)
            }
            FitMode.ACTUAL_SIZE -> {
                // No scaling
            }
        }

        canvas.drawBitmap(bitmap, matrix, paint)
        return result
    }

    private fun applyColorMode(bitmap: Bitmap, colorMode: ColorMode): Bitmap {
        return when (colorMode) {
            ColorMode.GRAYSCALE -> convertToGrayscale(bitmap)
            ColorMode.BLACK_AND_WHITE -> convertToBlackAndWhite(bitmap)
            ColorMode.COLOR -> bitmap
        }
    }

    private fun convertToGrayscale(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.RGB_565)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(
                android.graphics.ColorMatrix().also { it.setSaturation(0f) }
            )
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun convertToBlackAndWhite(bitmap: Bitmap): Bitmap {
        val gray = convertToGrayscale(bitmap)
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.RGB_565)
        for (y in 0 until gray.height) {
            for (x in 0 until gray.width) {
                val pixel = gray.getPixel(x, y)
                result.setPixel(x, y, if (Color.red(pixel) > 128) Color.WHITE else Color.BLACK)
            }
        }
        gray.recycle()
        return result
    }

    private fun getTargetWidth(settings: PrintSettings): Int {
        val dpi = settings.quality.dpi
        return when (settings.orientation) {
            Orientation.PORTRAIT -> mmToPx(settings.paperSize.widthMm, dpi)
            Orientation.LANDSCAPE -> mmToPx(settings.paperSize.heightMm, dpi)
        }.coerceAtMost(4096)
    }

    private fun getTargetHeight(settings: PrintSettings): Int {
        val dpi = settings.quality.dpi
        return when (settings.orientation) {
            Orientation.PORTRAIT -> mmToPx(settings.paperSize.heightMm, dpi)
            Orientation.LANDSCAPE -> mmToPx(settings.paperSize.widthMm, dpi)
        }.coerceAtMost(8192)
    }

    private fun mmToPx(mm: Float, dpi: Int): Int {
        return if (mm == Float.MAX_VALUE) 4096 else (mm / 25.4f * dpi).toInt()
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun parsePageRange(pageRange: String, totalPages: Int): IntRange {
        return when {
            pageRange == "all" -> 0 until totalPages
            pageRange.contains("-") -> {
                val parts = pageRange.split("-")
                val start = (parts[0].toIntOrNull() ?: 1) - 1
                val end = (parts[1].toIntOrNull() ?: totalPages) - 1
                start..end
            }
            else -> {
                val page = (pageRange.toIntOrNull() ?: 1) - 1
                page..page
            }
        }
    }

    private fun calculateTextHeight(text: String, width: Int, settings: PrintSettings): Int {
        val lineCount = text.lines().size
        val lineHeightPx = (settings.quality.dpi / 6)  // ~6 lines per inch
        return (lineCount * lineHeightPx + settings.quality.dpi).coerceAtLeast(200)
    }

    private fun createWhiteBitmap(width: Int, height: Int): Bitmap {
        return Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565).also {
            it.eraseColor(Color.WHITE)
        }
    }
}
