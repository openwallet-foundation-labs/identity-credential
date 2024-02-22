package com.android.identity_credential.wallet.ui.destination.credential

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.identity.credential.CredentialStore
import com.android.identity.credentialtype.CredentialTypeRepository
import com.android.identity.util.Logger
import com.android.identity_credential.wallet.R
import com.android.identity_credential.wallet.getViewCredentialData
import com.android.identity_credential.wallet.navigation.WalletDestination
import com.android.identity_credential.wallet.ui.ColumnWithPortrait
import com.android.identity_credential.wallet.ui.ScreenWithAppBarAndBackButton


const val TAG_CDS = "CredentialDetailsScreen"

@Composable
fun CredentialDetailsScreen(
    credentialId: String,
    onNavigate: (String) -> Unit,
    credentialStore: CredentialStore,
    credentialTypeRepository: CredentialTypeRepository
) {
    val credential = credentialStore.lookupCredential(credentialId)
    if (credential == null) {
        Logger.w(TAG_CDS, "No credential for $credentialId")
        return
    }

    val viewCredentialData = credential.getViewCredentialData(credentialTypeRepository)
    var portraitBitmap: Bitmap? = null
    var signatureOrUsualMark: Bitmap? = null

    if (viewCredentialData.portrait != null) {
        portraitBitmap = BitmapFactory.decodeByteArray(
            viewCredentialData.portrait,
            0,
            viewCredentialData.portrait.size
        )
    }
    if (viewCredentialData.signatureOrUsualMark != null) {
        signatureOrUsualMark = BitmapFactory.decodeByteArray(
            viewCredentialData.signatureOrUsualMark,
            0,
            viewCredentialData.signatureOrUsualMark.size
        )
    }

    ScreenWithAppBarAndBackButton(
        title = stringResource(R.string.details_title),
        onBackButtonClick = { onNavigate(WalletDestination.PopBackStack.route) }
    ) {
        ColumnWithPortrait {
            Row(
                horizontalArrangement = Arrangement.Center
            ) {
                if (portraitBitmap != null) {
                    Image(
                        bitmap = portraitBitmap.asImageBitmap(),
                        modifier = Modifier
                            .padding(8.dp)
                            .size(200.dp),
                        contentDescription = stringResource(R.string.accessibility_portrait),
                    )
                }
            }
            for (section in viewCredentialData.sections) {
                if (section != viewCredentialData.sections[0]) {
                    Divider(thickness = 4.dp, color = MaterialTheme.colorScheme.primary)
                }
                for ((key, value) in section.keyValuePairs) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // TODO: localizable key?
                        TextField(value, {}, readOnly = true, modifier = Modifier.fillMaxWidth(),
                            label = { Text(key) })
                    }
                }
            }
            if (signatureOrUsualMark != null) {
                Row(
                    horizontalArrangement = Arrangement.Center
                ) {
                    Image(
                        bitmap = signatureOrUsualMark.asImageBitmap(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .size(75.dp),
                        contentDescription = stringResource(R.string.accessibility_signature),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.weight(1.0f))
    }
}
