package com.otgprinthub.print.adapters

import android.graphics.Bitmap
import com.otgprinthub.domain.model.Driver
import com.otgprinthub.domain.model.PrintSettings

interface PrintAdapter {
    val driver: Driver

    fun buildInitSequence(): ByteArray
    fun buildResetSequence(): ByteArray
    fun buildTextData(text: String, settings: PrintSettings): ByteArray
    fun buildImageData(bitmap: Bitmap, settings: PrintSettings): ByteArray
    fun buildPageBreak(): ByteArray
    fun buildEndSequence(): ByteArray

    fun buildFullPrintJob(pages: List<ByteArray>): ByteArray {
        return buildInitSequence() +
                pages.flatMap { page -> page.toList() + buildPageBreak().toList() }.toByteArray() +
                buildEndSequence()
    }
}

fun ByteArray.plus(other: ByteArray): ByteArray {
    val result = ByteArray(this.size + other.size)
    System.arraycopy(this, 0, result, 0, this.size)
    System.arraycopy(other, 0, result, this.size, other.size)
    return result
}

fun List<Byte>.toByteArray(): ByteArray = ByteArray(size) { this[it] }
