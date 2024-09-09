package com.android.identity.kmmtestapp.ui

import androidx.compose.runtime.Composable

@Composable
expect fun SecureEnclaveSecureAreaScreen(showToast: (message: String) -> Unit)
