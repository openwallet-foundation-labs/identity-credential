package com.ul.ims.gmdl.cbordata.security.mdlauthentication

import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.model.Array
import co.nstant.`in`.cbor.model.DataItem
import co.nstant.`in`.cbor.model.MajorType
import co.nstant.`in`.cbor.model.SimpleValue
import com.ul.ims.gmdl.cbordata.generic.AbstractCborStructure
import java.io.ByteArrayOutputStream
import java.io.Serializable

class Handover private constructor(
    private val handoverSelectBytes: ByteArray?,
    private val handoverRequestBytes: ByteArray?
) : AbstractCborStructure(), Serializable {

    override fun encode(): ByteArray {
        val outputStream = ByteArrayOutputStream()


        CborEncoder(outputStream).encode(toDataItem())
        return outputStream.toByteArray()
    }

    fun toDataItem(): DataItem {
        // Handover = QRHandover / NFCHandover, null if QRCode was used for engagement
        return if (handoverSelectBytes == null) {
            SimpleValue.NULL
        } else {
            val builder = CborBuilder()
                .addArray()
                .add(handoverSelectBytes) // Handover Select message

            if (handoverRequestBytes != null) {
                builder.add(handoverRequestBytes) // Handover Request message
            } else {
                builder.add(SimpleValue.NULL) // Handover Request message
            }

            builder.end()
                .build()[0]
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Handover

        if (handoverSelectBytes != null) {
            if (other.handoverSelectBytes == null) return false
            if (!handoverSelectBytes.contentEquals(other.handoverSelectBytes)) return false
        } else if (other.handoverSelectBytes != null) return false
        if (handoverRequestBytes != null) {
            if (other.handoverRequestBytes == null) return false
            if (!handoverRequestBytes.contentEquals(other.handoverRequestBytes)) return false
        } else if (other.handoverRequestBytes != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = handoverSelectBytes?.contentHashCode() ?: 0
        result = 31 * result + (handoverRequestBytes?.contentHashCode() ?: 0)
        return result
    }

    class Builder {
        private var handoverSelectBytes: ByteArray? = null
        private var handoverRequestBytes: ByteArray? = null

        fun decodeArray(array: Array?) = apply {
            array?.let {
                if (it.dataItems.size == 2) {
                    handoverSelectBytes = toByteArray(it.dataItems[0])
                    handoverRequestBytes = toByteArray(it.dataItems[1])
                }
            }
        }

        private fun toByteArray(dataItem: DataItem?): ByteArray {
            val outputStream = ByteArrayOutputStream()
            CborEncoder(outputStream).encode(dataItem)
            return outputStream.toByteArray()
        }

        fun setHandoverSelect(handoverSelectBytes: ByteArray?) = apply {
            this.handoverSelectBytes = handoverSelectBytes
        }

        fun setHandoverRequest(handoverRequestBytes: ByteArray?) = apply {
            this.handoverRequestBytes = handoverRequestBytes
        }

        fun build(): Handover {
            return Handover(handoverSelectBytes, handoverRequestBytes)
        }

        fun decode(dataItem: DataItem) = apply {
            // If QRCode handover it will be a simple value NULL don't need to decode.
            if (dataItem.majorType == MajorType.ARRAY) {
                decodeArray(dataItem as Array)
            }
        }
    }
}