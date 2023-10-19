package com.android.identity.wallet.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun CounterInput(
    modifier: Modifier = Modifier,
    label: String,
    value: Int,
    onValueChange: (newValue: Int) -> Unit
) {
    Column(modifier = modifier) {
        OutlinedContainerHorizontal(modifier = Modifier.fillMaxWidth()) {
            ValueLabel(
                modifier = Modifier.weight(1f),
                label = label
            )
            NumberChanger(number = value, onNumberChanged = onValueChange)
        }
    }
}