package com.android.identity.testapp.ui

import androidx.compose.runtime.Composable

@Composable
actual fun AppTheme(content: @Composable () -> Unit) {
    return AppThemeDefault(content)
}
