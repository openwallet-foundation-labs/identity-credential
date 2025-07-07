package org.multipaz.mdoc.zkp

import kotlinx.datetime.Instant
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.buildCborMap
import org.multipaz.cbor.putCborArray
import org.multipaz.cbor.toDataItem
import org.multipaz.crypto.X509CertChain

/**
 * ZkDocumentData contains the data the proof will prove.
 *
 * @property zkSystemSpecId the ZK system spec Id from the verifier used to create the proof.
 * @property docType the doc type of doc being represented.
 * @property timestamp the timstampe the proof was generated at.
 * @property issuerSignedItems issuer signed document fields.
 * @property deviceSignedItems devices signed document fields.
 * @property msoX5chain the issuers certificate chain.
 */
data class ZkDocumentData (
    val zkSystemSpecId: String,
    val docType: String,
    val timestamp: Instant,
    val issuerSignedItems: List<DataItem>,
    val deviceSignedItems: List<DataItem>,
    val msoX5chain: X509CertChain?,
) {
    /**
     * Converts this ZkDocumentData instance to a CBOR DataItem representation.
     *
     * The resulting DataItem will be a CBOR map containing:
     * - "id": The ZK system specification identifier as a text string
     * - "docType": The document type as a text string
     * - "timestamp": The timestamp as an ISO-8601 formatted string
     * - "issuerSignedItems": An array of issuer-signed data items
     * - "deviceSignedItems": An array of device-signed data items
     * - "msoX5chain": The X.509 certificate chain (only included if non-null)
     *
     * @return A DataItem representing this ZkDocumentData in CBOR format
     */
    fun toDataItem(): DataItem {
        return buildCborMap {
            put("id", zkSystemSpecId)
            put("docType", docType)
            put("timestamp", timestamp.toString())
            putCborArray("issuerSignedItems") {
                issuerSignedItems.forEach{ add(it) }
            }
            putCborArray("deviceSignedItems") {
                deviceSignedItems.forEach{ add(it) }
            }

            if (msoX5chain != null) {
                put("msoX5chain", msoX5chain.toDataItem())
            }
        }
    }

    companion object {
        /**
         * Creates a ZkDocumentData instance from a CBOR DataItem.
         *
         * This factory method deserializes a CBOR representation back into a ZkDocumentData object.
         * It validates and extracts all required fields from the CBOR map structure.
         *
         * Expected CBOR structure:
         * - "id": Required text string for the ZK system specification ID
         * - "docType": Required text string for the document type
         * - "timestamp": Required text string in ISO-8601 format
         * - "issuerSignedItems": Optional array of DataItems (defaults to empty list)
         * - "deviceSignedItems": Optional array of DataItems (defaults to empty list)
         * - "msoX5chain": Required X.509 certificate chain
         *
         * @param dataItem The CBOR DataItem to deserialize
         * @return A new ZkDocumentData instance
         * @throws IllegalArgumentException if required fields are missing or have invalid types
         */
        fun fromDataItem(dataItem: DataItem): ZkDocumentData {
            val zkSystemSpecId = dataItem.getOrNull("id")?.asTstr
                ?: throw IllegalArgumentException("Missing or invalid 'id' field parsing ZkDocumentData.")
            val docType = dataItem.getOrNull("docType")?.asTstr
                ?: throw IllegalArgumentException("Missing or invalid 'docType' field parsing ZkDocumentData.")
            val timestamp = dataItem.getOrNull("timestamp")?.asTstr
                ?: throw IllegalArgumentException("Missing or invalid 'timestamp' field parsing ZkDocumentData.")
            val issuerSignedItems = dataItem.getOrNull("issuerSignedItems")?.asArray ?: listOf()
            val deviceSignedItems = dataItem.getOrNull("deviceSignedItems")?.asArray ?: listOf()
            val msoX5chain = dataItem.getOrNull("msoX5chain")?.asX509CertChain
            return ZkDocumentData(
                zkSystemSpecId,
                docType,
                timestamp = Instant.parse(timestamp),
                issuerSignedItems,
                deviceSignedItems,
                msoX5chain
            )
        }
    }
}
