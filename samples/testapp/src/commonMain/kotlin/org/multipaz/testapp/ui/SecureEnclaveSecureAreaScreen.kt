package org.multipaz.testapp.ui

import androidx.compose.runtime.Composable

@Composable
expect fun SecureEnclaveSecureAreaScreen(showToast: (message: String) -> Unit)
