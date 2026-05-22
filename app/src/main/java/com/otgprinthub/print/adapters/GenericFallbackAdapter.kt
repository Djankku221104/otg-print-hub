package com.otgprinthub.print.adapters

import android.graphics.Bitmap
import com.otgprinthub.domain.model.Driver
import com.otgprinthub.domain.model.PrintProtocol
import com.otgprinthub.domain.model.PrintSettings

class GenericFallbackAdapter(override val driver: Driver) : PrintAdapter {

    private val delegate: PrintAdapter = when (driver.protocol) {
        PrintProtocol.ESCPOS -> EscPosAdapter(driver)
        PrintProtocol.PCL5, PrintProtocol.PCL6, PrintProtocol.PCL3 -> PclAdapter(driver)
        PrintProtocol.ESCP, PrintProtocol.ESCP2 -> EscPAdapter(driver)
        PrintProtocol.POSTSCRIPT, PrintProtocol.POSTSCRIPT3 -> PostScriptAdapter(driver)
        PrintProtocol.DIRECT_PDF -> DirectPdfAdapter(driver)
        else -> RawTextAdapter(driver)
    }

    override fun buildInitSequence(): ByteArray = delegate.buildInitSequence()
    override fun buildResetSequence(): ByteArray = delegate.buildResetSequence()
    override fun buildTextData(text: String, settings: PrintSettings): ByteArray = delegate.buildTextData(text, settings)
    override fun buildImageData(bitmap: Bitmap, settings: PrintSettings): ByteArray = delegate.buildImageData(bitmap, settings)
    override fun buildPageBreak(): ByteArray = delegate.buildPageBreak()
    override fun buildEndSequence(): ByteArray = delegate.buildEndSequence()
}
