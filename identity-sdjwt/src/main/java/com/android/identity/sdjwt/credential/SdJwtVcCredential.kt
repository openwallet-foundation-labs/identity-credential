package com.android.identity.sdjwt.credential

import com.android.identity.claim.Claim
import com.android.identity.credential.Credential
import com.android.identity.documenttype.DocumentTypeRepository

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
     * This data must be the bytes of TODO
     */
    val issuerProvidedData: ByteArray

    fun getClaims(
        documentTypeRepository: DocumentTypeRepository?
    ): List<Claim> {
        TODO("Not yet implemented")
    }
}