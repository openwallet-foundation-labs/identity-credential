package com.android.identity.nfc

import com.android.identity.nfc.NdefRecord.Tnf
import com.android.identity.util.fromHex
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.ByteStringBuilder
import kotlinx.io.bytestring.encodeToByteString
import kotlin.math.pow

/**
 * Constants and utilities related to NFC.
 */
object Nfc {
    private const val TAG = "Nfc"

    /**
     * The Application ID for the Type 4 Tag NDEF application.
     */
    val NDEF_APPLICATION_ID = "D2760000850101".fromHex()

    /**
     * The File Identifier for the NDEF Capability Container file.
     */
    const val NDEF_CAPABILITY_CONTAINER_FILE_ID = 0xe103

    /**
     * The [ResponseApdu.status] for indicating a request was successful.
     */
    const val RESPONSE_STATUS_SUCCESS = 0x9000


    /**
     * RTD Text type, for use with [Tnf.WELL_KNOWN].
     */
    val RTD_TEXT = "T".encodeToByteString()

    /**
     * RTD URI type, for use with [Tnf.WELL_KNOWN].
     */
    val RTD_URI = "U".encodeToByteString()

    /**
     * RTD Smart Poster type, for use with [Tnf.WELL_KNOWN].
     */
    val RTD_SMART_POSTER = "Sp".encodeToByteString()

    /**
     * RTD Alternative Carrier type, for use with [Tnf.WELL_KNOWN].
     */
    val RTD_ALTERNATIVE_CARRIER = "ac".encodeToByteString()

    /**
     * RTD Handover Carrier type, for use with [Tnf.WELL_KNOWN].
     */
    val RTD_HANDOVER_CARRIER = "Hc".encodeToByteString()

    /**
     * RTD Handover Request type, for use with [Tnf.WELL_KNOWN].
     */
    val RTD_HANDOVER_REQUEST = "Hr".encodeToByteString()

    /**
     * RTD Handover Select type, for use with [Tnf.WELL_KNOWN].
     */
    val RTD_HANDOVER_SELECT = "Hs".encodeToByteString()

    /**
     * RTD Service Select type, for use with [Tnf.WELL_KNOWN].
     */
    val RTD_SERVICE_SELECT = "Ts".encodeToByteString()

    /**
     * RTD Service Parameter type, for use with [Tnf.WELL_KNOWN].
     */
    val RTD_SERVICE_PARAMETER = "Tp".encodeToByteString()

    /**
     * RTD TNEP Status Record type, for use with [Tnf.WELL_KNOWN].
     */
    val RTD_TNEP_STATUS = "Te".encodeToByteString()

    /**
     * The service name for connection handover.
     *
     * Reference: NFC Forum Connection Handover Technical Specification section 4.1.2.
     */
    const val SERVICE_NAME_CONNECTION_HANDOVER = "urn:nfc:sn:handover"

    /**
     * Encodes the Service Select payload.
     *
     * Reference: NFC Forum Tag NDEF Exchange Protocol section 4.2.2.
     *
     * @param serviceName the service name.
     * @return the payload.
     */
    fun encodeServiceSelectPayload(serviceName: String): ByteString {
        require(serviceName.length < 256) { "Service Name length must be shorter than 256" }
        val bsb = ByteStringBuilder()
        bsb.append(serviceName.length.toByte())
        bsb.append(serviceName.encodeToByteArray())
        return bsb.toByteString()
    }

    /**
     * Service Parameter Record.
     *
     * Reference: NFC Forum Tag NDEF Exchange Protocol section 4.1.2 Service Parameter Record.
     *
     * @property tnepVersion TNEP version.
     * @property serviceNameUri Service Name URI.
     * @property tnepCommunicationMode TNEP communication mode.
     * @property tWaitMillis Minimum waiting time twait.
     * @property nWait Maximum number of waiting time extensions.
     * @property maxNdefSize Maximum NDEF Message size in bytes.
     */
    data class ServiceParameterRecord(
        val tnepVersion: Int,
        val serviceNameUri: String,
        val tnepCommunicationMode: Int,
        val tWaitMillis: Double,
        val nWait: Int,
        val maxNdefSize: Int
    ) {
        companion object {
            /**
             * Checks if a record is a Service Parameter record and parses it if so.
             *
             * @param record the record to check
             * @return a [Nfc.ServiceParameterRecord] or `null`.
             */
            fun parseRecord(record: NdefRecord): ServiceParameterRecord? {
                if (record.tnf != Tnf.WELL_KNOWN ||
                    record.type != RTD_SERVICE_PARAMETER) {
                    return null
                }

                val p = record.payload
                require(p.size >= 1) { "Unexpected length of Service Parameter Record" }
                val serviceNameLen = p[1].toInt()
                require(p.size == serviceNameLen + 7) { "Unexpected length of body in Service Parameter Record" }

                val wtInt = p[3 + serviceNameLen].toInt().and(0xff)
                return ServiceParameterRecord(
                    p[0].toInt().and(0xff),
                    p.toByteArray().decodeToString(2, serviceNameLen + 2),
                    p[2 + serviceNameLen].toInt().and(0xff),
                    2.0.pow((wtInt/4 - 1).toDouble()),
                    p[4 + serviceNameLen].toInt().and(0xff),
                    (p[5 + serviceNameLen].toInt().and(0xff)) * 0x100 + (p[6 + serviceNameLen].toInt().and(0xff))
                )
            }
        }
    }

}
