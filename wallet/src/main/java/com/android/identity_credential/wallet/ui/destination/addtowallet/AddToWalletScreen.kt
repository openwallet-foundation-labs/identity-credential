package com.android.identity_credential.wallet.ui.destination.addtowallet

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.identity.document.DocumentStore
import com.android.identity.issuance.IssuingAuthorityRepository
import com.android.identity_credential.wallet.CardViewModel
import com.android.identity_credential.wallet.ProvisioningViewModel
import com.android.identity_credential.wallet.R
import com.android.identity_credential.wallet.navigation.WalletDestination
import com.android.identity_credential.wallet.ui.ScreenWithAppBarAndBackButton

@Composable
fun AddToWalletScreen(
    cardViewModel: CardViewModel,
    provisioningViewModel: ProvisioningViewModel,
    onNavigate: (String) -> Unit,
    documentStore: DocumentStore,
    issuingAuthorityRepository: IssuingAuthorityRepository
) {
    ScreenWithAppBarAndBackButton(
        title = stringResource(R.string.add_screen_title),
        onBackButtonClick = { onNavigate(WalletDestination.PopBackStack.route) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                text = stringResource(R.string.add_screen_select_issuer)
            )
        }

        for (issuerDisplayData in cardViewModel.issuerDisplayData) {
            /*
            ListItem(
                headlineContent = { Text(issuerDisplayData.configuration.description) },
                supportingContent = { Text(issuerDisplayData.configuration.issuingAuthorityName) },
                leadingContent = {
                    Image(
                        bitmap = issuerDisplayData.issuerLogo.asImageBitmap(),
                        contentDescription = stringResource(
                            R.string.accessibility_artwork_for,
                            issuerDisplayData.configuration.issuingAuthorityName
                        ),
                        modifier = Modifier.height(48.dp)
                    )
                },

                modifier = Modifier
                    .clickable {
                        println("TODO")
                    }
            )
             */

            FilledTonalButton(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                shape = RoundedCornerShape(8.dp),
                onClick = {
                    provisioningViewModel.reset()
                    provisioningViewModel.start(
                        issuingAuthorityRepository = issuingAuthorityRepository,
                        documentStore = documentStore,
                        issuerIdentifier = issuerDisplayData.configuration.identifier
                    )
                    onNavigate(WalletDestination.ProvisionDocument.route)
                }) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        bitmap = issuerDisplayData.issuerLogo.asImageBitmap(),
                        contentDescription = stringResource(
                            R.string.accessibility_artwork_for,
                            issuerDisplayData.configuration.issuingAuthorityName
                        ),
                        modifier = Modifier.height(48.dp).padding(4.dp)
                    )
                    Column(
                        modifier = Modifier.align(Alignment.CenterVertically)
                    ) {
                        Text(
                            text = issuerDisplayData.configuration.description,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = issuerDisplayData.configuration.issuingAuthorityName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}