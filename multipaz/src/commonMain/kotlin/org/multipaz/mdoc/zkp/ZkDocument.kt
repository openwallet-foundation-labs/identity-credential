package org.multipaz.mdoc.zkp

import kotlin.time.Instant
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.buildCborMap
import org.multipaz.cbor.putCborArray
import org.multipaz.cbor.toDataItem
import org.multipaz.crypto.X509CertChain

/**
 * Represents a document that contains a zero-knowledge (ZK) proof.
 *
 * @property zkDocumentData The structured data of the document.
 * @property proof The ZK proof that attests to the integrity and validity of the document data.
 */
data class ZkDocument(
    val zkDocumentData: ZkDocumentData,
    val proof: ByteString
) {
    /**
     * Converts this ZkDocument instance to a CBOR DataItem representation.
     *
     * The resulting DataItem will be a CBOR map containing two entries:
     * - "proof": The proof as a CBOR byte string
     * - "zkDocumentData": The document data serialized to its CBOR representation
     *
     * @return A DataItem representing this ZkDocument in CBOR format
     */
    fun toDataItem(): DataItem {
        return buildCborMap {
            put("proof", proof.toByteArray().toDataItem())
            put("zkDocumentData", zkDocumentData.toDataItem())
        }
    }

    companion object {
        /**
         * Creates a ZkDocument instance from a CBOR DataItem.
         *
         * This deserializes a CBOR representation back into a ZkDocument object.
         * It expects the DataItem to be a CBOR map with the following required fields:
         * - "proof": A CBOR byte string containing the ZK proof
         * - "zkDocumentData": A CBOR structure that can be deserialized into ZkDocumentData
         *
         * @param dataItem The CBOR DataItem to deserialize
         * @return A new ZkDocument instance
         * @throws IllegalArgumentException if required fields are missing or have invalid types
         */
        fun fromDataItem(dataItem: DataItem): ZkDocument {
            val proof = dataItem.getOrNull("proof")?.asBstr
                ?: throw IllegalArgumentException("Missing or invalid 'proof' field parsing ZkDocument.")
            val zkDocumentDataDataItem = dataItem.getOrNull("zkDocumentData")
                ?: throw IllegalArgumentException("Missing or invalid 'zkDocumentData' field parsing ZkDocument.")
            return ZkDocument(
                zkDocumentData = ZkDocumentData.fromDataItem(zkDocumentDataDataItem),
                proof = ByteString(proof)
            )
        }
    }
}


