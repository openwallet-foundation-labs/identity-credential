package com.android.identity.testapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.DiagnosticOption
import com.android.identity.claim.Claim
import com.android.identity.claim.MdocClaim
import com.android.identity.claim.VcClaim
import com.android.identity.documenttype.DocumentTypeRepository
import com.android.identity.testapp.DocumentModel

@Composable
fun CredentialClaimsViewerScreen(
    documentModel: DocumentModel,
    documentTypeRepository: DocumentTypeRepository,
    documentId: String,
    credentialId: String,
    showToast: (message: String) -> Unit
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
            println("foo")
            for (claim in credential.getClaims(documentTypeRepository)) {
                RenderClaim(claim)
            }
        }
    }
}

@Composable
private fun RenderClaim(
    claim: Claim,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .padding(8.dp)
            .fillMaxWidth()) {
        Text(
            text = claim.displayName,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        when (claim) {
            is MdocClaim -> {
                if (claim.value == null) {
                    Text(text = "<not set>")
                } else {
                    Text(
                        text = Cbor.toDiagnostics(
                            claim.value!!,
                            setOf(DiagnosticOption.PRETTY_PRINT, DiagnosticOption.BSTR_PRINT_LENGTH)
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            is VcClaim -> {
                if (claim.value == null) {
                    Text(text = "<not set>")
                } else {
                    Text(
                        text = claim.value.toString(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
