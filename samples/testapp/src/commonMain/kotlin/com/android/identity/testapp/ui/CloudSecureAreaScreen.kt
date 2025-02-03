package com.android.identity.secure_area_test_app.ui

import androidx.compose.runtime.Composable

@Composable
expect fun CloudSecureAreaScreen(
    showToast: (message: String) -> Unit,
    onViewCertificate: (encodedCertificateData: String) -> Unit
)
