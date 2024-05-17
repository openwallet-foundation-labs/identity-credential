package com.android.identity.issuance

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.document.NameSpacedData

@CborSerializable
data class SdJwtVcDocumentConfiguration(
    /**
     * The Verifiable Credential Type for SD-JWT VC credentials.
     */
    val vct: String,
)
