package com.android.identity.nfc

import kotlinx.io.Buffer
import kotlinx.io.readByteArray

class NdefMessage(
    val records: List<NdefRecord>
) {
    fun encode(): ByteArray {
        val buf = Buffer()
        for (idx in records.indices) {
            val record = records[idx]
            val mb = (idx == 0)                  // first record
            val me = (idx == records.size - 1)   // last recrod
            record.encode(buf, mb, me)
        }
        return buf.readByteArray()
    }

    companion object {
        fun fromEncoded(encoded: ByteArray): NdefMessage {
            return NdefMessage(NdefRecord.fromEncoded(encoded))
        }
    }
}

