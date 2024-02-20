package com.android.identity_credential.wallet.ui.destination.credential

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.identity.credential.CredentialStore
import com.android.identity.credentialtype.CredentialTypeRepository
import com.android.identity.util.Logger
import com.android.identity_credential.wallet.ScreenWithAppBarAndBackButton
import com.android.identity_credential.wallet.getViewCredentialData


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

    ScreenWithAppBarAndBackButton(title = "Credential Details", onNavigate = onNavigate) {
        if (portraitBitmap != null) {
            Row(
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    bitmap = portraitBitmap.asImageBitmap(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .size(200.dp),
                    contentDescription = "Portrait of Holder",
                )
            }
            Divider(modifier = Modifier.padding(8.dp))
        }
        for (section in viewCredentialData.sections) {
            if (section != viewCredentialData.sections[0]) {
                Divider(modifier = Modifier.padding(8.dp))
            }
            for ((key, value) in section.keyValuePairs) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "$key:",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "$value",
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
        if (signatureOrUsualMark != null) {
            Divider(modifier = Modifier.padding(8.dp))
            Row(
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    bitmap = signatureOrUsualMark.asImageBitmap(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .size(75.dp),
                    contentDescription = "Signature / Usual Mark of Holder",
                )
            }
        }
    }
}
