package com.android.identity.nfc

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
        check(tnepVersion >= 0 && tnepVersion < 256)
        check(serviceNameUri.length < 256) { "Service name length must fit in a byte" }
        check(tnepCommunicationMode >= 0 && tnepCommunicationMode < 256)
        check(wtInt >= 0 && wtInt < 64)
        check(nWait >= 0 && nWait < 16)
        val bsb = ByteStringBuilder()
        bsb.append(tnepVersion.toByte())
        bsb.append(serviceNameUri.length.toByte())
        bsb.append(serviceNameUri.encodeToByteArray())
        bsb.append(tnepCommunicationMode.toByte())
        bsb.append(wtInt.toByte())
        bsb.append(nWait.toByte())
        bsb.append((maxNdefSize/0x100).toByte())
        bsb.append(maxNdefSize.and(0xff).toByte())
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
         * @return a [Nfc.ServiceParameterRecord] or `null`.
         */
        fun fromNdefRecord(record: NdefRecord): ServiceParameterRecord? {
            if (record.tnf != NdefRecord.Tnf.WELL_KNOWN ||
                record.type != Nfc.RTD_SERVICE_PARAMETER) {
                return null
            }

            val p = record.payload
            require(p.size >= 1) { "Unexpected length of Service Parameter Record" }
            val serviceNameLen = p[1].toInt().and(0xff)
            require(p.size == serviceNameLen + 7) { "Unexpected length of body in Service Parameter Record" }

            return ServiceParameterRecord(
                tnepVersion = p[0].toInt().and(0xff),
                serviceNameUri = p.toByteArray().decodeToString(2, serviceNameLen + 2),
                tnepCommunicationMode = p[2 + serviceNameLen].toInt().and(0xff),
                wtInt = p[3 + serviceNameLen].toInt().and(0xff),
                nWait = p[4 + serviceNameLen].toInt().and(0xff),
                maxNdefSize = (p[5 + serviceNameLen].toInt().and(0xff)) * 0x100 + (p[6 + serviceNameLen].toInt().and(0xff))
            )
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