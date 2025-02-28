package com.android.identity.testapp

import androidx.compose.ui.graphics.ImageBitmap
import com.android.identity.credential.Credential
import com.android.identity.document.Document

data class DocumentInfo(
    val document: Document,
    val cardArt: ImageBitmap,
    val credentialInfos: List<CredentialInfo>
)
