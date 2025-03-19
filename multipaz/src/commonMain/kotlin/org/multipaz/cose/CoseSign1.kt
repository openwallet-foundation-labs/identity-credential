package org.multipaz.cose

import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborArray
import org.multipaz.cbor.CborMap
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.Simple
import org.multipaz.cbor.addCborMap
import org.multipaz.cbor.buildCborArray
import org.multipaz.cbor.buildCborMap

/**
 * COSE Signature message for a single signer.
 *
 * @param protectedHeaders protected headers.
 * @param unprotectedHeaders unprotected headers.
 * @param signature the signature.
 * @param payload the payload, if available.
 */
data class CoseSign1(
    val protectedHeaders: Map<CoseLabel, DataItem>,
    val unprotectedHeaders: Map<CoseLabel, DataItem>,
    val signature: ByteArray,
    val payload: ByteArray?
) {
    /**
     * Encodes the COSE_Sign1 as a CBOR data item.
     */
    fun toDataItem(): DataItem {
        val serializedProtectedHeaders =
            if (protectedHeaders.isNotEmpty()) {
                Cbor.encode(
                    buildCborMap {
                        protectedHeaders.forEach { (label, di) -> put(label.toDataItem(), di) }
                    }
                )
            } else {
                byteArrayOf()
            }

        return buildCborArray {
            add(serializedProtectedHeaders)
            addCborMap {
                unprotectedHeaders.forEach { (label, dataItem) -> put(label.toDataItem(), dataItem) }
            }
            if (payload != null) {
                add(Bstr(payload))
            } else {
                add(Simple.NULL)
            }
            add(signature)
        }
    }

    companion object {
        /**
         * Decodes CBOR data into a [CoseSign1]
         *
         * @param dataItem the data item.
         * @return a [CoseSign1].
         */
        fun fromDataItem(dataItem: DataItem): CoseSign1 {
            require(dataItem is CborArray)
            require(dataItem.items.size == 4)

            val unprotectedHeaders = mutableMapOf<CoseLabel, DataItem>()
            val uph = dataItem.items[1] as CborMap
            uph.items.forEach { (key, value) -> unprotectedHeaders.put(key.asCoseLabel, value) }

            val protectedHeaders = mutableMapOf<CoseLabel, DataItem>()
            val serializedProtectedHeaders = dataItem.items[0].asBstr
            if (serializedProtectedHeaders.isNotEmpty()) {
                val ph = Cbor.decode(serializedProtectedHeaders) as CborMap
                ph.items.forEach { (key, value) -> protectedHeaders[key.asCoseLabel] = value }
            }

            var payloadOrNil: ByteArray? = null
            if (dataItem.items[2] is Bstr) {
                payloadOrNil = dataItem.items[2].asBstr
            }

            val signature = dataItem.items[3].asBstr
            return CoseSign1(protectedHeaders, unprotectedHeaders, signature, payloadOrNil)
        }
    }
}
