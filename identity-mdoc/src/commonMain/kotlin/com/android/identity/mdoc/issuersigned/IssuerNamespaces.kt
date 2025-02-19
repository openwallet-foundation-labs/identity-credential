package com.android.identity.mdoc.issuersigned

import com.android.identity.cbor.Bstr
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.CborMap
import com.android.identity.cbor.DataItem
import com.android.identity.cbor.Tagged
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.collections.set

/**
 * A data structure for representing `IssuerNameSpaces` in ISO/IEC 18013-5:2021.
 *
 * Use [fromDataItem] to parse CBOR and [toDataItem] to generate CBOR.
 *
 * @property data map from namespace name to a map from data element name to [IssuerSignedItem].
 */
data class IssuerNamespaces(
    val data: Map<String, Map<String, IssuerSignedItem>>
) {

    /**
     * Generate `IssuerNameSpaces` CBOR
     *
     * @return a [DataItem] for `IssuerNameSpaces` CBOR.
     */
    fun toDataItem(): DataItem {
        val builder = CborMap.builder()
        for ((namespaceName, innerMap) in data) {
            val array = CborArray.builder()
            for ((_, issuerSignedItem) in innerMap) {
                array.add(Tagged(Tagged.ENCODED_CBOR, Bstr(Cbor.encode(issuerSignedItem.toDataItem()))))
            }
            builder.put(namespaceName, array.end().build())
        }
        return builder.end().build()
    }

    companion object {

        /**
         * Parse `IssuerNameSpaces` CBOR.
         *
         * @param nameSpaces a [DataItem] for `IssuerNameSpaces` CBOR.
         * @return the parsed representation.
         */
        fun fromDataItem(nameSpaces: DataItem): IssuerNamespaces {
            val ret = mutableMapOf<String, MutableMap<String, IssuerSignedItem>>()
            for ((namespaceDataItemKey, namespaceDataItemValue) in nameSpaces.asMap) {
                val namespaceName = namespaceDataItemKey.asTstr
                val innerMap = mutableMapOf<String, IssuerSignedItem>()
                for (issuerSignedItemBytes in namespaceDataItemValue.asArray) {
                    val issuerSignedItem = IssuerSignedItem.fromDataItem(
                        issuerSignedItemBytes.asTaggedEncodedCbor
                    )
                    innerMap[issuerSignedItem.dataElementIdentifier] = issuerSignedItem
                }
                ret[namespaceName] = innerMap
            }
            return IssuerNamespaces(ret)
        }
    }
}