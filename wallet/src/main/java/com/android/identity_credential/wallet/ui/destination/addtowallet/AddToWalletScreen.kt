package com.android.identity_credential.wallet.ui.destination.addtowallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.identity.credential.CredentialStore
import com.android.identity.issuance.IssuingAuthorityRepository
import com.android.identity_credential.wallet.ProvisioningViewModel
import com.android.identity_credential.wallet.ScreenWithAppBarAndBackButton
import com.android.identity_credential.wallet.navigation.WalletDestination

@Composable
fun AddToWalletScreen(
    provisioningViewModel: ProvisioningViewModel,
    onNavigate: (String) -> Unit,
    issuingAuthorityRepository: IssuingAuthorityRepository,
    credentialStore: CredentialStore
) {
    ScreenWithAppBarAndBackButton(title = "Add to Wallet", onNavigate = onNavigate) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                modifier = Modifier.padding(8.dp),
                text = "Select the issuer for provisioning."
            )
        }

        for (issuer in issuingAuthorityRepository.getIssuingAuthorities()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(onClick = {
                    provisioningViewModel.reset()
                    provisioningViewModel.start(
                        issuingAuthorityRepository = issuingAuthorityRepository,
                        credentialStore = credentialStore,
                        issuer = issuer
                    )
                    onNavigate(WalletDestination.ProvisionCredential.route)
                }) {
                    Text(issuer.configuration.name)
                }
            }
        }
    }
}