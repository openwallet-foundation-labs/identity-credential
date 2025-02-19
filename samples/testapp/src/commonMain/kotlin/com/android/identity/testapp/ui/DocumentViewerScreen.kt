package com.android.identity.testapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.android.identity.testapp.DocumentModel

@Composable
fun DocumentViewerScreen(
    documentModel: DocumentModel,
    documentId: String,
    showToast: (message: String) -> Unit,
    onViewCredential: (documentId: String, credentialId: String) -> Unit,
) {
    val documentInfo = documentModel.documentInfos[documentId]

    Column(Modifier.padding(8.dp)) {
        if (documentInfo == null) {
            Text("No document for identifier ${documentId}")
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    modifier = Modifier.height(200.dp),
                    contentScale = ContentScale.FillHeight,
                    bitmap = documentInfo.cardArt,
                    contentDescription = null,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    modifier = Modifier.padding(8.dp),
                    text = documentInfo.document.metadata.typeDisplayName ?: "(typeDisplayName not set)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                KeyValuePairText(
                    keyText = "Provisioned",
                    valueText = if (documentInfo.document.metadata.provisioned) "Yes" else "No"
                )
                KeyValuePairText(
                    keyText = "Document Type",
                    valueText = documentInfo.document.metadata.typeDisplayName ?: "(typeDisplayName not set)"
                )
                KeyValuePairText(
                    keyText = "Document Name",
                    valueText = documentInfo.document.metadata.displayName ?: "(displayName not set)"
                )
                Text(
                    text = "Credentials",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                for (credential in documentInfo.credentials) {
                    KeyValuePairText(
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .clickable {
                                onViewCredential(documentInfo.document.identifier, credential.identifier)
                            },
                        keyText = credential::class.simpleName.toString(),
                        valueText = buildAnnotatedString {
                            withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.secondary)) {
                                append("Click for details")
                            }
                        }
                    )
                }
            }

        }
    }
}
