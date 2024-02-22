package com.android.identity_credential.wallet.ui.destination.credential

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.credential.CredentialStore
import com.android.identity.issuance.CredentialExtensions.credentialConfiguration
import com.android.identity.issuance.CredentialExtensions.issuingAuthorityIdentifier
import com.android.identity.issuance.CredentialExtensions.state
import com.android.identity.issuance.IssuingAuthorityRepository
import com.android.identity.util.Logger
import com.android.identity_credential.wallet.CredentialInformationViewModel
import com.android.identity_credential.wallet.navigation.WalletDestination
import com.android.identity_credential.wallet.ui.ScreenWithAppBarAndBackButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone


const val TAG_CIS = "CredentialInfoScreen"

@Composable
fun CredentialInfoScreen(
    credentialId: String,
    onNavigate: (String) -> Unit,
    credentialStore: CredentialStore,
    issuingAuthorityRepository: IssuingAuthorityRepository,
    androidKeystoreSecureArea: AndroidKeystoreSecureArea,
    credentialInformationViewModel: CredentialInformationViewModel
) {
    val credential = credentialStore.lookupCredential(credentialId)
    if (credential == null) {
        Logger.w(TAG_CIS, "No credential for $credentialId")
        return
    }
    Logger.d(TAG_CIS, "issuer ${credential.issuingAuthorityIdentifier}")
    val issuer =
        issuingAuthorityRepository.lookupIssuingAuthority(credential.issuingAuthorityIdentifier)
    if (issuer == null) {
        Logger.w(
            TAG_CIS,
            "No IssuingAuthority for ${credential.issuingAuthorityIdentifier}"
        )
        return
    }
    val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ssXXX", Locale.US)
    df.timeZone = TimeZone.getTimeZone("UTC")
    val stateTimeString = df.format(Date(credential.state.timestamp))

    // TODO: this is needed to make the UI update when _lastRefreshedAt_ is
    //  updated. Figure out a way to do this without logging.
    Logger.d(
        TAG_CIS,
        "Last refresh in UI at ${credentialInformationViewModel.lastHousekeepingAt.value}"
    )

    ScreenWithAppBarAndBackButton(
        title = "Credential Information",
        onBackButtonClick = { onNavigate(WalletDestination.PopBackStack.route) }) {
        Text("Name: ${credential.credentialConfiguration.displayName}")
        Text("Issuer: ${issuer.configuration.name}")
        val state = credential.state
        Text("State: ${state.condition}")
        Text("CPO pending: ${state.numAvailableCPO}")
        Text("State Last Refresh: $stateTimeString")
        Divider()
        Text("Num PendingAuthKey: ${credential.pendingAuthenticationKeys.size}")
        Text("Num AuthKey: ${credential.authenticationKeys.size}")
        Divider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = {
                credentialInformationViewModel.housekeeping(
                    issuingAuthorityRepository = issuingAuthorityRepository,
                    androidKeystoreSecureArea = androidKeystoreSecureArea,
                    credential = credential
                )
            }) {
                Text("Refresh")
            }
            Button(onClick = {
                WalletDestination.CredentialInfo.getRouteWithArguments(
                    listOf(
                        Pair(
                            WalletDestination.CredentialInfo.Argument.CREDENTIAL_ID,
                            credentialId
                        ),
                        Pair(
                            WalletDestination.CredentialInfo.Argument.SECTION,
                            "details"
                        )
                    )
                )

            }) {
                Text("View")
            }
            Button(onClick = {
                // TODO: run issuer deletion flow
                credentialStore.deleteCredential(credentialId)
                onNavigate(WalletDestination.PopBackStack.route)
            }) {
                Text("Delete")
            }
        }
    }
}