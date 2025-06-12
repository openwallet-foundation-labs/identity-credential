package org.multipaz.mdoc.zkp

import kotlinx.datetime.Instant
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.DataItem
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
    companion object {
        /**
         * Creates a [ZkDocument] using the provided system spec and document details.
         *
         * @param zkSystemSpec The specification of the ZK system used.
         * @param docType The type identifier for the document being represented.
         * @param timestamp The timestamp indicating when the proof was generated.
         * @param issuerSignedItems The fields in the document that are signed by the issuer.
         * @param msoX5chain The certificate chain of the issuer (optional).
         * @param proof The ZK proof associated with the document.
         * @return A new instance of [ZkDocument].
         */
        fun create(
            zkSystemSpec: ZkSystemSpec,
            docType: String,
            timestamp: Instant,
            issuerSignedItems: List<DataItem>,
            msoX5chain: X509CertChain?,
            proof: ByteString
        ): ZkDocument {
            val zkDocumentBytes = ZkDocumentData(
                zkSystemSpec,
                docType,
                timestamp,
                issuerSignedItems,
                msoX5chain
            )

            return ZkDocument(
                zkDocumentData= zkDocumentBytes,
                proof=proof
            )
        }
    }
}
