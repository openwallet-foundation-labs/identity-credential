package org.multipaz.issuance

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.document.NameSpacedData

@CborSerializable
data class MdocDocumentConfiguration(
    /**
     * The mdoc DocType.
     */
    val docType: String,

    /**
     * Static data for use with [MdocCredential] instances.
     */
    val staticData: NameSpacedData,
)
