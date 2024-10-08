package com.android.identity_credential.wallet.credentialoffer

import com.android.identity.document.DocumentStore
import com.android.identity.issuance.remote.WalletServerProvider
import com.android.identity_credential.wallet.ProvisioningViewModel
import com.android.identity_credential.wallet.SettingsModel
import com.android.identity_credential.wallet.navigation.WalletDestination
import org.json.JSONObject

/**
 * Initiate the process of setting a dynamic Credential Issuer defined in the credential offer
 * payload that produces [credentialIssuerUri] Url of Issuer for obtaining credentials and
 * [credentialIssuerConfigurationId] for the Credential Id (such as "pid-mso-mdoc" or "pid-sd-jwt").
 */
fun initiateCredentialOfferIssuance(
    walletServerProvider: WalletServerProvider,
    provisioningViewModel: ProvisioningViewModel,
    settingsModel: SettingsModel,
    documentStore: DocumentStore,
    onNavigate: (String) -> Unit,
    credentialIssuerUri: String,
    credentialIssuerConfigurationId: String,
) {
    provisioningViewModel.start(
        walletServerProvider = walletServerProvider,
        documentStore = documentStore,
        settingsModel = settingsModel,
        issuerIdentifier = null,
        credentialIssuerUri = credentialIssuerUri,
        credentialIssuerConfigurationId = credentialIssuerConfigurationId
    )
    onNavigate(WalletDestination.ProvisionDocument.route)
}

/**
 * Parse the Url Query component of an OID4VCI credential offer Url (from a deep link or Qr scan)
 * and return a [Pair] containing the Credential Issuer Uri and Credential (Config) Id that are
 * used for initiating the OID4VCI Credential Offer Issuance flow above [initiateCredentialOfferIssuance].
 */
fun extractCredentialIssuerData(urlQueryComponent: String): Pair<String, String> {
    val jsonPayload = JSONObject("{$urlQueryComponent}")
    val credentialOffer = jsonPayload.getJSONObject("credential_offer")
    // extract Credential Issuer Uri and Credential (Config) Id
    val credentialIssuerUri = credentialOffer.getString("credential_issuer")
    val credentialConfigurationIds =
        credentialOffer.getJSONArray("credential_configuration_ids")
    // TODO should there be a future implementation addressing all specified ids in credential_configuration_ids ?
    val credentialConfigurationId = credentialConfigurationIds.getString(0)
    return Pair(credentialIssuerUri, credentialConfigurationId)
}