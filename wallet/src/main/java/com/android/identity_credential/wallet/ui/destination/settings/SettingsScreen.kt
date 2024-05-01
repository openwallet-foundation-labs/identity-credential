package com.android.identity_credential.wallet.ui.destination.settings

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.identity_credential.wallet.R
import com.android.identity_credential.wallet.SettingsModel
import com.android.identity_credential.wallet.navigation.WalletDestination
import com.android.identity_credential.wallet.ui.ScreenWithAppBarAndBackButton
import com.android.identity_credential.wallet.ui.SettingToggle

@Composable
fun SettingsScreen(
    settingsModel: SettingsModel,
    onNavigate: (String) -> Unit,
) {
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
                onDismissRequest = { showMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.settings_screen_log_to_file_clear_button)) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                    enabled = settingsModel.loggingEnabled.observeAsState(false).value,
                    onClick = {
                        settingsModel.clearLog()
                        showMenu = false
                    },
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
                                context.getString(R.string.settings_screen_log_to_file_chooser_title),
                            ),
                        )
                        showMenu = false
                    },
                )
            }
        },
        scrollable = false,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
        ) {
            SettingToggle(
                title = stringResource(R.string.settings_screen_dev_mode_title),
                subtitleOn = stringResource(R.string.settings_screen_dev_mode_subtitle_on),
                subtitleOff = stringResource(R.string.settings_screen_dev_mode_subtitle_off),
                isChecked = settingsModel.developerModeEnabled.observeAsState(false).value,
                onCheckedChange = { settingsModel.developerModeEnabled.value = it },
            )
            SettingToggle(
                title = stringResource(R.string.settings_screen_log_to_file_title),
                subtitleOn = stringResource(R.string.settings_screen_log_to_file_subtitle_on),
                subtitleOff = stringResource(R.string.settings_screen_log_to_file_subtitle_off),
                isChecked = settingsModel.loggingEnabled.observeAsState(false).value,
                onCheckedChange = { settingsModel.loggingEnabled.value = it },
            )
        }
    }
}
