package org.multipaz.mdoc.zkp

import kotlinx.datetime.Instant
import org.multipaz.cbor.DataItem
import org.multipaz.crypto.X509CertChain

/**
 * ZkDocumentData contains the data the proof will prove.
 *
 * @property zkSystemSpec the ZK system spec used to create the ZkDocument.
 * @property docType the doc type of doc being represented.
 * @property timestamp the timstampe the proof was generated at.
 * @property issuerSignedItems issuer signed document fields.
 * @property deviceSignedItems devices signed document fields.
 * @property msoX5chain the issuers certificate chain.
 */
data class ZkDocumentData (
    val zkSystemSpec: ZkSystemSpec,
    val docType: String,
    val timestamp: Instant,
    val issuerSignedItems: List<DataItem>,
    val msoX5chain: X509CertChain?,
)
