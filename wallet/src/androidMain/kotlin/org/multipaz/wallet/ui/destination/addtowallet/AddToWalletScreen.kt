package org.multipaz_credential.wallet.ui.destination.addtowallet

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.multipaz.issuance.IssuingAuthorityConfiguration
import org.multipaz.issuance.remote.WalletServerProvider
import org.multipaz.util.Logger
import org.multipaz_credential.wallet.ProvisioningViewModel
import org.multipaz_credential.wallet.R
import org.multipaz_credential.wallet.WalletApplication
import org.multipaz_credential.wallet.credentialoffer.extractCredentialIssuerData
import org.multipaz_credential.wallet.navigation.WalletDestination
import org.multipaz_credential.wallet.ui.ScreenWithAppBarAndBackButton
import org.multipaz_credential.wallet.util.getUrlQueryFromCustomSchemeUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.multipaz.compose.qrcode.ScanQrCodeDialog

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
    provisioningViewModel: ProvisioningViewModel,
    onNavigate: (String) -> Unit,
    walletServerProvider: WalletServerProvider,
) {
    val loadingIssuerDisplayDatas = remember { mutableStateOf(true) }
    val loadingIssuerDisplayError = remember { mutableStateOf<Throwable?>(null) }
    val issuerDisplayDatas = remember { mutableStateListOf<IssuerDisplayData>() }
    // whether to show the ScanQrDialog composable
    val showQrScannerDialog = remember { mutableStateOf(false) }
    // force a navigation recomposition after processing the Qr code and onNavigate(route) does not work
    val navigateToOnComposable = remember { mutableStateOf<String?>(null) }
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

    // perform a navigateTo (hoisted navigation) from ScanQrDialog to run now after being requested
    // to navigate after scanning a Qr code
    navigateToOnComposable.value?.let { route -> onNavigate(route) }

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
                // compose ScanQrDialog when user taps on "Scan Credential Offer"
                if (showQrScannerDialog.value) {
                    ScanQrCodeDialog(
                        title = @Composable { Text(text = stringResource(R.string.credential_offer_scan)) },
                        text = @Composable { Text(
                            text = stringResource(id = R.string.credential_offer_details)
                        )},
                        onCodeScanned = { qrCodeTextUrl ->
                            // filter only for OID4VCI Url schemes.
                            if (qrCodeTextUrl.startsWith(WalletApplication.OID4VCI_CREDENTIAL_OFFER_URL_SCHEME)) {
                                // scanned text is expected to be an encoded Url
                                CoroutineScope(Dispatchers.Main).launch {
                                    val query = getUrlQueryFromCustomSchemeUrl(qrCodeTextUrl)
                                    // extract Credential Issuer Uri (issuing authority path) and credential id (pid-mso-mdoc, pid-sd-jwt)
                                    val offer = extractCredentialIssuerData(query)
                                    if (offer != null) {
                                        // initiate getting issuing authority dynamically from specified Issuer Uri and Credential Id
                                        provisioningViewModel.start(
                                            issuerIdentifier = null,
                                            openid4VciCredentialOffer = offer,
                                        )
                                        onNavigate(WalletDestination.ProvisionDocument.route)
                                    }
                                }
                            }
                            true
                        },
                        dismissButton = stringResource(R.string.reader_screen_scan_qr_dialog_dismiss_button),
                        onDismiss = { showQrScannerDialog.value = false },
                        modifier = Modifier
                            .fillMaxWidth()//0.9f)
                            .fillMaxHeight(0.6f),
                    )
                } else { // not showing [ScanQrDialog]
                    AddToWalletScreenWithIssuerDisplayDatas(
                        provisioningViewModel,
                        onNavigate,
                        issuerDisplayDatas,
                        onShowScanQrDialog = {
                            showQrScannerDialog.value = true
                        }
                    )
                }
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
    issuerDisplayDatas: SnapshotStateList<IssuerDisplayData>,
    onShowScanQrDialog: () -> Unit,
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            onClick = {
                provisioningViewModel.reset()
                provisioningViewModel.start(
                    issuerIdentifier = issuerDisplayData.configuration.identifier,
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
                    modifier = Modifier
                        .size(48.dp)
                        .padding(4.dp)
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

    // Scan Credential Offer
    FilledTonalButton(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(24.dp),
        onClick = onShowScanQrDialog
    ) {
        Row {
            Image(
                painter = painterResource(id = R.drawable.qr_icon),
                contentDescription = stringResource(
                    R.string.accessibility_artwork_for,
                    stringResource(id = R.string.credential_offer_scan)
                ),
                modifier = Modifier
                    .size(48.dp)
                    .padding(4.dp)
            )
            Column(
                modifier = Modifier.align(Alignment.CenterVertically)
            ) {
                Text(
                    text = stringResource(id = R.string.credential_offer_scan),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = stringResource(id = R.string.credential_offer_details),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}