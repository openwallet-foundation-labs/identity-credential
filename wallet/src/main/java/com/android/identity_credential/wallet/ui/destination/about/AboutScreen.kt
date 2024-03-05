package com.android.identity_credential.wallet.ui.destination.about

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.identity.util.Logger
import com.android.identity_credential.wallet.LoggerModel
import com.android.identity_credential.wallet.R
import com.android.identity_credential.wallet.navigation.WalletDestination
import com.android.identity_credential.wallet.ui.MarkdownAsset
import com.android.identity_credential.wallet.ui.ScreenWithAppBarAndBackButton

@Composable
fun AboutScreen(loggerModel: LoggerModel, onNavigate: (String) -> Unit) {
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
        if (Logger.isDebugEnabled) {
            DevelopmentTools(loggerModel)
        }
    }
}

@Composable
fun DevelopmentTools(loggerModel: LoggerModel) {
    val context = LocalContext.current
    Divider(modifier = Modifier.padding(top = 2.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Development tools",
            modifier = Modifier.padding(8.dp),
            style = MaterialTheme.typography.titleLarge,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val logToFile = loggerModel.logToFile.observeAsState(false)
        Checkbox(checked = logToFile.value, onCheckedChange = {loggerModel.logToFile.value = it})
        Text("Log to file",
            modifier = Modifier.padding(8.dp),
            style = MaterialTheme.typography.bodyLarge)
        Button(
            modifier = Modifier.padding(8.dp),
            onClick = {
                loggerModel.clearLog()
            }) {
            Text(text = "Clear")
        }
        Button(
            modifier = Modifier.padding(8.dp),
            onClick = {
                context.startActivity(Intent.createChooser(
                    loggerModel.createLogSharingIntent(context), "Share log"))
            }) {
            Text(text = "Share")
        }
    }
}