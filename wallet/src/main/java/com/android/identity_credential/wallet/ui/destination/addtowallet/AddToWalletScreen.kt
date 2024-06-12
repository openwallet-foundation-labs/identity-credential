package com.android.identity_credential.wallet.ui.destination.addtowallet

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.identity.document.DocumentStore
import com.android.identity.issuance.IssuingAuthorityConfiguration
import com.android.identity.issuance.remote.WalletServerProvider
import com.android.identity.util.Logger
import com.android.identity_credential.wallet.DocumentModel
import com.android.identity_credential.wallet.ProvisioningViewModel
import com.android.identity_credential.wallet.R
import com.android.identity_credential.wallet.SettingsModel
import com.android.identity_credential.wallet.navigation.WalletDestination
import com.android.identity_credential.wallet.ui.ScreenWithAppBarAndBackButton

private const val TAG = "AddToWalletScreen"

private data class IssuerDisplayData(
    val configuration: IssuingAuthorityConfiguration,
    val issuerLogo: Bitmap,
)

private suspend fun getIssuerDisplayDatas(
    walletServerProvider: WalletServerProvider
): List<IssuerDisplayData> {
    val ret = mutableListOf<IssuerDisplayData>()
    val walletServer = walletServerProvider.getWalletServer()
    for (configuration in walletServer.getIssuingAuthorityConfigurations()) {

        val options = BitmapFactory.Options()
        options.inMutable = true
        val bitmap = BitmapFactory.decodeByteArray(
            configuration.issuingAuthorityLogo,
            0,
            configuration.issuingAuthorityLogo.size,
            options
        )
        ret.add(IssuerDisplayData(configuration, bitmap))
    }
    return ret
}

@Composable
fun AddToWalletScreen(
    documentModel: DocumentModel,
    provisioningViewModel: ProvisioningViewModel,
    onNavigate: (String) -> Unit,
    documentStore: DocumentStore,
    walletServerProvider: WalletServerProvider,
    settingsModel: SettingsModel,
) {
    val loadingIssuerDisplayDatas = remember { mutableStateOf(true) }
    val loadingIssuerDisplayError = remember { mutableStateOf<Throwable?>(null) }
    val issuerDisplayDatas = remember { mutableStateListOf<IssuerDisplayData>() }
    LaunchedEffect(Unit) {
        issuerDisplayDatas.clear()
        try {
            for (data in getIssuerDisplayDatas(walletServerProvider)) {
                issuerDisplayDatas.add(data)
            }
        } catch (e: Throwable) {
            loadingIssuerDisplayError.value = e
        }
        loadingIssuerDisplayDatas.value = false
    }


    ScreenWithAppBarAndBackButton(
        title = stringResource(R.string.add_screen_title),
        onBackButtonClick = { onNavigate(WalletDestination.PopBackStack.route) }
    ) {

        if (loadingIssuerDisplayDatas.value) {
            AddToWalletScreenLoading()
        } else {
            if (loadingIssuerDisplayError.value != null) {
                Logger.e(TAG, "Error loading issuers", loadingIssuerDisplayError.value!!)
                AddToWalletScreenLoadingError(loadingIssuerDisplayError.value!!)
            } else {
                AddToWalletScreenWithIssuerDisplayDatas(
                    provisioningViewModel,
                    onNavigate,
                    documentStore,
                    walletServerProvider,
                    settingsModel,
                    issuerDisplayDatas,
                )
            }
        }

    }
}

@Composable
private fun AddToWalletScreenLoadingError(error: Throwable) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            modifier = Modifier.padding(8.dp),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            text = stringResource(R.string.add_screen_loading_error)
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            modifier = Modifier.padding(8.dp),
            text = error.message ?: "",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start
        )
    }
}

@Composable
private fun AddToWalletScreenLoading() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            modifier = Modifier.padding(8.dp),
            text = stringResource(R.string.add_screen_loading),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AddToWalletScreenWithIssuerDisplayDatas(
    provisioningViewModel: ProvisioningViewModel,
    onNavigate: (String) -> Unit,
    documentStore: DocumentStore,
    walletServerProvider: WalletServerProvider,
    settingsModel: SettingsModel,
    issuerDisplayDatas: SnapshotStateList<IssuerDisplayData>,
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

    for (issuerDisplayData in issuerDisplayDatas) {
        FilledTonalButton(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            shape = RoundedCornerShape(8.dp),
            onClick = {
                provisioningViewModel.reset()
                provisioningViewModel.start(
                    walletServerProvider = walletServerProvider,
                    documentStore = documentStore,
                    issuerIdentifier = issuerDisplayData.configuration.identifier,
                    settingsModel = settingsModel,
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
                    modifier = Modifier.size(48.dp).padding(4.dp)
                )
                Column(
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) {
                    Text(
                        text = issuerDisplayData.configuration.issuingAuthorityDescription,
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