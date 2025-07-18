package org.multipaz.mdoc.zkp

import kotlinx.datetime.Instant
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.addCborMap
import org.multipaz.cbor.buildCborMap
import org.multipaz.cbor.putCborArray
import org.multipaz.cbor.putCborMap
import org.multipaz.crypto.X509CertChain
import org.multipaz.util.Logger

/**
 * ZkDocumentData contains the data the proof will prove.
 *
 * @property zkSystemSpecId the ZK system spec Id from the verifier used to create the proof.
 * @property docType the doc type of doc being represented.
 * @property timestamp the timstamps the proof was generated at.
 * @property issuerSigned issuer signed name spaces and values.
 * @property deviceSigned devices signed name spaces and values.
 * @property msoX5chain the issuers certificate chain.
 */
data class ZkDocumentData (
    val zkSystemSpecId: String,
    val docType: String,
    val timestamp: Instant,
    val issuerSigned: Map<String, Map<String, DataItem>>,
    val deviceSigned: Map<String, Map<String, DataItem>>,
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
            putCborMap("issuerSigned") {
                issuerSigned.forEach { (namespaceName, dataElements) ->
                    putCborArray(namespaceName) {
                        dataElements.forEach { (dataElementName, dataElementValue) ->
                            addCborMap {
                                put("elementIdentifier", dataElementName)
                                put("elementValue", dataElementValue)
                            }
                        }
                    }
                }
            }
            putCborMap("deviceSigned") {
                deviceSigned.forEach { (namespaceName, dataElements) ->
                    putCborArray(namespaceName) {
                        dataElements.forEach { (dataElementName, dataElementValue) ->
                            addCborMap {
                                put("elementIdentifier", dataElementName)
                                put("elementValue", dataElementValue)
                            }
                        }
                    }
                }
            }
            if (msoX5chain != null) {
                put("msoX5chain", msoX5chain.toDataItem())
            }
        }
    }

    companion object {
        private const val TAG = "ZkDocumentData"

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

            val issuerSigned = mutableMapOf<String, Map<String, DataItem>>()
            dataItem.getOrNull("issuerSigned")?.asMap?.forEach { (nameSpaceItem, dateElementsItem) ->
                val dataElementsMap = mutableMapOf<String, DataItem>()
                dateElementsItem.asArray.forEach { zkSignedItem ->
                    dataElementsMap.put(zkSignedItem["elementIdentifier"].asTstr, zkSignedItem["elementValue"])
                }
                issuerSigned.put(nameSpaceItem.asTstr, dataElementsMap)
            }

            val deviceSigned = mutableMapOf<String, Map<String, DataItem>>()
            dataItem.getOrNull("deviceSigned")?.asMap?.forEach { (nameSpaceItem, dateElementsItem) ->
                val dataElementsMap = mutableMapOf<String, DataItem>()
                dateElementsItem.asArray.forEach { zkSignedItem ->
                    dataElementsMap.put(zkSignedItem["elementIdentifier"].asTstr, zkSignedItem["elementValue"])
                }
                deviceSigned.put(nameSpaceItem.asTstr, dataElementsMap)
            }

            val msoX5chain = dataItem.getOrNull("msoX5chain")?.asX509CertChain
            return ZkDocumentData(
                zkSystemSpecId = zkSystemSpecId,
                docType = docType,
                timestamp = Instant.parse(timestamp),
                issuerSigned = issuerSigned,
                deviceSigned = deviceSigned,
                msoX5chain = msoX5chain
            )
        }
    }
}
