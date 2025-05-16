package org.multipaz.zkp

import kotlinx.datetime.Instant
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.DataItem
import org.multipaz.crypto.X509CertChain

/**
 * ZkDocument a ZK proof representation of a document.
 *
 * @property zkSystemSpec the ZK system spec used to create the ZkDocument.
 * @property docType the doc type of doc being represented.
 * @property timestamp the timstampe the proof was generated at.
 * @property issuerSignedItems issuer signed document fields.
 * @property deviceSignedItems device signed document fields.
 * @property msoX5chain the issuers certificate chain.
 * @property proof the ZK proof that can be verified.
 */
data class ZkDocument(
    val zkSystemSpec: ZkSystemSpec,
    val docType: String,
    val timestamp: Instant,
    val issuerSignedItems: List<DataItem>,
    val deviceSignedItems: List<DataItem>,
    val msoX5chain: X509CertChain?,
    val proof: ByteString
)
