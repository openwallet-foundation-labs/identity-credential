package org.multipaz.sdjwt.credential

import org.multipaz.claim.Claim
import org.multipaz.claim.VcClaim
import org.multipaz.credential.Credential
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.sdjwt.SdJwtVerifiableCredential

/**
 * A SD-JWT VC credential, according to [draft-ietf-oauth-sd-jwt-vc-03]
 * (https://datatracker.ietf.org/doc/draft-ietf-oauth-sd-jwt-vc/).
 *
 * An object that implements this interface must also be a [Credential]
 */
interface SdJwtVcCredential {
    /**
     * The Verifiable Credential Type - or `vct` - as defined in section 3.2.2.1.1 of
     * [draft-ietf-oauth-sd-jwt-vc-03]
     * (https://datatracker.ietf.org/doc/draft-ietf-oauth-sd-jwt-vc/)
     */
    val vct: String

    /**
     * The issuer-provided data associated with the credential, see [Credential.issuerProvidedData].
     *
     * This data must be the encoded string containing the SD-JWT VC. The SD-JWT VC itself is by
     * disclosures: `<header>.<body>.<signature>~<Disclosure 1>~<Disclosure 2>~...~<Disclosure N>~`
     */
    val issuerProvidedData: ByteArray

    fun getClaimsImpl(
        documentTypeRepository: DocumentTypeRepository?
    ): List<VcClaim> {
        val ret = mutableListOf<VcClaim>()
        val sdJwt = SdJwtVerifiableCredential.fromString(issuerProvidedData.decodeToString())
        val dt = documentTypeRepository?.getDocumentTypeForVc(vct)
        for (disclosure in sdJwt.disclosures) {
            val attribute = dt?.vcDocumentType?.claims?.get(disclosure.key)
            ret.add(
                VcClaim(
                    displayName = dt?.vcDocumentType?.claims?.get(disclosure.key)?.displayName ?: disclosure.key,
                    attribute = attribute,
                    claimName = disclosure.key,
                    value = disclosure.value
                )
            )
        }
        return ret
    }
}