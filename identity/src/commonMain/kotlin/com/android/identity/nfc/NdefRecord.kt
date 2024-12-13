package com.android.identity.nfc

import com.android.identity.util.Logger
import com.android.identity.util.toHex
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.readUByte
import kotlin.experimental.and

class NdefRecord(
    val tnf: Int,
    val type: ByteArray,
    val id: ByteArray,
    val payload: ByteArray
) {

    internal fun encode(
        buf: Buffer,
        mb: Boolean,
        me: Boolean
    ) {
        val sr = payload.size < 256
        val il = if (tnf == TNF_EMPTY) true else (id.size > 0)
        var flags = tnf
        if (mb) flags = flags or FLAG_MB
        if (me) flags = flags or FLAG_ME
        if (sr) flags = flags or FLAG_SR
        if (il) flags = flags or FLAG_IL

        buf.writeByte(flags.toByte())
        buf.writeByte(type.size.toByte())
        if (sr) {
            buf.writeByte(payload.size.toByte())
        } else {
            buf.writeInt(payload.size)
        }
        if (il) {
            buf.writeByte(id.size.toByte())
        }
        buf.write(type)
        buf.write(id)
        buf.write(payload)
    }

    override fun equals(other: Any?): Boolean =
        other != null && other is NdefRecord &&
                other.tnf == tnf &&
                other.payload contentEquals payload &&
                other.type contentEquals type &&
                other.id contentEquals id

    override fun hashCode(): Int {
        var result = id.contentHashCode();
        result = 31*result + payload.contentHashCode();
        result = 31*result + tnf;
        result = 31*result + type.contentHashCode();
        return result;
    }

    override fun toString(): String {
        return "NdefRecord(tnf=$tnf id=${id.toHex()} type=${type.toHex()} payload=${payload.toHex()})"
    }

    companion object {
        private const val TAG = "NdefRecord"

        const val TNF_EMPTY = 0x00
        const val TNF_WELL_KNOWN = 0x01
        const val TNF_MIME_MEDIA = 0x02
        const val TNF_EXTERNAL_TYPE = 0x04

        private const val FLAG_MB = 0x80;
        private const val FLAG_ME = 0x40;
        private const val FLAG_CF = 0x20;
        private const val FLAG_SR = 0x10;
        private const val FLAG_IL = 0x08;

        internal fun fromEncoded(encoded: ByteArray): List<NdefRecord> {
            Logger.iHex(TAG, "fromEncoded", encoded)
            val buf = Buffer()
            buf.write(encoded)
            val records = mutableListOf<NdefRecord>()
            while (!buf.exhausted()) {
                val flags = buf.readByte().toInt().and(0xff)
                val mb = flags.and(FLAG_MB) != 0x00
                val me = flags.and(FLAG_ME) != 0x00
                val cf = flags.and(FLAG_CF) != 0x00
                val sr = flags.and(FLAG_SR) != 0x00
                val il = flags.and(FLAG_IL) != 0x00
                val tnf = flags.and(0x07)

                // TODO: check mb/me
                if (cf) {
                    TODO("Add support for chunks")
                }

                val typeLength = buf.readByte().toInt().and(0xff)
                val payloadLength = if (sr) {
                    buf.readByte().toInt().and(0xff)
                } else {
                    buf.readInt()
                }
                val idLength = if (il) {
                    buf.readByte().toInt().and(0xff)
                } else {
                    0
                }
                val type = buf.readByteArray(typeLength)
                val id = buf.readByteArray(idLength)
                val payload = buf.readByteArray(payloadLength)
                Logger.i(TAG, "Added record tnf=$tnf t=${type.toHex()} p=${payload.toHex()} i=${id.toHex()}")
                records.add(
                    NdefRecord(
                        tnf = tnf,
                        type = type,
                        id = id,
                        payload = payload
                    )
                )
            }
            return records
        }
    }
}

