package com.android.identity.wallet.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ValueLabel(
    modifier: Modifier = Modifier,
    label: String,
) {
    Text(
        modifier = modifier,
        text = label,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.labelMedium,
    )
}
