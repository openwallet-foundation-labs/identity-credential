package com.android.identity.issuance

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.document.NameSpacedData

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
