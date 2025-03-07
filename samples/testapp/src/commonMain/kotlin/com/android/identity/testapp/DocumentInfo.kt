package org.multipaz.testapp

import androidx.compose.ui.graphics.ImageBitmap
import org.multipaz.credential.Credential
import org.multipaz.document.Document

data class DocumentInfo(
    val document: Document,
    val cardArt: ImageBitmap,
    val credentialInfos: List<CredentialInfo>
)
