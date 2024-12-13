package com.android.identity.nfc

import com.android.identity.util.Logger
import com.android.identity.util.UUID
import com.android.identity.util.fromHex
import com.android.identity.util.toHex
import kotlinx.coroutines.delay
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.readLongLe
import kotlin.math.ceil
import kotlin.math.pow

private fun Buffer.writeByte(intVal: Int) {
    this.writeByte(intVal.and(0xff).toByte())
}

object NfcUtil {
    private const val TAG = "NfcUtil"

    // Defined by NFC Forum
    val AID_FOR_TYPE_4_TAG_NDEF_APPLICATION = "D2760000850101".fromHex()

    // Defined by 18013-5 Section 8.3.3.1.2 Data retrieval using near field communication (NFC)
    val AID_FOR_MDL_DATA_TRANSFER = "A0000002480400".fromHex()

    const val COMMAND_TYPE_OTHER = 0
    const val COMMAND_TYPE_SELECT_BY_AID = 1
    const val COMMAND_TYPE_SELECT_FILE = 2
    const val COMMAND_TYPE_READ_BINARY = 3
    const val COMMAND_TYPE_UPDATE_BINARY = 4
    const val COMMAND_TYPE_ENVELOPE = 5
    const val COMMAND_TYPE_RESPONSE = 6
    const val CAPABILITY_CONTAINER_FILE_ID = 0xe103
    const val NDEF_FILE_ID = 0xe104

    val STATUS_WORD_INSTRUCTION_NOT_SUPPORTED = byteArrayOf(0x6d.toByte(), 0x00.toByte())
    val STATUS_WORD_OK = byteArrayOf(0x90.toByte(), 0x00.toByte())
    val STATUS_WORD_FILE_NOT_FOUND = byteArrayOf(0x6a.toByte(), 0x82.toByte())
    val STATUS_WORD_END_OF_FILE_REACHED = byteArrayOf(0x62.toByte(), 0x82.toByte())
    val STATUS_WORD_WRONG_PARAMETERS = byteArrayOf(0x6b.toByte(), 0x00.toByte())
    val STATUS_WORD_WRONG_LENGTH = byteArrayOf(0x67.toByte(), 0x00.toByte())

    fun nfcGetCommandType(apdu: ByteArray): Int {
        if (apdu.size < 3) {
            return COMMAND_TYPE_OTHER
        }
        val ins = apdu[1].toInt() and 0xff
        val p1 = apdu[2].toInt() and 0xff
        if (ins == 0xA4) {
            if (p1 == 0x04) {
                return COMMAND_TYPE_SELECT_BY_AID
            } else if (p1 == 0x00) {
                return COMMAND_TYPE_SELECT_FILE
            }
        } else if (ins == 0xb0) {
            return COMMAND_TYPE_READ_BINARY
        } else if (ins == 0xd6) {
            return COMMAND_TYPE_UPDATE_BINARY
        } else if (ins == 0xc0) {
            return COMMAND_TYPE_RESPONSE
        } else if (ins == 0xc3) {
            return COMMAND_TYPE_ENVELOPE
        }
        return COMMAND_TYPE_OTHER
    }

    fun createApduApplicationSelect(aid: ByteArray): ByteArray {
        val buf = Buffer()
        buf.writeByte(0x00)
        buf.writeByte(0xa4)
        buf.writeByte(0x04)
        buf.writeByte(0x00)
        buf.writeByte(0x07)
        buf.write(aid)
        return buf.readByteArray()
    }

    fun createApduSelectFile(fileId: Int): ByteArray {
        val buf = Buffer()
        buf.writeByte(0x00)
        buf.writeByte(0xa4)
        buf.writeByte(0x00)
        buf.writeByte(0x0c)
        buf.writeByte(0x02)
        buf.writeByte(fileId / 0x100)
        buf.writeByte(fileId and 0xff)
        return buf.readByteArray()
    }

    fun createApduReadBinary(offset: Int, length: Int): ByteArray {
        val buf = Buffer()
        buf.writeByte(0x00)
        buf.writeByte(0xb0)
        buf.writeByte(offset / 0x100)
        buf.writeByte(offset and 0xff)
        require(length != 0) { "Length cannot be zero" }
        if (length < 0x100) {
            buf.writeByte(length and 0xff)
        } else {
            buf.writeByte(0x00)
            buf.writeByte(length / 0x100)
            buf.writeByte(length and 0xff)
        }
        return buf.readByteArray()
    }

    fun createApduUpdateBinary(offset: Int, data: ByteArray): ByteArray {
        val buf = Buffer()
        buf.writeByte(0x00)
        buf.writeByte(0xd6)
        buf.writeByte(offset / 0x100)
        buf.writeByte(offset and 0xff)
        require(data.size < 0x100) { "Data must be shorter than 0x100 bytes" }
        buf.writeByte(data.size and 0xff)
        buf.write(data)
        return buf.readByteArray()
    }

    fun createNdefMessageServiceSelect(serviceName: String): ByteArray {
        // [TNEP] section 4.2.2 Service Select Record
        val payload = " $serviceName".encodeToByteArray()
        payload[0] = (payload.size - 1).toByte()
        val record = NdefRecord(
            NdefRecord.TNF_WELL_KNOWN,
            "Ts".encodeToByteArray(),
            byteArrayOf(),
            payload
        )
        return NdefMessage(listOf(record)).encode()
    }

    fun findServiceParameterRecordWithName(
        ndefMessage: ByteArray,
        serviceName: String
    ): NdefRecord? {
        val m = NdefMessage.fromEncoded(ndefMessage)
        val snUtf8 = serviceName.encodeToByteArray()
        val expectedPayload = byteArrayOf(0x10, snUtf8.size.and(0xff).toByte()) + snUtf8
        for (r in m.records) {
            Logger.iHex(TAG, "expectedPayload", expectedPayload)
            Logger.iHex(TAG, "payload", r.payload)
            if (r.tnf == NdefRecord.TNF_WELL_KNOWN &&
                r.type contentEquals "Tp".encodeToByteArray() &&
                r.payload.size > expectedPayload.size &&
                expectedPayload contentEquals r.payload.sliceArray(IntRange(0, expectedPayload.size - 1))) {
                return r
            }
        }
        return null
    }

    fun parseServiceParameterRecord(serviceParameterRecord: NdefRecord): ParsedServiceParameterRecord {
        require(serviceParameterRecord.tnf == NdefRecord.TNF_WELL_KNOWN) { "Record is not well known" }
        require(serviceParameterRecord.type contentEquals "Tp".encodeToByteArray()) { "Expected type Tp" }

        // See [TNEP] 4.1.2 Service Parameter Record for the payload
        val p = serviceParameterRecord.payload!!
        require(p.size >= 1) { "Unexpected length of Service Parameter Record" }
        val serviceNameLen = p[1].toInt()
        require(p.size == serviceNameLen + 7) { "Unexpected length of body in Service Parameter Record" }

        val psprTnepVersion = p[0].toInt() and 0xff
        val psprServiceNameUri = p.decodeToString(2)
        val psprTnepCommunicationMode = p[2 + serviceNameLen].toInt() and 0xff
        val wt_int = p[3 + serviceNameLen].toInt() and 0xff
        val psprTWaitMillis = 2.0.pow((wt_int / 4 - 1).toDouble())
        val psprNWait = p[4 + serviceNameLen].toInt() and 0xff
        val psprMaxNdefSize =
            (p[5 + serviceNameLen].toInt() and 0xff) * 0x100 + (p[6 + serviceNameLen].toInt() and 0xff)
        return ParsedServiceParameterRecord(
            psprTnepVersion,
            psprServiceNameUri,
            psprTnepCommunicationMode,
            psprTWaitMillis,
            psprNWait,
            psprMaxNdefSize
        )
    }

    fun findTnepStatusRecord(ndefMessage: ByteArray): NdefRecord? {
        var m = NdefMessage.fromEncoded(ndefMessage)
        for (r in m.records) {
            if (r.tnf == NdefRecord.TNF_WELL_KNOWN &&
                "Te".encodeToByteArray() contentEquals r.type) {
                return r
            }
        }
        return null
    }

    data class ParsedServiceParameterRecord(
            val tnepVersion: Int,
            val serviceNameUri: String,
            val tnepCommunicationMode: Int,
            val tWaitMillis: Double,
            val nWait: Int,
            val maxNdefSize: Int
    )

    suspend fun readBinary(
        tag: NfcIsoTag,
        offset: Int,
        size: Int
    ): ByteArray? {
        val ret = tag.transceive(NfcUtil.createApduReadBinary(offset, size))
        if (ret.size < 2 || ret[ret.size - 2] != 0x90.toByte() || ret[ret.size - 1] != 0x00.toByte()) {
            Logger.eHex(TAG, "Error sending READ_BINARY command, ret", ret)
            return null
        }
        return ret.sliceArray(IntRange(0, ret.size - 3))
    }

    suspend fun ndefReadMessage(
        tag: NfcIsoTag,
        tWaitMillis: Double,
        nWait: Int
    ): ByteArray? {
        var nWait_ = nWait
        var apdu: ByteArray
        var ret: ByteArray
        var replyLen: Int
        do {
            apdu = NfcUtil.createApduReadBinary(0x0000, 2)
            ret = tag.transceive(apdu)
            if (ret.size != 4 || ret[2] != 0x90.toByte() || ret[3] != 0x00.toByte()) {
                Logger.eHex(TAG, "ndefReadMessage: Malformed response for first " +
                        "READ_BINARY command for length, ret", ret)
                return null
            }
            replyLen = (ret[0].toInt() and 0xff) * 256 + (ret[1].toInt() and 0xff)
            if (replyLen > 0) {
                break
            }

            // As per [TNEP] 4.1.7 if the tag sends an empty NDEF message it means that
            // it's requesting extra time... honor this if we can.
            if (nWait_ > 0) {
                Logger.d(TAG, "ndefReadMessage: NDEF message with length 0 and $nWait_ time extensions left")
                val waitMillis = ceil(tWaitMillis).toLong()
                Logger.d(TAG,"ndefReadMessage: Sleeping $waitMillis ms")
                delay(waitMillis)
                nWait_--
            } else {
                Logger.e(TAG, "ndefReadMessage: NDEF message with length 0 but no time extensions left")
                return null
            }
        } while (true)
        apdu = NfcUtil.createApduReadBinary(0x0002, replyLen)
        ret = tag.transceive(apdu)
        if (ret.size != replyLen + 2 || ret[replyLen] != 0x90.toByte() || ret[replyLen + 1] != 0x00.toByte()) {
            Logger.eHex(TAG, "Malformed response for second READ_BINARY command for payload, ret", ret)
            return null
        }
        return ret.copyOfRange(0, ret.size - 2)
    }

    suspend fun ndefTransact(
        tag: NfcIsoTag,
        ndefMessage: ByteArray,
        tWaitMillis: Double, nWait: Int
    ): ByteArray? {
        var apdu: ByteArray
        var ret: ByteArray
        Logger.dHex(TAG, "ndefTransact: writing NDEF message", ndefMessage)

        // See Type 4 Tag Technical Specification Version 1.2 section 7.5.5 NDEF Write Procedure
        // for how this is done.

        // Check to see if we can merge the three UPDATE_BINARY messages into a single message.
        // This is allowed as per [T4T] 7.5.5 NDEF Write Procedure:
        //
        //   If the entire NDEF Message can be written with a single UPDATE_BINARY
        //   Command, the Reader/Writer MAY write NLEN and ENLEN (Symbol 6), as
        //   well as the entire NDEF Message (Symbol 5) using a single
        //   UPDATE_BINARY Command. In this case the Reader/Writer SHALL
        //   proceed to Symbol 5 and merge Symbols 5 and 6 operations into a single
        //   UPDATE_BINARY Command.
        //
        // For debugging, this optimization can be turned off by setting this to |true|:
        val bypassUpdateBinaryOptimization = false
        if (!bypassUpdateBinaryOptimization && ndefMessage.size < 256 - 2) {
            Logger.d(TAG, "ndefTransact: using single UPDATE_BINARY command")
            val data = ByteArray(ndefMessage.size + 2)
            data[0] = 0.toByte()
            data[1] = (ndefMessage.size and 0xff).toByte()
            ndefMessage.copyInto(data, 2, 0, ndefMessage.size)
            apdu = NfcUtil.createApduUpdateBinary(0x0000, data)
            ret = tag.transceive(apdu)
            if (!(ret contentEquals NfcUtil.STATUS_WORD_OK)) {
                Logger.eHex(TAG, "Error sending combined UPDATE_BINARY command, ret", ret)
                return null
            }
        } else {
            Logger.d(TAG, "ndefTransact: using 3+ UPDATE_BINARY commands")

            // First command is UPDATE_BINARY to reset length
            apdu = NfcUtil.createApduUpdateBinary(0x0000, byteArrayOf(0x00, 0x00))
            ret = tag.transceive(apdu)
            if (!ret.contentEquals(NfcUtil.STATUS_WORD_OK)) {
                Logger.eHex(TAG, "Error sending initial UPDATE_BINARY command, ret", ret)
                return null
            }

            // Subsequent commands are UPDATE_BINARY with payload, chopped into bits no longer
            // than 255 bytes each
            var offset = 0
            var remaining = ndefMessage.size
            while (remaining > 0) {
                val numBytesToWrite = remaining.coerceAtMost(255)
                val bytesToWrite = ndefMessage.copyOfRange(offset, offset + numBytesToWrite)
                apdu = NfcUtil.createApduUpdateBinary(offset + 2, bytesToWrite)
                ret = tag.transceive(apdu)
                if (!ret.contentEquals(NfcUtil.STATUS_WORD_OK)) {
                    Logger.eHex(TAG, "Error sending UPDATE_BINARY command with payload, ret", ret)
                    return null
                }
                remaining -= numBytesToWrite
                offset += numBytesToWrite
            }

            // Final command is UPDATE_BINARY to write the length
            val encodedLength = byteArrayOf(
                (ndefMessage.size / 0x100 and 0xff).toByte(),
                (ndefMessage.size and 0xff).toByte()
            )
            apdu = NfcUtil.createApduUpdateBinary(0x0000, encodedLength)
            ret = tag.transceive(apdu)
            if (!ret.contentEquals(NfcUtil.STATUS_WORD_OK)) {
                Logger.eHex(TAG, "Error sending final UPDATE_BINARY command, ret", ret)
                return null
            }
        }
        val waitMillis = ceil(tWaitMillis).toLong() // Just round up to closest millisecond
        Logger.d(TAG, "ndefTransact: Sleeping $waitMillis ms")
        delay(waitMillis)

        // Now read NDEF file...
        val receivedNdefMessage = ndefReadMessage(tag, tWaitMillis, nWait) ?: return null
        Logger.dHex(TAG, "ndefTransact: read NDEF message", receivedNdefMessage)
        return receivedNdefMessage
    }

}
