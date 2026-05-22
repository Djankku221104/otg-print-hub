package com.otgprinthub.print.adapters

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.otgprinthub.domain.model.Driver
import com.otgprinthub.domain.model.PrintSettings
import dagger.hilt.android.qualifiers.ApplicationContext

class DirectPdfAdapter(
    override val driver: Driver,
    private val context: Context? = null
) : PrintAdapter {

    override fun buildInitSequence(): ByteArray = byteArrayOf()
    override fun buildResetSequence(): ByteArray = byteArrayOf()

    override fun buildTextData(text: String, settings: PrintSettings): ByteArray {
        // Wrap in minimal PDF structure
        return buildMinimalPdf(text)
    }

    override fun buildImageData(bitmap: Bitmap, settings: PrintSettings): ByteArray {
        // For images with DirectPDF, the engine should convert to PDF first
        return byteArrayOf()
    }

    override fun buildPageBreak(): ByteArray = byteArrayOf()

    override fun buildEndSequence(): ByteArray = byteArrayOf()

    fun readPdfBytes(fileUri: Uri): ByteArray? {
        return try {
            context?.contentResolver?.openInputStream(fileUri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }

    private fun buildMinimalPdf(text: String): ByteArray {
        // Creates a minimal valid PDF with the given text
        val contentStream = "BT\n/F1 12 Tf\n72 720 Td\n(${text.replace("(","\\(").replace(")","\\)")}) Tj\nET"
        val sb = StringBuilder()
        sb.appendLine("%PDF-1.4")
        sb.appendLine("1 0 obj")
        sb.appendLine("<< /Type /Catalog /Pages 2 0 R >>")
        sb.appendLine("endobj")
        sb.appendLine("2 0 obj")
        sb.appendLine("<< /Type /Pages /Kids [3 0 R] /Count 1 >>")
        sb.appendLine("endobj")
        sb.appendLine("3 0 obj")
        sb.appendLine("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>")
        sb.appendLine("endobj")
        sb.appendLine("4 0 obj")
        sb.appendLine("<< /Length ${contentStream.length} >>")
        sb.appendLine("stream")
        sb.appendLine(contentStream)
        sb.appendLine("endstream")
        sb.appendLine("endobj")
        sb.appendLine("5 0 obj")
        sb.appendLine("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")
        sb.appendLine("endobj")
        sb.appendLine("xref")
        sb.appendLine("0 6")
        sb.appendLine("%%EOF")
        return sb.toString().toByteArray(Charsets.US_ASCII)
    }
}
