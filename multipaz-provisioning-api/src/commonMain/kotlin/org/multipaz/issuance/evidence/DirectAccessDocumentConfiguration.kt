package org.multipaz.issuance.evidence

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.document.NameSpacedData

@CborSerializable
data class DirectAccessDocumentConfiguration(
    /**
     * The mdoc DocType.
     */
    val docType: String,

    /**
     * Static data for use with [DirectAccessCredential] instances.
     */
    val staticData: NameSpacedData,
)
