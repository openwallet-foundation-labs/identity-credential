package org.multipaz.mdoc.connectionmethod

import org.multipaz.mdoc.transport.MdocTransport
import org.multipaz.nfc.NdefRecord
import org.multipaz.nfc.Nfc
import org.multipaz.util.Logger
import kotlinx.io.bytestring.buildByteString
import kotlinx.io.bytestring.encodeToByteString
import kotlinx.io.Buffer
import kotlinx.io.bytestring.ByteStringBuilder
import kotlinx.io.readByteArray
import kotlinx.io.readByteString
import kotlinx.io.write
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.addCborMap
import org.multipaz.cbor.buildCborArray
import org.multipaz.cbor.Cbor.decode
import org.multipaz.cbor.Cbor.encode
import org.multipaz.cbor.CborArray
import org.multipaz.cbor.CborMap
import org.multipaz.util.ByteDataReader
import org.multipaz.util.appendByteString
import org.multipaz.util.appendUInt16
import org.multipaz.util.appendUInt8
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
        return Cbor.encode(
            buildCborArray {
                add(METHOD_TYPE)
                add(METHOD_MAX_VERSION)
                addCborMap {
                    put(OPTION_KEY_COMMAND_DATA_FIELD_MAX_LENGTH, commandDataFieldMaxLength)
                    put(OPTION_KEY_RESPONSE_DATA_FIELD_MAX_LENGTH, responseDataFieldMaxLength)
                }
            }
        )
    }

    private fun ByteStringBuilder.appendEncodedInt(dataType: UByte, value: Int) {
        if (value < 0x100) {
            appendUInt8(0x02) // Length
            appendUInt8(dataType)
            appendUInt8(value)
        } else if (value < 0x10000) {
            appendUInt8(0x03) // Length
            appendUInt8(dataType)
            appendUInt16(value)
        } else {
            appendUInt8(0x04) // Length
            appendUInt8(dataType)
            appendUInt8(value / 0x10000)
            appendUInt16(value.and(0xFFFF))
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
        val record = NdefRecord(
            NdefRecord.Tnf.EXTERNAL_TYPE,
            Nfc.EXTERNAL_TYPE_ISO_18013_5_NFC.encodeToByteString(),
            carrierDataReference,
            buildByteString {
                appendUInt8(0x01) // Version
                appendEncodedInt(DATA_TYPE_MAXIMUM_COMMAND_DATA_LENGTH, commandDataFieldMaxLength.toInt())
                appendEncodedInt(DATA_TYPE_MAXIMUM_RESPONSE_DATA_LENGTH, responseDataFieldMaxLength.toInt())
            }
        )

        // From NFC Forum Connection Handover v1.5 section 7.1 Alternative Carrier Record
        //
        check(auxiliaryReferences.size < 0x100)
        val acRecord = NdefRecord(
            tnf = NdefRecord.Tnf.WELL_KNOWN,
            type = Nfc.RTD_ALTERNATIVE_CARRIER,
            payload = buildByteString {
                appendUInt8(0x01) // CPS: active
                appendUInt8(carrierDataReference.size) // Length of carrier data reference
                appendByteString(carrierDataReference)
                appendUInt8(auxiliaryReferences.size) // Number of auxiliary references
                for (auxRef in auxiliaryReferences) {
                    // Each auxiliary reference consists of a single byte for the length and then as
                    // many bytes for the reference itself.
                    val auxRefUtf8 = auxRef.encodeToByteString()
                    check(auxRefUtf8.size < 0x100)
                    appendUInt8(auxRefUtf8.size)
                    appendByteString(auxRefUtf8)
                }
            }
        )
        return Pair(record, acRecord)
    }

    companion object {
        private const val TAG = "ConnectionMethodNfc"

        // Defined in ISO 18013-5 8.2.2.2 Alternative Carrier Record for device retrieval using NFC
        //
        private const val DATA_TYPE_MAXIMUM_COMMAND_DATA_LENGTH: UByte = 0x01u
        private const val DATA_TYPE_MAXIMUM_RESPONSE_DATA_LENGTH: UByte = 0x02u

        const val METHOD_TYPE = 1L
        const val METHOD_MAX_VERSION = 1L
        private const val OPTION_KEY_COMMAND_DATA_FIELD_MAX_LENGTH = 0L
        private const val OPTION_KEY_RESPONSE_DATA_FIELD_MAX_LENGTH = 1L

        internal fun fromDeviceEngagement(encodedDeviceRetrievalMethod: ByteArray): ConnectionMethodNfc? {
            val array = Cbor.decode(encodedDeviceRetrievalMethod)
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
            with (ByteDataReader(record.payload)) {
                val version = getUInt8().toInt()
                if (version != 0x01) {
                    Logger.w(TAG, "Expected version 0x01, found $version")
                    return null
                }
                val cmdLen = getUInt8()
                val cmdType = getUInt8()
                if (cmdType != DATA_TYPE_MAXIMUM_COMMAND_DATA_LENGTH) {
                    Logger.w(TAG, "expected type 0x01, found $cmdType")
                    return null
                }
                if (cmdLen < 2u || cmdLen > 3u) {
                    Logger.w(TAG, "expected cmdLen in range 2-3, got $cmdLen")
                    return null
                }
                var commandDataFieldMaxLength = 0u
                for (n in 0u until cmdLen - 1u) {
                    commandDataFieldMaxLength *= 256u
                    commandDataFieldMaxLength += getUInt8()
                }
                val rspLen = getUInt8()
                val rspType = getUInt8()
                if (rspType != DATA_TYPE_MAXIMUM_RESPONSE_DATA_LENGTH) {
                    Logger.w(TAG, "expected type 0x02, found $rspType")
                    return null
                }
                if (rspLen < 2u || rspLen > 4u) {
                    Logger.w(TAG, "expected rspLen in range 2-4, got $rspLen")
                    return null
                }
                var responseDataFieldMaxLength = 0u
                for (n in 0u until rspLen - 1u) {
                    responseDataFieldMaxLength *= 256u
                    responseDataFieldMaxLength += getUInt8()
                }
                return ConnectionMethodNfc(
                    commandDataFieldMaxLength.toLong(),
                    responseDataFieldMaxLength.toLong()
                )
            }
        }
    }
}