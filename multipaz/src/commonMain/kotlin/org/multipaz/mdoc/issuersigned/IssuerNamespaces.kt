package org.multipaz.mdoc.issuersigned

import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborArray
import org.multipaz.cbor.CborMap
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.Tagged
import org.multipaz.cbor.buildCborMap
import org.multipaz.cbor.putCborArray
import org.multipaz.request.MdocRequestedClaim
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
        return buildCborMap {
            for ((namespaceName, innerMap) in data) {
                putCborArray(namespaceName) {
                    for ((_, issuerSignedItem) in innerMap) {
                        add(Tagged(
                            Tagged.ENCODED_CBOR,
                            Bstr(Cbor.encode(issuerSignedItem.toDataItem()))
                        ))
                    }
                }
            }
        }
    }

    /**
     * Returns a new object filtering the [IssuerSignedItem] so they match a request.
     *
     * @param requestedClaims the list of data elements to request.
     * @return a new object containing the [IssuerSignedItem] present that are also requested in [requestedClaims].
     */
    fun filter(requestedClaims: List<MdocRequestedClaim>): IssuerNamespaces {
        val ret = mutableMapOf<String, MutableMap<String, IssuerSignedItem>>()
        for (claim in requestedClaims) {
            val issuerSignedItem = data[claim.namespaceName]?.get(claim.dataElementName)
            if (issuerSignedItem != null) {
                val innerMap = ret.getOrPut(claim.namespaceName, { mutableMapOf<String, IssuerSignedItem>() })
                innerMap.put(claim.dataElementName, issuerSignedItem)
            }
        }
        return IssuerNamespaces(ret)
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