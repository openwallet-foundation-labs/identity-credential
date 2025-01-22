package com.android.identity.nfc

import com.android.identity.util.fromHex
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.ByteStringBuilder
import kotlinx.io.bytestring.append

/**
 * A Command APDU according to ISO/IEC 7816.
 *
 * @property cla Command class byte.
 * @property ins Instruction byte.
 * @property p1 Parameter byte 1.
 * @property p2 Parameter byte 2.
 * @property payload Payload.
 * @property le Maximum length of response data field.
 */
data class CommandApdu(
    val cla: Int,
    val ins: Int,
    val p1: Int,
    val p2: Int,
    val payload: ByteString,
    val le: Int
) {
    /**
     * Encodes the APDU as bytes.
     *
     * @return the bytes of the APDU.
     */
    fun encode(): ByteArray {
        check(cla >= 0 && cla < 0x100)
        check(ins >= 0 && ins < 0x100)
        check(p1 >= 0 && p1 < 0x100)
        check(p2 >= 0 && p2 < 0x100)
        check(payload.size < 0x10000)

        val bsb = ByteStringBuilder()
        bsb.append(cla.toByte())
        bsb.append(ins.toByte())
        bsb.append(p1.toByte())
        bsb.append(p2.toByte())
        var lcPresent = false

        // Lc and Le must use the same encoding, either short or long
        val useShortEncoding = payload.size < 0x100 && le <= 0x100

        if (payload.size > 0) {
            lcPresent = true
            if (useShortEncoding) {
                bsb.append(payload.size.toByte())
            } else {
                bsb.append(0x00)
                bsb.append((payload.size shr 8).toByte())
                bsb.append(payload.size.toByte())
            }
            bsb.append(payload)
        }
        if (le > 0) {
            if (useShortEncoding) {
                if (le == 0x100) {
                    bsb.append(0x00)
                } else {
                    bsb.append(le.toByte())
                }
            } else {
                if (!lcPresent) {
                    bsb.append(0x00)
                }
                if (le < 0x10000) {
                    bsb.append((le shr 8).toByte())
                    bsb.append(le.toByte())
                } else if (le == 0x10000) {
                    bsb.append(0x00)
                    bsb.append(0x00)
                } else {
                    throw IllegalStateException("invalid LE size $le")
                }
            }
        }
        return bsb.toByteString().toByteArray()
    }

    companion object {
        /**
         * Decodes an APDU.
         *
         * @param encoded the bytes of the APDU
         * @return an object with the decoded fields.
         */
        @OptIn(ExperimentalStdlibApi::class)
        fun decode(encoded: ByteArray): CommandApdu {
            require(encoded.size >= 4)
            val cla = encoded[0].toInt().and(0xff)
            val ins = encoded[1].toInt().and(0xff)
            val p1 = encoded[2].toInt().and(0xff)
            val p2 = encoded[3].toInt().and(0xff)
            var payload = ByteString(byteArrayOf())
            var lc = 0
            var le = 0
            if (encoded.size == 5) {
                val encLe = encoded[4].toInt().and(0xff)
                le = if (encLe == 0x00) 0x100 else encLe
            } else if (encoded.size > 5) {
                lc = encoded[4].toInt().and(0xff)
                var lcEndsAt = 5
                if (lc == 0) {
                    lc = encoded[5].toInt().and(0xff).shl(8) + encoded[6].toInt().and(0xff)
                    lcEndsAt = 7
                }
                if (lc > 0 && lcEndsAt + lc <= encoded.size) {
                    val payloadArray = ByteArray(lc)
                    encoded.copyInto(payloadArray, 0, lcEndsAt, lcEndsAt + lc)
                    payload = ByteString(payloadArray)
                } else {
                    lc = 0
                    lcEndsAt = 4
                }
                val leLen = encoded.size - lcEndsAt - lc
                le = when (leLen) {
                    0 -> 0
                    1 -> {
                        val encLe = encoded[encoded.size - 1].toInt().and(0xff)
                        if (encLe == 0x00) 0x100 else encLe
                    }
                    2, 3 -> {
                        val encLe = encoded[encoded.size - 2].toInt().and(0xff).shl(8) +
                                encoded[encoded.size - 1].toInt().and(0xff)
                        if (encLe == 0x00) 0x10000 else encLe
                    }
                    else -> throw IllegalStateException("Invalid LE len $leLen")
                }
            }
            return CommandApdu(
                cla = cla,
                ins = ins,
                p1 = p1,
                p2 = p2,
                payload = payload,
                le = le
            )
        }
    }
}