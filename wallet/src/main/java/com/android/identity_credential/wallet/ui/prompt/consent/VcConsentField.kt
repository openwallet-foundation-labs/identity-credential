package com.android.identity_credential.wallet.ui.prompt.consent

import com.android.identity.documenttype.DocumentAttribute
import com.android.identity.documenttype.DocumentTypeRepository
import com.android.identity.sdjwt.SdJwtVerifiableCredential
import com.android.identity.sdjwt.credential.SdJwtVcCredential
import com.android.identity.util.Logger

/**
 * Consent field for VC credentials.
 *
 * @param displayName the name to display in the consent prompt.
 * @param claimName the claim name.
 * @param attribute a [DocumentAttribute], if the claim is well-known.
 */
data class VcConsentField(
    override val displayName: String,
    override val attribute: DocumentAttribute?,
    val claimName: String
) : ConsentField(displayName, attribute) {

    companion object {

        /**
         * Helper function to generate a list of entries for the consent prompt for VCs.
         *
         * @param vct the Verifiable Credential Type.
         * @param claims the list of claims.
         * @param documentTypeRepository a [DocumentTypeRepository] used to determine the display name.
         * @param vcCredential if set, the returned list is filtered so it only references claims
         * available in the credential.
         */
        fun generateConsentFields(
            vct: String,
            claims: List<String>,
            documentTypeRepository: DocumentTypeRepository,
            vcCredential: SdJwtVcCredential?,
        ): List<VcConsentField> {
            val vcType = documentTypeRepository.getDocumentTypeForVc(vct)?.vcDocumentType
            val ret = mutableListOf<VcConsentField>()
            for (claimName in claims) {
                val attribute = vcType?.claims?.get(claimName)
                ret.add(
                    VcConsentField(
                        attribute?.displayName ?: claimName,
                        attribute,
                        claimName
                    )
                )
            }
            return filterConsentFields(ret, vcCredential)
        }

        private fun filterConsentFields(
            list: List<VcConsentField>,
            credential: SdJwtVcCredential?
        ): List<VcConsentField> {
            if (credential == null) {
                return list
            }
            val sdJwt = SdJwtVerifiableCredential.fromString(
                String(credential.issuerProvidedData, Charsets.US_ASCII))

            val availableClaims = mutableSetOf<String>()
            for (disclosure in sdJwt.disclosures) {
                availableClaims.add(disclosure.key)
            }
            return list.filter { vcConsentField ->
                availableClaims.contains(vcConsentField.claimName)
            }
        }

    }
}