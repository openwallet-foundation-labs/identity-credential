package com.android.identity.testapp.ui

import androidx.compose.runtime.Composable

@Composable
actual fun AndroidKeystoreSecureAreaScreen(
    showToast: (message: String) -> Unit,
    onViewCertificate: (encodedCertificateData: String) -> Unit
) {
}
