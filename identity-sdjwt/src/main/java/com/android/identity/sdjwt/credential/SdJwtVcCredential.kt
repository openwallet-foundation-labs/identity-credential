package com.android.identity.sdjwt.credential

import com.android.identity.cbor.CborBuilder
import com.android.identity.cbor.DataItem
import com.android.identity.cbor.MapBuilder
import com.android.identity.credential.Credential
import com.android.identity.credential.SecureAreaBoundCredential
import com.android.identity.document.Document
import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.SecureArea

/**
 * A SD-JWT VC credential, according to [draft-ietf-oauth-sd-jwt-vc-03]
 * (https://datatracker.ietf.org/doc/draft-ietf-oauth-sd-jwt-vc/).
 */
class SdJwtVcCredential : SecureAreaBoundCredential {
    companion object {
        private const val TAG = "SdJwtVcCredential"
    }

    /**
     * The Verifiable Credential Type - or `vct` - as defined in section 3.2.2.1.1 of
     * [draft-ietf-oauth-sd-jwt-vc-03]
     * (https://datatracker.ietf.org/doc/draft-ietf-oauth-sd-jwt-vc/)
     */
    val vct: String

    /**
     * Constructs a new [SdJwtVcCredential].
     *
     * @param document the document to add the credential to.
     * @param asReplacementFor the credential this credential will replace, if not null
     * @param domain the domain of the credential
     * @param secureArea the secure area for the authentication key associated with this credential.
     * @param createKeySettings the settings used to create new credentials.
     * @param vct the Verifiable Credential Type.
     */
    constructor(
        document: Document,
        asReplacementFor: Credential?,
        domain: String,
        secureArea: SecureArea,
        createKeySettings: CreateKeySettings,
        vct: String,
    ) : super(document, asReplacementFor, domain, secureArea, createKeySettings) {
        this.vct = vct
        // Only the leaf constructor should add the credential to the document.
        if (this::class == SdJwtVcCredential::class) {
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