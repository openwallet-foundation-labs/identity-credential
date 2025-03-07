package org.multipaz_credential.wallet.ui.destination.document

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import org.multipaz.securearea.UserAuthenticationType
import org.multipaz.util.Logger
import org.multipaz_credential.wallet.AttributeDisplayInfoHtml
import org.multipaz_credential.wallet.AttributeDisplayInfoImage
import org.multipaz_credential.wallet.AttributeDisplayInfoPlainText
import org.multipaz_credential.wallet.DocumentInfo
import org.multipaz_credential.wallet.DocumentModel
import org.multipaz_credential.wallet.R
import org.multipaz_credential.wallet.navigation.WalletDestination
import org.multipaz_credential.wallet.ui.KeyValuePairHtml
import org.multipaz_credential.wallet.ui.KeyValuePairText
import org.multipaz_credential.wallet.ui.ScreenWithAppBarAndBackButton
import org.multipaz_credential.wallet.ui.prompt.biometric.showBiometricPrompt
import org.multipaz_credential.wallet.util.inverse
import kotlinx.coroutines.launch


private const val TAG = "DocumentDetailsScreen"

@Composable
fun DocumentDetailsScreen(
    documentId: String,
    documentModel: DocumentModel,
    requireAuthentication: Boolean,
    onNavigate: (String) -> Unit
) {
    val documentInfo = documentModel.getDocumentInfo(documentId)
    if (documentInfo == null) {
        Logger.w(TAG, "No document with id $documentId")
        onNavigate(WalletDestination.Main.route)
        return
    }

    Logger.d(TAG, "requireAuthentication: $requireAuthentication")

    var needAuthBeforeShowing = remember { mutableStateOf(requireAuthentication) }

    Box(
        modifier = Modifier.fillMaxHeight()
    ) {

        ScreenWithAppBarAndBackButton(
            title = stringResource(R.string.document_details_screen_title),
            onBackButtonClick = { onNavigate(WalletDestination.PopBackStack.route) }
        ) {
            if (needAuthBeforeShowing.value) {
                DocumentAuthenticationRequired(
                    documentInfo,
                    needAuthBeforeShowing,
                    onNavigate)
            } else {
                DocumentDetails(documentInfo)
            }
        }

        if (!needAuthBeforeShowing.value) {
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
                        shadow = Shadow(
                            color = Color.Black,
                            offset = Offset(0f, 0f),
                            blurRadius = 2f
                        ),
                    ),
                    modifier = Modifier.rotate(-30f)
                )
            }
        }
    }
}

@Composable
private fun DocumentAuthenticationRequired(
    documentInfo: DocumentInfo,
    needAuthBeforeShowing: MutableState<Boolean>,
    onNavigate: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val activity = LocalContext.current
    SideEffect {
        coroutineScope.launch() {
            // TODO: unlock one of the Credentials instead so we support other kind of
            //  authentications methods too, including e.g. passphrase
            showBiometricPrompt(
                activity = activity as FragmentActivity,
                title = "Use your screen lock",
                subtitle = "Authentication is required to view document data",
                cryptoObject = null,
                userAuthenticationTypes = setOf(
                    UserAuthenticationType.LSKF,
                    UserAuthenticationType.BIOMETRIC
                ),
                requireConfirmation = false,
                onSuccess = {
                    needAuthBeforeShowing.value = false
                },
                onCanceled = {
                    onNavigate(WalletDestination.PopBackStack.route)
                },
                onError = {
                    onNavigate(WalletDestination.PopBackStack.route)
                }
            )
        }
    }
}

@Composable
private fun DocumentDetails(
    documentInfo: DocumentInfo,
) {
    Column(
        modifier = Modifier
            .padding(top = 20.dp)
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(bottom = 20.dp)
                .border(
                    1.dp,
                    SolidColor(MaterialTheme.colorScheme.outline),
                    RoundedCornerShape(30.dp)
                )
        ) {
            Icon(
                modifier = Modifier
                    .size(40.dp)
                    .padding(top = 20.dp, start = 10.dp, end = 10.dp),
                painter = painterResource(id = R.drawable.warning),
                tint = MaterialTheme.colorScheme.background.inverse(),
                contentDescription = stringResource(
                    R.string.accessibility_artwork_for,
                    stringResource(id = R.string.document_details_screen_warning_icon_unverified_data)
                )
            )
            Text(
                modifier = Modifier.padding(top = 20.dp, bottom = 20.dp, end = 16.dp),
                text = stringResource(R.string.document_details_screen_flash_pass_lecture),
                textAlign = TextAlign.Left,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        val topImages = listOf("portrait")
        val bottomImages = listOf("signature_usual_mark")
        for (attributeId in topImages) {
            val attributeDisplayInfo = documentInfo.attributes[attributeId]
            if (attributeDisplayInfo != null) {
                val displayInfo = attributeDisplayInfo as AttributeDisplayInfoImage
                Image(
                    bitmap = displayInfo.image.asImageBitmap(),
                    modifier = Modifier
                        .width(100.dp)
                        .background(Color.Green)
                        .align(Alignment.Start),
                    contentDescription = displayInfo.name
                )
            }
        }
        val centerAttributes = documentInfo.attributes.filter {
            !topImages.contains(it.key) && !bottomImages.contains(it.key)
        }
        val orderedEntries = listOf(
            "issuing_country",
            "birth_date",
            "expiry_date",
            "given_name",
            "family_name",
        )
        val sortedKeys = centerAttributes.keys.sortedBy { keyValue ->
            if (keyValue in orderedEntries) {
                orderedEntries.indexOfFirst { it == keyValue }
            } else {
                Int.MAX_VALUE
            }
        }
        for (sortedKey in sortedKeys) {
            when (val displayInfo = centerAttributes[sortedKey]) {
                is AttributeDisplayInfoPlainText -> {
                    KeyValuePairText(displayInfo.name, displayInfo.value)
                }
                is AttributeDisplayInfoHtml -> {
                    KeyValuePairHtml(displayInfo.name, displayInfo.value)
                }
                else -> {
                    throw IllegalArgumentException("Unsupported attribute display info for $sortedKey: $displayInfo")
                }
            }
        }

        for (attributeId in bottomImages) {
            val attributeDisplayInfo = documentInfo.attributes[attributeId]
            if (attributeDisplayInfo != null) {
                val displayInfo = attributeDisplayInfo as AttributeDisplayInfoImage
                Row(
                    horizontalArrangement = Arrangement.Center
                ) {
                    Image(
                        bitmap = displayInfo.image.asImageBitmap(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .size(75.dp),
                        contentDescription = displayInfo.name
                    )
                }
            }
        }
    }
}
