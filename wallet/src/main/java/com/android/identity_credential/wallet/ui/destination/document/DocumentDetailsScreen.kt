package com.android.identity_credential.wallet.ui.destination.document

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.android.identity.util.Logger
import com.android.identity_credential.wallet.DocumentModel
import com.android.identity_credential.wallet.R
import com.android.identity_credential.wallet.navigation.WalletDestination
import com.android.identity_credential.wallet.ui.KeyValuePairText
import com.android.identity_credential.wallet.ui.ScreenWithAppBarAndBackButton


private const val TAG = "DocumentDetailsScreen"

@Composable
fun DocumentDetailsScreen(
    documentId: String,
    documentModel: DocumentModel,
    onNavigate: (String) -> Unit
) {
    val card = documentModel.getDocumentInfo(documentId)
    if (card == null) {
        Logger.w(TAG, "No document with id $documentId")
        onNavigate(WalletDestination.Main.route)
        return
    }


    Box(
        modifier = Modifier.fillMaxHeight()
    ) {

        ScreenWithAppBarAndBackButton(
            title = stringResource(R.string.document_details_screen_title),
            onBackButtonClick = { onNavigate(WalletDestination.PopBackStack.route) }
        ) {
            Column() {
                Text(
                    text = stringResource(R.string.document_details_screen_flash_pass_lecture),
                    textAlign = TextAlign.Center,
                    fontStyle = FontStyle.Italic,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )

                if (card.attributePortrait != null) {
                    Row(
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Image(
                            bitmap = card.attributePortrait.asImageBitmap(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .size(200.dp),
                            contentDescription = stringResource(R.string.accessibility_portrait),
                        )
                    }
                }

                for ((key, value) in card.attributes) {
                    KeyValuePairText(key, value)
                }

                if (card.attributeSignatureOrUsualMark != null) {
                    Row(
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Image(
                            bitmap = card.attributeSignatureOrUsualMark.asImageBitmap(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .size(75.dp),
                            contentDescription = stringResource(R.string.accessibility_signature),
                        )
                    }
                }
            }
        }

        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Text(
                text = stringResource(id = R.string.document_details_screen_disclaimer),
                textAlign = TextAlign.Center,
                lineHeight = 1.25.em,
                color = Color(red = 255, green = 128, blue = 128, alpha = 192),
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                style = TextStyle(
                    fontSize = 30.sp,
                    shadow = Shadow(color = Color.Black, offset = Offset(0f, 0f), blurRadius = 2f),
                ),
                modifier = Modifier.rotate(-30f)
            )
        }

    }
}
