package com.android.identity.sdjwt.credential

import com.android.identity.cbor.CborBuilder
import com.android.identity.cbor.DataItem
import com.android.identity.cbor.MapBuilder
import com.android.identity.credential.Credential
import com.android.identity.document.Document

class KeylessSdJwtVcCredential : Credential, SdJwtVcCredential {
    override val vct: String

    /**
     * Constructs a new [KeyBoundSdJwtVcCredential].
     *
     * @param document the document to add the credential to.
     * @param asReplacementFor the credential this credential will replace, if not null
     * @param domain the domain of the credential
     * @param vct the Verifiable Credential Type.
     */
    constructor(
        document: Document,
        asReplacementFor: Credential?,
        domain: String,
        vct: String,
    ) : super(document, asReplacementFor, domain) {
        this.vct = vct
        // Only the leaf constructor should add the credential to the document.
        if (this::class == KeylessSdJwtVcCredential::class) {
            addToDocument()
        }
    }

    /**
     * Constructs a Credential from serialized data.
     *
     * @param document the [Document] that the credential belongs to.
     * @param dataItem the serialized data.
     */
    constructor(
        document: Document,
        dataItem: DataItem,
    ) : super(document, dataItem) {
        vct = dataItem["vct"].asTstr
    }

    override fun addSerializedData(builder: MapBuilder<CborBuilder>) {
        super.addSerializedData(builder)
        builder.put("vct", vct)
    }
}