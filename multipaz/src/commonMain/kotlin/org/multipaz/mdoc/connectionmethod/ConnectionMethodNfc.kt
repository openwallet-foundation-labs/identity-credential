package org.multipaz.mdoc.connectionmethod

import org.multipaz.cbor.Cbor.decode
import org.multipaz.cbor.Cbor.encode
import org.multipaz.cbor.CborArray
import org.multipaz.cbor.CborMap
import org.multipaz.mdoc.transport.MdocTransport
import org.multipaz.nfc.NdefRecord
import org.multipaz.nfc.Nfc
import org.multipaz.util.Logger
import kotlinx.io.Buffer
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.encodeToByteString
import kotlinx.io.readByteArray
import kotlinx.io.readByteString
import kotlinx.io.write

/**
 * Connection method for NFC.
 *
 * @param commandDataFieldMaxLength  the maximum length for the command data field.
 * @param responseDataFieldMaxLength the maximum length of the response data field.
 */
class ConnectionMethodNfc(
    val commandDataFieldMaxLength: Long,
    val responseDataFieldMaxLength: Long
): ConnectionMethod() {
    override fun equals(other: Any?): Boolean {
        return other is ConnectionMethodNfc &&
                other.commandDataFieldMaxLength == commandDataFieldMaxLength &&
                other.responseDataFieldMaxLength == responseDataFieldMaxLength
    }

    override fun toString(): String =
        "nfc:cmd_max_length=$commandDataFieldMaxLength:resp_max_length=$responseDataFieldMaxLength"

    override fun toDeviceEngagement(): ByteArray {
        val builder = CborMap.builder()
        builder.put(OPTION_KEY_COMMAND_DATA_FIELD_MAX_LENGTH, commandDataFieldMaxLength)
        builder.put(OPTION_KEY_RESPONSE_DATA_FIELD_MAX_LENGTH, responseDataFieldMaxLength)
        return encode(
            CborArray.builder()
                .add(METHOD_TYPE)
                .add(METHOD_MAX_VERSION)
                .add(builder.end().build())
                .end().build()
        )
    }


    private fun encodeInt(dataType: Int, value: Int, buf: Buffer) {
        if (value < 0x100) {
            buf.writeByte(0x02.toByte()) // Length
            buf.writeByte(dataType.toByte())
            buf.writeByte(value.and(0xff).toByte())
        } else if (value < 0x10000) {
            buf.writeByte(0x03.toByte()) // Length
            buf.writeByte(dataType.toByte())
            buf.writeByte((value / 0x100).toByte())
            buf.writeByte((value.and(0xff)).toByte())
        } else {
            buf.writeByte(0x04.toByte()) // Length
            buf.writeByte(dataType.toByte())
            buf.writeByte((value / 0x10000).toByte())
            buf.writeByte((value / 0x100).and(0xff).toByte())
            buf.writeByte((value.and(0xff)).toByte())
        }
    }

    override fun toNdefRecord(
        auxiliaryReferences: List<String>,
        role: MdocTransport.Role,
        skipUuids: Boolean
    ): Pair<NdefRecord, NdefRecord>? {
        val carrierDataReference = "nfc".encodeToByteString()

        // This is defined by ISO 18013-5 8.2.2.2 Alternative Carrier Record for device
        // retrieval using NFC.
        //
        val buf = Buffer()
        buf.writeByte(0x01.toByte()) // Version
        encodeInt(DATA_TYPE_MAXIMUM_COMMAND_DATA_LENGTH, commandDataFieldMaxLength.toInt(), buf)
        encodeInt(DATA_TYPE_MAXIMUM_RESPONSE_DATA_LENGTH, responseDataFieldMaxLength.toInt(), buf)
        val record = NdefRecord(
            NdefRecord.Tnf.EXTERNAL_TYPE,
            Nfc.EXTERNAL_TYPE_ISO_18013_5_NFC.encodeToByteString(),
            carrierDataReference,
            buf.readByteString()
        )

        // From NFC Forum Connection Handover v1.5 section 7.1 Alternative Carrier Record
        //
        check(auxiliaryReferences.size < 0x100)
        val acrBuf = Buffer()
        acrBuf.writeByte(0x01) // CPS: active
        acrBuf.writeByte(carrierDataReference.size.toByte()) // Length of carrier data reference
        acrBuf.write(carrierDataReference)
        acrBuf.writeByte(auxiliaryReferences.size.toByte()) // Number of auxiliary references
        for (auxRef in auxiliaryReferences) {
            // Each auxiliary reference consists of a single byte for the length and then as
            // many bytes for the reference itself.
            val auxRefUtf8 = auxRef.encodeToByteString()
            check(auxRefUtf8.size < 0x100)
            acrBuf.writeByte(auxRefUtf8.size.toByte())
            acrBuf.write(auxRefUtf8)
        }
        val acRecordPayload = acrBuf.readByteArray()
        val acRecord = NdefRecord(
            tnf = NdefRecord.Tnf.WELL_KNOWN,
            type = Nfc.RTD_ALTERNATIVE_CARRIER,
            payload = ByteString(acRecordPayload)
        )
        return Pair(record, acRecord)
    }

    companion object {
        private const val TAG = "ConnectionMethodNfc"

        // Defined in ISO 18013-5 8.2.2.2 Alternative Carrier Record for device retrieval using NFC
        //
        private const val DATA_TYPE_MAXIMUM_COMMAND_DATA_LENGTH = 0x01
        private const val DATA_TYPE_MAXIMUM_RESPONSE_DATA_LENGTH = 0x02

        const val METHOD_TYPE = 1L
        const val METHOD_MAX_VERSION = 1L
        private const val OPTION_KEY_COMMAND_DATA_FIELD_MAX_LENGTH = 0L
        private const val OPTION_KEY_RESPONSE_DATA_FIELD_MAX_LENGTH = 1L

        internal fun fromDeviceEngagement(encodedDeviceRetrievalMethod: ByteArray): ConnectionMethodNfc? {
            val array = decode(encodedDeviceRetrievalMethod)
            val type = array[0].asNumber
            val version = array[1].asNumber
            require(type == METHOD_TYPE)
            if (version > METHOD_MAX_VERSION) {
                return null
            }
            val map = array[2]
            return ConnectionMethodNfc(
                map[OPTION_KEY_COMMAND_DATA_FIELD_MAX_LENGTH].asNumber,
                map[OPTION_KEY_RESPONSE_DATA_FIELD_MAX_LENGTH].asNumber
            )
        }

        internal fun fromNdefRecord(
            record: NdefRecord,
            role: MdocTransport.Role,
        ): ConnectionMethodNfc? {
            val payload = Buffer()
            payload.write(record.payload)
            val version = payload.readByte().toInt()
            if (version != 0x01) {
                Logger.w(TAG, "Expected version 0x01, found $version")
                return null
            }
            val cmdLen = payload.readByte().toInt().and(0xff)
            val cmdType = payload.readByte().toInt().and(0xff)
            if (cmdType != DATA_TYPE_MAXIMUM_COMMAND_DATA_LENGTH) {
                Logger.w(TAG, "expected type 0x01, found $cmdType")
                return null
            }
            if (cmdLen < 2 || cmdLen > 3) {
                Logger.w(TAG, "expected cmdLen in range 2-3, got $cmdLen")
                return null
            }
            var commandDataFieldMaxLength = 0
            for (n in 0 until cmdLen - 1) {
                commandDataFieldMaxLength *= 256
                commandDataFieldMaxLength += payload.readByte().toInt().and(0xff)
            }
            val rspLen = payload.readByte().toInt().and(0xff)
            val rspType = payload.readByte().toInt().and(0xff)
            if (rspType != DATA_TYPE_MAXIMUM_RESPONSE_DATA_LENGTH) {
                Logger.w(TAG, "expected type 0x02, found $rspType")
                return null
            }
            if (rspLen < 2 || rspLen > 4) {
                Logger.w(TAG, "expected rspLen in range 2-4, got $rspLen")
                return null
            }
            var responseDataFieldMaxLength = 0
            for (n in 0 until rspLen - 1) {
                responseDataFieldMaxLength *= 256
                responseDataFieldMaxLength += payload.readByte().toInt().and(0xff)
            }
            return ConnectionMethodNfc(
                commandDataFieldMaxLength.toLong(),
                responseDataFieldMaxLength.toLong()
            )
        }
    }
}