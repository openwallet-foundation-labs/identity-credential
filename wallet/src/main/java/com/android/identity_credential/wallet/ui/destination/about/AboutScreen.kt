package com.android.identity_credential.wallet.ui.destination.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.identity_credential.wallet.ScreenWithAppBarAndBackButton

@Composable
fun AboutScreen(onNavigate: (String) -> Unit) {
    ScreenWithAppBarAndBackButton(title = "About Wallet", onNavigate = onNavigate) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                modifier = Modifier.padding(8.dp),
                text = "TODO: About Screen"
            )
        }
    }
}