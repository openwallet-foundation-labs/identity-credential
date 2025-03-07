package org.multipaz.issuance.evidence

import org.multipaz.cbor.annotation.CborSerializable

/**
 * Credential offer as described in OpenId4Vci spec:
 * https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html#name-credential-offer-parameters
 */
@CborSerializable
sealed class Openid4VciCredentialOffer(
    val issuerUri: String,
    val configurationId: String,
    val authorizationServer: String?
) {
    companion object
}

/**
 * Credential offer with Grant Type `urn:ietf:params:oauth:grant-type:pre-authorized_code`.
 */
class Openid4VciCredentialOfferPreauthorizedCode(
    issuerUri: String,
    configurationId: String,
    authorizationServer: String?,
    val preauthorizedCode: String,
    val txCode: Openid4VciTxCode?
) : Openid4VciCredentialOffer(issuerUri, configurationId, authorizationServer)

/**
 * Credential offer with Grant Type `authorization_code`.
 */
class Openid4VciCredentialOfferAuthorizationCode(
    issuerUri: String,
    configurationId: String,
    authorizationServer: String?,
    val issuerState: String?
) : Openid4VciCredentialOffer(issuerUri, configurationId, authorizationServer)


/**
 * Describes tx_code parameter (see OpenId4Vci spec referenced above).
 */
@CborSerializable
data class Openid4VciTxCode(
    val description: String,
    val isNumeric: Boolean,
    val length: Int
)
