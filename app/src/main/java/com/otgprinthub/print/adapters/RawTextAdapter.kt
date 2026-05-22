package com.otgprinthub.print.adapters

import android.graphics.Bitmap
import com.otgprinthub.domain.model.Driver
import com.otgprinthub.domain.model.PrintSettings

class RawTextAdapter(override val driver: Driver) : PrintAdapter {

    private val FF = 0x0C.toByte()
    private val LF = 0x0A.toByte()
    private val CR = 0x0D.toByte()

    override fun buildInitSequence(): ByteArray = byteArrayOf()
    override fun buildResetSequence(): ByteArray = byteArrayOf()

    override fun buildTextData(text: String, settings: PrintSettings): ByteArray {
        val result = mutableListOf<Byte>()
        text.lines().forEach { line ->
            result.addAll(line.toByteArray(Charsets.UTF_8).toList())
            result.add(CR)
            result.add(LF)
        }
        result.add(FF)
        return result.toByteArray()
    }

    override fun buildImageData(bitmap: Bitmap, settings: PrintSettings): ByteArray {
        // Raw adapter can't print images - return minimal text
        return "Image printing not supported in raw mode\n".toByteArray(Charsets.US_ASCII)
    }

    override fun buildPageBreak(): ByteArray = byteArrayOf(FF)

    override fun buildEndSequence(): ByteArray = byteArrayOf(FF)
}
