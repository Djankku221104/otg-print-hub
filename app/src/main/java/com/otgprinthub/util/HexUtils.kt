package com.otgprinthub.util

object HexUtils {
    fun hexToBytes(hex: String): ByteArray {
        val clean = hex.replace(" ", "").replace("-", "")
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02X".format(it) }

    fun bytesToHexSpaced(bytes: ByteArray): String =
        bytes.joinToString(" ") { "%02X".format(it) }

    fun intToHex(value: Int, digits: Int = 4): String =
        "%0${digits}X".format(value)
}
