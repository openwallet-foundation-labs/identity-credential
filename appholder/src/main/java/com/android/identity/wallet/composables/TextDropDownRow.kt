package com.android.identity.wallet.composables

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun TextDropDownRow(
    modifier: Modifier = Modifier,
    label: String,
    onSelected: () -> Unit
) {
    DropdownMenuItem(
        modifier = modifier,
        text = {
            ValueLabel(
                modifier = Modifier.fillMaxWidth(),
                label = label
            )
        },
        onClick = onSelected
    )
}