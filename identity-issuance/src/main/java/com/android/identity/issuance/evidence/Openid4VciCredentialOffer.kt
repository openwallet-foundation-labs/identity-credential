package com.android.identity.issuance.evidence

import com.android.identity.cbor.annotation.CborSerializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@CborSerializable
sealed class Openid4VciCredentialOffer(
    val issuerUri: String,
    val configurationId: String,
    val authorizationServer: String?
) {
    companion object {
        fun parse(json: JsonObject): Openid4VciCredentialOffer {
            val credentialIssuerUri = json["credential_issuer"]!!.jsonPrimitive.content
            val credentialConfigurationIds = json["credential_configuration_ids"]!!.jsonArray
            // Right now only use the first configuration id
            val credentialConfigurationId = credentialConfigurationIds[0].jsonPrimitive.content
            if (json.containsKey("grants")) {
                val grants = json["grants"]!!.jsonObject
                val preAuthGrant = grants["urn:ietf:params:oauth:grant-type:pre-authorized_code"]
                if (preAuthGrant != null) {
                    val grant = preAuthGrant.jsonObject
                    val preauthorizedCode = grant["pre-authorized_code"]!!.jsonPrimitive.content
                    val authorizationServer = grant["authorization_server"]?.jsonPrimitive?.content
                    val txCode = extractTxCode(grant["tx_code"])
                    return Openid4VciCredentialOfferPreauthorizedCode(
                        issuerUri = credentialIssuerUri,
                        configurationId = credentialConfigurationId,
                        authorizationServer = authorizationServer,
                        preauthorizedCode = preauthorizedCode,
                        txCode = txCode
                    )
                } else {
                    val grant = grants["authorization_code"]?.jsonObject
                    if (grant != null) {
                        val issuerState = grant["issuer_state"]?.jsonPrimitive?.content
                        val authorizationServer = grant["authorization_server"]?.jsonPrimitive?.content
                        return Openid4VciCredentialOfferAuthorizationCode(
                            issuerUri = credentialIssuerUri,
                            configurationId = credentialConfigurationId,
                            authorizationServer = authorizationServer,
                            issuerState = issuerState
                        )
                    }
                }
            }
            throw IllegalArgumentException("Could not parse credential offer")
        }
    }
}

class Openid4VciCredentialOfferPreauthorizedCode(
    issuerUri: String,
    configurationId: String,
    authorizationServer: String?,
    val preauthorizedCode: String,
    val txCode: Openid4VciTxCode?
) : Openid4VciCredentialOffer(issuerUri, configurationId, authorizationServer)

class Openid4VciCredentialOfferAuthorizationCode(
    issuerUri: String,
    configurationId: String,
    authorizationServer: String?,
    val issuerState: String?
) : Openid4VciCredentialOffer(issuerUri, configurationId, authorizationServer)

@CborSerializable
data class Openid4VciTxCode(
    val message: String,
    val numeric: Boolean,
    val length: Int
)

private fun extractTxCode(txCodeJson: JsonElement?): Openid4VciTxCode? {
    return if (txCodeJson == null) {
        null
    } else {
        val obj = txCodeJson.jsonObject
        Openid4VciTxCode(
            message = obj["description"]?.jsonPrimitive?.content ?:
            "Enter transaction code that was previously communicated to you",
            length = obj["length"]?.jsonPrimitive?.intOrNull ?: Int.MAX_VALUE,
            numeric = obj["input_mode"]?.jsonPrimitive?.content != "text"
        )
    }
}
