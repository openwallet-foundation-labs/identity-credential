package org.multipaz_credential.wallet.ui.destination.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.multipaz_credential.wallet.R
import org.multipaz_credential.wallet.navigation.WalletDestination
import org.multipaz_credential.wallet.ui.MarkdownAsset
import org.multipaz_credential.wallet.ui.ScreenWithAppBarAndBackButton

@Composable
fun AboutScreen(onNavigate: (String) -> Unit) {
    ScreenWithAppBarAndBackButton(
        title = stringResource(R.string.about_screen_title),
        onBackButtonClick = { onNavigate(WalletDestination.PopBackStack.route) },
        scrollable = false
    ) {
        Row(
            modifier = Modifier.weight(1.0f),
            horizontalArrangement = Arrangement.Center
        ) {
            MarkdownAsset(asset = stringResource(R.string.asset_about_md),
                verticalScrolling = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight())
        }
    }
}
