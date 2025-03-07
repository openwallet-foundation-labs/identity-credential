package org.multipaz_credential.wallet.ui.destination.document

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.multipaz.issuance.CredentialFormat
import org.multipaz.util.Logger
import org.multipaz_credential.wallet.CredentialInfo
import org.multipaz_credential.wallet.DocumentInfo
import org.multipaz_credential.wallet.DocumentModel
import org.multipaz_credential.wallet.R
import org.multipaz_credential.wallet.navigation.WalletDestination
import org.multipaz_credential.wallet.ui.KeyValuePairText
import org.multipaz_credential.wallet.ui.ScreenWithAppBarAndBackButton
import org.multipaz_credential.wallet.util.asFormattedDateTimeInCurrentTimezone


private const val TAG = "CredentialInfoScreen"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CredentialInfoScreen(
    documentId: String,
    documentModel: DocumentModel,
    onNavigate: (String) -> Unit
) {
    val documentInfo = documentModel.getDocumentInfo(documentId)
    if (documentInfo == null) {
        Logger.w(TAG, "No document with id $documentId")
        onNavigate(WalletDestination.Main.route)
        return
    }


    Box(
        modifier = Modifier.fillMaxHeight()
    ) {

        val pagerState = rememberPagerState(pageCount = { documentInfo.credentialInfos.size })

        ScreenWithAppBarAndBackButton(
            title = stringResource(R.string.credential_info_screen_title),
            onBackButtonClick = { onNavigate(WalletDestination.PopBackStack.route) }
        ) {

            if (documentInfo.credentialInfos.size == 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        modifier = Modifier.padding(8.dp),
                        text = stringResource(R.string.credential_info_screen_no_credentials),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                }

            } else {
                Column {
                    HorizontalPager(
                        state = pagerState,
                    ) { page ->
                        CredentialInfo(
                            documentInfo.credentialInfos[page],
                            page,
                            documentInfo.credentialInfos.size
                        )
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .wrapContentHeight()
                .fillMaxWidth()
                .height(30.dp)
                .padding(8.dp),
        ) {
            repeat(pagerState.pageCount) { iteration ->
                val color =
                    if (pagerState.currentPage == iteration) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondary
                    }
                Box(
                    modifier = Modifier
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(color)
                        .size(8.dp)
                )
            }
        }

    }
}

@Composable
private fun CredentialInfo(credentialInfo: CredentialInfo,
                           credentialIndex: Int,
                           numCredentials: Int) {
    Column(Modifier.padding(8.dp)) {
        KeyValuePairText("Credential Number", "${credentialIndex + 1} of ${numCredentials}")
        KeyValuePairText("Description", credentialInfo.description)
        for ((key, value) in credentialInfo.details) {
            KeyValuePairText(key, value)
        }
        KeyValuePairText("Usage Count", "${credentialInfo.usageCount}")
        KeyValuePairText("Signed At",
            credentialInfo.signedAt?.asFormattedDateTimeInCurrentTimezone ?: "Not Set")
        KeyValuePairText("Valid From",
            credentialInfo.validFrom?.asFormattedDateTimeInCurrentTimezone ?: "Not Set")
        KeyValuePairText("Valid Until",
            credentialInfo.validUntil?.asFormattedDateTimeInCurrentTimezone ?: "Not Set")
        KeyValuePairText("Expected Update",
            credentialInfo.expectedUpdate?.asFormattedDateTimeInCurrentTimezone ?: "Not Set"
        )
    }
}
