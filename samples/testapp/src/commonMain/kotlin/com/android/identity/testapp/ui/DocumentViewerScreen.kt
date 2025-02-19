package com.android.identity.testapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
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

            LazyColumn(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (credential in documentInfo.credentials) {
                    item {
                        TextButton(
                            onClick = {
                                onViewCredential(documentInfo.document.identifier, credential.identifier)
                            }
                        ) {
                            Text("${credential::class.simpleName} domain ${credential.domain}")
                        }
                    }
                }
            }

        }
    }
}