package com.android.identity_credential.wallet.ui.destination.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.identity_credential.wallet.R
import com.android.identity_credential.wallet.navigation.WalletDestination
import com.android.identity_credential.wallet.ui.ScreenWithAppBarAndBackButton

@Composable
fun AboutScreen(onNavigate: (String) -> Unit) {
    ScreenWithAppBarAndBackButton(
        title = stringResource(R.string.about_screen_title),
        onBackButtonClick = { onNavigate(WalletDestination.PopBackStack.route) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodyLarge,
                text = stringResource(R.string.about_screen_text)
            )
        }
    }
}