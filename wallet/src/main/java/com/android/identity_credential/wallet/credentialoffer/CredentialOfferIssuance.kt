package com.android.identity_credential.wallet.credentialoffer

import com.android.identity.issuance.evidence.Openid4VciCredentialOffer
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
): Openid4VciCredentialOffer? {
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
        return Openid4VciCredentialOffer.parse(json)
    } catch (err: Exception) {
        Logger.e("CredentialOffer", "Parsing error", err)
        return null
    }
}