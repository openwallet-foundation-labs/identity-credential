package com.ul.ims.gmdl.appholder.util

object FormatUtil {
    // Helper function to convert a byteArray to HEX string
    fun encodeToString(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }

        return sb.toString()
    }
}