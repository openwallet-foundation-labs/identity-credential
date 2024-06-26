package com.android.identity.cose

import com.android.identity.cbor.Bstr
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.CborMap
import com.android.identity.cbor.DataItem
import com.android.identity.cbor.Simple

/**
 * COSE MACed Message.
 *
 * @param protectedHeaders protected headers.
 * @param unprotectedHeaders unprotected headers.
 * @param tag the MAC value.
 * @param payload the payload, if available.
 */
data class CoseMac0(
    val protectedHeaders: Map<CoseLabel, DataItem>,
    val unprotectedHeaders: Map<CoseLabel, DataItem>,
    val tag: ByteArray,
    val payload: ByteArray?
) {
    /**
     * Encodes the COSE_Mac0 as a CBOR data item.
     */
    fun toDataItem(): DataItem {
        val uphb = CborMap.builder()
        unprotectedHeaders.forEach { (label, dataItem) -> uphb.put(label.toDataItem(), dataItem) }

        val serializedProtectedHeaders =
            if (protectedHeaders.isNotEmpty()) {
                val phb = CborMap.builder()
                protectedHeaders.forEach { (label, di) -> phb.put(label.toDataItem(), di) }
                Cbor.encode(phb.end().build())
            } else {
                byteArrayOf()
            }
        val payloadOrNil =
            if (payload != null) {
                Bstr(payload)
            } else {
                Simple.NULL
            }
        return CborArray.builder()
            .add(serializedProtectedHeaders)
            .add(uphb.end().build())
            .add(payloadOrNil)
            .add(tag)
            .end().build()
    }

    companion object {
        /**
         * Decodes CBOR data into a [CoseMac0]
         *
         * @param dataItem the data item.
         * @return a [CoseMac0].
         */
        fun fromDataItem(dataItem: DataItem): CoseMac0 {
            require(dataItem is CborArray)
            require(dataItem.items.size == 4)

            val unprotectedHeaders = mutableMapOf<CoseLabel, DataItem>()
            val uph = dataItem.items[1] as CborMap
            uph.items.forEach { (key, value) -> unprotectedHeaders.put(key.asCoseLabel, value) }

            val protectedHeaders = mutableMapOf<CoseLabel, DataItem>()
            val serializedProtectedHeaders = dataItem.items[0].asBstr
            if (serializedProtectedHeaders.isEmpty()) {
                val ph = Cbor.decode(serializedProtectedHeaders) as CborMap
                ph.items.forEach { (key, value) -> protectedHeaders.put(key.asCoseLabel, value) }
            }

            var payloadOrNil: ByteArray? = null
            if (dataItem.items[2] is Bstr) {
                payloadOrNil = dataItem.items[2].asBstr
            }

            val tag = dataItem.items[3].asBstr
            return CoseMac0(protectedHeaders, unprotectedHeaders, tag, payloadOrNil)
        }
    }

}