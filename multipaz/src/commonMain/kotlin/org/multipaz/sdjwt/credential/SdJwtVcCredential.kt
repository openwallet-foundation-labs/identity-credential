package org.multipaz.sdjwt.credential

import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import org.multipaz.claim.JsonClaim
import org.multipaz.credential.Credential
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.sdjwt.SdJwt

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
    ): List<JsonClaim> {
        val ret = mutableListOf<JsonClaim>()
        val sdJwt = SdJwt(issuerProvidedData.decodeToString())
        val issuerKey = sdJwt.x5c!!.certificates.first().ecPublicKey
        val processedJwt = sdJwt.verify(issuerKey)

        // By design, we only include the top-level claims.
        val dt = documentTypeRepository?.getDocumentTypeForJson(vct)
        for ((claimName, claimValue) in processedJwt) {
            val attribute = dt?.jsonDocumentType?.claims?.get(claimName)
            ret.add(
                JsonClaim(
                    displayName = dt?.jsonDocumentType?.claims?.get(claimName)?.displayName ?: claimName,
                    attribute = attribute,
                    claimPath = buildJsonArray { add(claimName) },
                    value = claimValue
                )
            )
        }
        return ret
    }
}