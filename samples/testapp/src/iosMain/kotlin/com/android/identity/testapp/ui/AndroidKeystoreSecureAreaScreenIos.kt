package com.android.identity.testapp.ui

import androidx.compose.runtime.Composable
import com.android.identity.prompt.PromptModel

@Composable
actual fun AndroidKeystoreSecureAreaScreen(
    promptModel: PromptModel,
    showToast: (message: String) -> Unit,
    onViewCertificate: (encodedCertificateData: String) -> Unit
) {
}
