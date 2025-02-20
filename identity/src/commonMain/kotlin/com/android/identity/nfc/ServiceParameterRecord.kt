package com.android.identity.nfc

import com.android.identity.util.ByteDataReader
import com.android.identity.util.appendArray
import com.android.identity.util.appendUInt16
import com.android.identity.util.appendUInt8
import com.android.identity.util.getUInt8
import kotlinx.io.bytestring.ByteStringBuilder
import kotlin.math.pow
import kotlin.time.Duration

/**
 * Service Parameter Record.
 *
 * Reference: NFC Forum Tag NDEF Exchange Protocol section 4.1.2 Service Parameter Record.
 *
 * @property tnepVersion TNEP version.
 * @property serviceNameUri Service Name URI.
 * @property tnepCommunicationMode TNEP communication mode.
 * @property wtInt Minimum waiting time, use [Duration.Companion.fromWtInt] to convert to a [Duration].
 * @property nWait Maximum number of waiting time extensions.
 * @property maxNdefSize Maximum NDEF Message size in bytes.
 */
data class ServiceParameterRecord(
    val tnepVersion: Int,
    val serviceNameUri: String,
    val tnepCommunicationMode: Int,
    val wtInt: Int,
    val nWait: Int,
    val maxNdefSize: Int
) {
    /**
     * Generate a [NdefRecord].
     *
     * @return a [NdefRecord].
     */
    fun generateNdefRecord(): NdefRecord {
        check(serviceNameUri.length < 256) { "Service name length must fit in a byte" }

        val bsb = ByteStringBuilder()
        bsb.appendUInt8(tnepVersion)
        bsb.appendUInt8(serviceNameUri.length)
        bsb.appendArray(serviceNameUri.encodeToByteArray())
        bsb.appendUInt8(tnepCommunicationMode)
        bsb.appendUInt8(wtInt)
        bsb.appendUInt8(nWait)
        bsb.appendUInt16(maxNdefSize)

        return NdefRecord(
            tnf = NdefRecord.Tnf.WELL_KNOWN,
            type = Nfc.RTD_SERVICE_PARAMETER,
            payload = bsb.toByteString()
        )
    }

    companion object {
        /**
         * Checks if a record is a Service Parameter record and parses it if so.
         *
         * @param record the record to check
         * @return a [com.android.identity.nfc.ServiceParameterRecord] or `null`.
         */
        fun fromNdefRecord(record: NdefRecord): ServiceParameterRecord? {
            if (record.tnf != NdefRecord.Tnf.WELL_KNOWN ||
                record.type != Nfc.RTD_SERVICE_PARAMETER) {
                return null
            }

            val p = record.payload
            require(p.size >= 1) { "Unexpected length of Service Parameter Record" }
            val serviceNameLen = p.getUInt8(1).toInt()
            require(p.size == serviceNameLen + 7) { "Unexpected length of body in Service Parameter Record" }

            return with (ByteDataReader(p)) {
                ServiceParameterRecord(
                    tnepVersion = getUInt8().toInt(), // TODO: b/393388370 - 1 byte, then skip 1 byte? Looks wrong.
                    serviceNameUri = skip(1).getString(serviceNameLen),
                    tnepCommunicationMode = getUInt8().toInt(),
                    wtInt = getUInt8().toInt(),
                    nWait = getUInt8().toInt(),
                    maxNdefSize = getUInt16().toInt()
                )
            }
        }
    }
}

/**
 * Converts Minimum Waiting Time to a duration.
 *
 * Reference: NFC Forum Tag NDEF Exchange Protocol section 4.1.6 Minimum Waiting Time.
 *
 * @param wtInt the Minimum Waiting Time value.
 * @return a [Duration].
 */
fun Duration.Companion.fromWtInt(wtInt: Int) = (2.0.pow((wtInt/4 - 1).toDouble())/1000.0).seconds