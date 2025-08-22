package org.multipaz.legacyprovisioning.evidence

import org.multipaz.cbor.annotation.CborSerializable

/**
 * Credential offer as described in OpenId4Vci spec:
 * https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html#name-credential-offer-parameters
 */
@CborSerializable
sealed class Openid4VciCredentialOffer {
    abstract val issuerUri: String
    abstract val configurationId: String
    abstract val authorizationServer: String?

    companion object
}

/**
 * Credential offer that does not contain grants.
 */
data class Openid4VciCredentialOfferGrantless(
    override val issuerUri: String,
    override val configurationId: String,
): Openid4VciCredentialOffer() {
    override val authorizationServer: String? get() = null
}

/**
 * Credential offer with Grant Type `urn:ietf:params:oauth:grant-type:pre-authorized_code`.
 */
data class Openid4VciCredentialOfferPreauthorizedCode(
    override val issuerUri: String,
    override val configurationId: String,
    override val authorizationServer: String?,
    val preauthorizedCode: String,
    val txCode: Openid4VciTxCode?
) : Openid4VciCredentialOffer()

/**
 * Credential offer with Grant Type `authorization_code`.
 */
data class Openid4VciCredentialOfferAuthorizationCode(
    override val issuerUri: String,
    override val configurationId: String,
    override val authorizationServer: String?,
    val issuerState: String?
) : Openid4VciCredentialOffer()


/**
 * Describes tx_code parameter (see OpenId4Vci spec referenced above).
 */
@CborSerializable
data class Openid4VciTxCode(
    val description: String,
    val isNumeric: Boolean,
    val length: Int
)
