package com.android.identity.wallet.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.identity.wallet.R

@Composable
fun SoftwareSetupContainer(
    modifier: Modifier = Modifier,
    passphrase: String,
    onPassphraseChanged: (newValue: String) -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedContainerHorizontal(modifier = Modifier.fillMaxWidth()) {
            Box(contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    textStyle = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    value = passphrase,
                    onValueChange = onPassphraseChanged,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface)
                )
                if (passphrase.isEmpty()) {
                    Text(
                        text = stringResource(id = R.string.keystore_software_passphrase_hint),
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f)
                        ),
                    )
                }
            }
        }
    }
}