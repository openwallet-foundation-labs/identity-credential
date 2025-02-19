package com.android.identity.testapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.identity.cbor.Cbor
import com.android.identity.credential.SecureAreaBoundCredential
import com.android.identity.crypto.X509CertChain
import com.android.identity.testapp.DocumentModel
import com.android.identity.util.toBase64Url
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime
import org.multipaz.compose.datetime.formattedDateTime

@Composable
fun CredentialViewerScreen(
    documentModel: DocumentModel,
    documentId: String,
    credentialId: String,
    showToast: (message: String) -> Unit,
    onViewCertificateChain: (encodedCertificateData: String) -> Unit,
    onViewCredentialClaims: (documentId: String, credentialId: String) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val documentInfo = documentModel.documentInfos[documentId]
    val credential = documentInfo?.credentials?.find { it.identifier == credentialId  }

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (credential == null) {
            Text("No credential for documentId ${documentId} credentialId ${credentialId}")
        } else {
            KeyValuePairText("Class", credential::class.simpleName.toString())
            KeyValuePairText("Identifier", credential.identifier)
            KeyValuePairText("Domain", credential.domain)
            KeyValuePairText("Valid From", formattedDateTime(credential.validFrom))
            KeyValuePairText("Valid Until", formattedDateTime(credential.validUntil))
            KeyValuePairText("Certified", credential.isCertified.toString())
            KeyValuePairText("Usage Count", credential.usageCount.toString())

            if (credential is SecureAreaBoundCredential) {
                KeyValuePairText("Secure Area", credential.secureArea.displayName)
                KeyValuePairText("Secure Area Identifier", credential.secureArea.identifier)
                KeyValuePairText("Device Key Attestation", "Click to see",
                    modifier = Modifier.clickable {
                        coroutineScope.launch {
                            val attestation = credential.getAttestation()
                            if (attestation.certChain != null) {
                                onViewCertificateChain(Cbor.encode(attestation.certChain!!.toDataItem()).toBase64Url())
                            } else {
                                showToast("No attestation for Device Key")
                            }
                        }
                    }
                )
            } else {
                KeyValuePairText("Secure Area", "N/A")
            }

            KeyValuePairText("Claims", "Click to see",
                modifier = Modifier.clickable {
                    coroutineScope.launch {
                        onViewCredentialClaims(documentId, credentialId)
                    }
                }
            )

        }
    }
}

@Composable
private fun KeyValuePairText(
    keyText: String,
    valueText: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .padding(8.dp)
            .fillMaxWidth()) {
        Text(
            text = keyText,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun KeyValuePairText(
    keyText: String,
    valueText: AnnotatedString,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .padding(8.dp)
            .fillMaxWidth()) {
        Text(
            text = keyText,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
