package com.android.mdl.app.util

import android.icu.text.SimpleDateFormat
import android.icu.util.TimeZone
import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.CborException
import co.nstant.`in`.cbor.model.DataItem
import java.io.ByteArrayOutputStream
import java.security.PublicKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECPoint
import kotlin.math.min


object FormatUtil {
    // Helper function to convert a byteArray to HEX string
    fun encodeToString(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }

        return sb.toString()
    }

    private const val CHUNK_SIZE = 2048

    private fun debugPrint(message: String) {
        var index = 0
        while (index < message.length) {
            log(message.substring(index, min(message.length, index + CHUNK_SIZE)))
            index += CHUNK_SIZE
        }
    }

    fun debugPrintEncodeToString(bytes: ByteArray) {
        debugPrint(encodeToString(bytes))
    }

    private const val COSE_KEY_KTY = 1
    private const val COSE_KEY_TYPE_EC2 = 2
    private const val COSE_KEY_EC2_CRV = -1
    private const val COSE_KEY_EC2_X = -2
    private const val COSE_KEY_EC2_Y = -3
    private const val COSE_KEY_EC2_CRV_P256 = 1

    fun cborBuildCoseKey(key: PublicKey): DataItem {
        val ecKey: ECPublicKey = key as ECPublicKey
        val w: ECPoint = ecKey.w
        // X and Y are always positive so for interop we remove any leading zeroes
        // inserted by the BigInteger encoder.
        val x = stripLeadingZeroes(w.affineX.toByteArray())
        val y = stripLeadingZeroes(w.affineY.toByteArray())
        return CborBuilder()
            .addMap()
            .put(COSE_KEY_KTY.toLong(), COSE_KEY_TYPE_EC2.toLong())
            .put(COSE_KEY_EC2_CRV.toLong(), COSE_KEY_EC2_CRV_P256.toLong())
            .put(COSE_KEY_EC2_X.toLong(), x)
            .put(COSE_KEY_EC2_Y.toLong(), y)
            .end()
            .build()[0]
    }

    fun cborEncode(dataItem: DataItem): ByteArray {
        val baos = ByteArrayOutputStream()
        try {
            CborEncoder(baos).encode(dataItem)
        } catch (e: CborException) {
            // This should never happen and we don't want cborEncode() to throw since that
            // would complicate all callers. Log it instead.
            throw IllegalStateException("Unexpected failure encoding data", e)
        }
        return baos.toByteArray()
    }

    private fun stripLeadingZeroes(value: ByteArray): ByteArray {
        var n = 0
        while (n < value.size && value[n] == 0x00.toByte()) {
            n++
        }
        return value.copyOfRange(n, value.size)
    }

    fun fullDateStringToMilliseconds(date: String): Long {
        val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd")
        simpleDateFormat.timeZone = TimeZone.getTimeZone("UTC")
        return simpleDateFormat.parse(date).toInstant().toEpochMilli()
    }

    fun millisecondsToFullDateString(milliseconds: Long): String {
        val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd")
        return simpleDateFormat.format(milliseconds)
    }
}