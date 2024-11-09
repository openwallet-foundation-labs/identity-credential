package com.android.identity_credential.wallet.credentialoffer

import com.android.identity.util.Logger
import com.android.identity_credential.wallet.ProvisioningViewModel
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLDecoder

/**
 * Parse the Url Query component of an OID4VCI credential offer Url (from a deep link or Qr scan)
 * and return a [ProvisioningViewModel.Openid4VciCredentialOffer] containing the
 * Credential Issuer Uri and Credential (Config) Id that are used for initiating the
 * OpenId4VCI Credential Offer Issuance flow using [ProvisioningViewModel.start] call.
 */
suspend fun extractCredentialIssuerData(
    urlQueryComponent: String
): ProvisioningViewModel.Openid4VciCredentialOffer? {
    try {
        val params = urlQueryComponent.split('&').map {
            val index = it.indexOf('=')
            val name = if (index < 0) "" else it.substring(0, index)
            Pair(
                URLDecoder.decode(name, "UTF-8"),
                URLDecoder.decode(it.substring(index + 1), "UTF-8")
            )
        }.toMap()
        var credentialOfferString = params["credential_offer"]
        if (credentialOfferString == null) {
            val url = params["credential_offer_uri"]
            if (url == null) {
                Logger.e("CredentialOffer", "Could not parse offer")
                return null
            }
            val response = HttpClient().get(url) {}
            if (response.status != HttpStatusCode.OK) {
                Logger.e("CredentialOffer", "Error retrieving '$url'")
                return null
            }
            credentialOfferString = String(response.readBytes())
        }
        val json = Json.parseToJsonElement(credentialOfferString).jsonObject
        // extract Credential Issuer Uri and Credential (Config) Id
        val credentialIssuerUri = json["credential_issuer"]!!.jsonPrimitive.content
        val credentialConfigurationIds = json["credential_configuration_ids"]!!.jsonArray
        // Right now only use the first configuration id
        val credentialConfigurationId = credentialConfigurationIds[0].jsonPrimitive.content
        var preauthorizedCode: String? = null
        if (json.containsKey("grants")) {
            val codeObject = json["grants"]!!.jsonObject["urn:ietf:params:oauth:grant-type:pre-authorized_code"]
            if (codeObject != null) {
                preauthorizedCode = codeObject.jsonObject["pre-authorized_code"]?.jsonPrimitive?.content
            }
        }
        return ProvisioningViewModel.Openid4VciCredentialOffer(
            credentialIssuerUri,
            credentialConfigurationId,
            preauthorizedCode
        )
    } catch (err: Exception) {
        Logger.e("CredentialOffer", "Parsing error", err)
        return null
    }
}