package com.android.identity_credential.wallet.ui.destination.credential

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.credential.CredentialStore
import com.android.identity.issuance.CredentialExtensions.credentialConfiguration
import com.android.identity.issuance.CredentialExtensions.issuingAuthorityIdentifier
import com.android.identity.issuance.CredentialExtensions.state
import com.android.identity.issuance.IssuingAuthorityRepository
import com.android.identity.util.Logger
import com.android.identity_credential.wallet.CredentialInformationViewModel
import com.android.identity_credential.wallet.R
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
    // TODO: should this be localized?
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
        title = stringResource(R.string.credential_title),
        onBackButtonClick = { onNavigate(WalletDestination.PopBackStack.route) }) {
        val state = credential.state
        TextField(credential.credentialConfiguration.displayName, {}, readOnly = true,
                label = {Text(stringResource(R.string.credential_label_name))}, modifier = Modifier.fillMaxWidth())
        TextField(issuer.configuration.name, {}, readOnly = true,
                label = {Text(stringResource(R.string.credential_label_issuer))}, modifier = Modifier.fillMaxWidth())
        // TODO: localize state.condition text
        TextField(state.condition.toString(), {}, readOnly = true,
                label = {Text(stringResource(R.string.credential_label_state))}, modifier = Modifier.fillMaxWidth())
        TextField(state.numAvailableCPO.toString(), {}, readOnly = true,
                label = {Text(stringResource(R.string.credential_label_cpo_pending))}, modifier = Modifier.fillMaxWidth())
        TextField(stateTimeString, {}, readOnly = true,
                label = {Text(stringResource(R.string.credential_label_last_refresh))}, modifier = Modifier.fillMaxWidth())
        Divider(thickness = 4.dp, color = MaterialTheme.colorScheme.primary)
        TextField(credential.pendingAuthenticationKeys.size.toString(), {}, readOnly = true,
                label = {Text(stringResource(R.string.credential_label_pending_auth_keys))}, modifier = Modifier.fillMaxWidth())
        TextField(credential.authenticationKeys.size.toString(), {}, readOnly = true,
                label = {Text(stringResource(R.string.credential_label_auth_keys))}, modifier = Modifier.fillMaxWidth())
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = {
                credentialInformationViewModel.housekeeping(
                    issuingAuthorityRepository = issuingAuthorityRepository,
                    androidKeystoreSecureArea = androidKeystoreSecureArea,
                    credential = credential
                )
            }) {
                Text(stringResource(R.string.credential_button_refresh))
            }
            Button(onClick = {
                onNavigate(WalletDestination.CredentialInfo.getRouteWithArguments(
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
                ))
            }) {
                Text(stringResource(R.string.credential_button_view))
            }
            Button(onClick = {
                // TODO: run issuer deletion flow
                credentialStore.deleteCredential(credentialId)
                onNavigate(WalletDestination.PopBackStack.route)
            }) {
                Text(stringResource(R.string.credential_button_delete))
            }
        }
    }
}