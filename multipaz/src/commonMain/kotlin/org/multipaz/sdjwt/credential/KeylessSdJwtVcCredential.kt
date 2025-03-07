package org.multipaz.sdjwt.credential

import org.multipaz.cbor.CborBuilder
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.MapBuilder
import org.multipaz.claim.Claim
import org.multipaz.claim.VcClaim
import org.multipaz.credential.Credential
import org.multipaz.document.Document
import org.multipaz.documenttype.DocumentTypeRepository

class KeylessSdJwtVcCredential : Credential, SdJwtVcCredential {
    override lateinit var vct: String
        private set

    /**
     * Constructs a new [KeyBoundSdJwtVcCredential].
     *
     * @param document the document to add the credential to.
     * @param asReplacementFor the credential this credential will replace, if not null
     * @param domain the domain of the credential
     * @param vct the Verifiable Credential Type.
     */
    private constructor(
        document: Document,
        asReplacementForIdentifier: String?,
        domain: String,
        vct: String,
    ) : super(document, asReplacementForIdentifier, domain) {
        this.vct = vct
        // Only the leaf constructors for keyless credentials should add the credential to
        // the document.
        check (this::class == KeylessSdJwtVcCredential::class)
    }

    /**
     * Constructs a Credential from serialized data.
     *
     * [deserialize] providing actual serialized data must be called before using this object.
     *
     * @param document the [Document] that the credential belongs to.
     */
    constructor(document: Document) : super(document)

    override suspend fun deserialize(dataItem: DataItem) {
        vct = dataItem["vct"].asTstr
    }

    override fun addSerializedData(builder: MapBuilder<CborBuilder>) {
        super.addSerializedData(builder)
        builder.put("vct", vct)
    }

    companion object {
        suspend fun create(
            document: Document,
            asReplacementForIdentifier: String?,
            domain: String,
            vct: String
        ): KeylessSdJwtVcCredential {
            return KeylessSdJwtVcCredential(
                document,
                asReplacementForIdentifier,
                domain,
                vct
            ).apply {
                addToDocument()
            }
        }
    }

    override fun getClaims(documentTypeRepository: DocumentTypeRepository?): List<VcClaim> {
        return getClaimsImpl(documentTypeRepository)
    }
}