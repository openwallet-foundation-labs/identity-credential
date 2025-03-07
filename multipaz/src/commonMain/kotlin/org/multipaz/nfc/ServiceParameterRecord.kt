package org.multipaz.nfc

import org.multipaz.util.ByteDataReader
import org.multipaz.util.appendByteString
import org.multipaz.util.appendUInt16
import org.multipaz.util.appendUInt8
import org.multipaz.util.getUInt8
import kotlinx.io.bytestring.buildByteString
import kotlinx.io.bytestring.encodeToByteString
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
        check(tnepCommunicationMode >= 0 && tnepCommunicationMode < 256)
        check(wtInt >= 0 && wtInt < 64)
        check(nWait >= 0 && nWait < 16)
        return NdefRecord(
            tnf = NdefRecord.Tnf.WELL_KNOWN,
            type = Nfc.RTD_SERVICE_PARAMETER,
            payload = buildByteString {
                appendUInt8(tnepVersion)
                appendUInt8(serviceNameUri.length)
                appendByteString(serviceNameUri.encodeToByteString())
                appendUInt8(tnepCommunicationMode)
                appendUInt8(wtInt)
                appendUInt8(nWait)
                appendUInt16(maxNdefSize)
            }
        )
    }

    companion object {
        /**
         * Checks if a record is a Service Parameter record and parses it if so.
         *
         * @param record the record to check
         * @return a [org.multipaz.nfc.ServiceParameterRecord] or `null`.
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
                    tnepVersion = getUInt8().toInt(),
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