package com.android.identity_credential.wallet.ui.destination.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.android.identity.document.DocumentStore
import com.android.identity_credential.wallet.R
import com.android.identity_credential.wallet.SettingsModel
import com.android.identity_credential.wallet.navigation.WalletDestination
import com.android.identity_credential.wallet.ui.ScreenWithAppBarAndBackButton
import com.android.identity_credential.wallet.ui.SettingString
import com.android.identity_credential.wallet.ui.SettingToggle

@Composable
fun SettingsScreen(
    settingsModel: SettingsModel,
    documentStore: DocumentStore,
    onNavigate: (String) -> Unit
) {
    var showConfirmChangeWalletServerUrlDialog by remember { mutableStateOf("") }
    if (showConfirmChangeWalletServerUrlDialog != "") {
        AlertDialog(
            onDismissRequest = { showConfirmChangeWalletServerUrlDialog = "" },
            title = { Text(text = stringResource(R.string.settings_screen_confirm_set_wallet_server_url_dialog_title)) },
            text = {
                Text(stringResource(R.string.settings_screen_confirm_set_wallet_server_url_dialog_message))
            },
            confirmButton = {
                Button(
                    onClick = {
                        for (documentId in documentStore.listDocuments()) {
                            documentStore.deleteDocument(documentId)
                        }
                        settingsModel.walletServerUrl.value = showConfirmChangeWalletServerUrlDialog
                        showConfirmChangeWalletServerUrlDialog = ""
                    }) {
                    Text(stringResource(R.string.settings_screen_confirm_set_wallet_server_url_dialog_confirm))
                }
            },
            dismissButton = {
                Button(
                    onClick = { showConfirmChangeWalletServerUrlDialog = "" }) {
                    Text(stringResource(R.string.settings_screen_confirm_set_wallet_server_url_dialog_dismiss))
                }
            }
        )
    }

    var showSetWalletServerUrlDialog by remember { mutableStateOf(false) }
    if (showSetWalletServerUrlDialog) {
        SetWalletServerUrlDialog(
            initialValue = settingsModel.walletServerUrl.value!!,
            onDismissed = { showSetWalletServerUrlDialog = false },
            onSet = { walletServerUrl ->
                showSetWalletServerUrlDialog = false
                showConfirmChangeWalletServerUrlDialog = walletServerUrl
            }
        )
    }

    var showMenu by remember { mutableStateOf(false) }
    ScreenWithAppBarAndBackButton(
        title = stringResource(R.string.settings_screen_title),
        onBackButtonClick = { onNavigate(WalletDestination.PopBackStack.route) },
        actions = {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.Menu, contentDescription = null)
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.settings_screen_log_to_file_clear_button)) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                    enabled = settingsModel.loggingEnabled.observeAsState(false).value,
                    onClick = {
                        settingsModel.clearLog()
                        showMenu = false
                    }
                )
                val context = LocalContext.current
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.settings_screen_log_to_file_share_button)) },
                    leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                    enabled = settingsModel.loggingEnabled.observeAsState(false).value,
                    onClick = {
                        context.startActivity(
                            Intent.createChooser(
                                settingsModel.createLogSharingIntent(context),
                                context.getString(R.string.settings_screen_log_to_file_chooser_title)
                            )
                        )
                        showMenu = false
                    }
                )
            }
        },
        scrollable = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            SettingToggle(
                title = stringResource(R.string.settings_screen_dev_mode_title),
                subtitleOn = stringResource(R.string.settings_screen_dev_mode_subtitle_on),
                subtitleOff = stringResource(R.string.settings_screen_dev_mode_subtitle_off),
                isChecked = settingsModel.developerModeEnabled.observeAsState(false).value,
                onCheckedChange = { settingsModel.developerModeEnabled.value = it }
            )
            SettingToggle(
                title = stringResource(R.string.settings_screen_log_to_file_title),
                subtitleOn = stringResource(R.string.settings_screen_log_to_file_subtitle_on),
                subtitleOff = stringResource(R.string.settings_screen_log_to_file_subtitle_off),
                isChecked = settingsModel.loggingEnabled.observeAsState(false).value,
                onCheckedChange = { settingsModel.loggingEnabled.value = it }
            )
            SettingString(
                title = stringResource(R.string.settings_screen_wallet_server_title),
                subtitle = settingsModel.walletServerUrl.observeAsState().value!!.let {
                    if (it == "dev:") {
                        stringResource(R.string.settings_screen_wallet_server_built_in)
                    } else it
                },
                onClicked = { showSetWalletServerUrlDialog = true }
            )

        }
    }
}

@Composable
private fun SetWalletServerUrlDialog(
    initialValue: String,
    onDismissed: () -> Unit,
    onSet: (walletServerUrl: String) -> Unit
) {
    var url by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = { onDismissed() },
        title = { Text(text = stringResource(R.string.settings_screen_set_wallet_server_url_dialog_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.settings_screen_set_wallet_server_url_dialog_message))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(stringResource(R.string.settings_screen_set_wallet_server_url_label)) }
                    )
                }
                LinkText(stringResource(R.string.settings_screen_set_wallet_server_url_link_built_in)) { url = "dev:" }
                LinkText(stringResource(R.string.settings_screen_set_wallet_server_url_link_emulator)) {
                    url = "http://10.0.2.2:8080/wallet-server"
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSet(url) }) {
                Text(stringResource(R.string.settings_screen_set_wallet_server_url_dialog_confirm_button))
            }
        },
        dismissButton = {
            Button(
                onClick = { onDismissed() }) {
                Text(stringResource(R.string.settings_screen_set_wallet_server_url_dialog_dismiss_button))
            }
        }
    )
}

@Composable
private fun LinkText(
    text: String,
    onClicked: () -> Unit,
) {
    ClickableText(buildAnnotatedString {
        withStyle(
            style = SpanStyle(
                color = Color.Blue,
                textDecoration = TextDecoration.Underline,
            )
        ) {
            append(text)
        }
    }) {
        onClicked()
    }
}
