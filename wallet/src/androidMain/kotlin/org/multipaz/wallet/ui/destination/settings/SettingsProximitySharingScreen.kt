package org.multipaz_credential.wallet.ui.destination.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.multipaz.document.DocumentStore
import org.multipaz.util.Logger
import org.multipaz_credential.wallet.R
import org.multipaz_credential.wallet.SettingsModel
import org.multipaz_credential.wallet.navigation.WalletDestination
import org.multipaz_credential.wallet.ui.ScreenWithAppBarAndBackButton
import org.multipaz_credential.wallet.ui.SettingSectionSubtitle
import org.multipaz_credential.wallet.ui.SettingToggle

private const val TAG = "SettingsProximitySharingScreen"

@Composable
fun SettingsProximitySharingScreen(
    settingsModel: SettingsModel,
    documentStore: DocumentStore,
    onNavigate: (String) -> Unit
) {
    ScreenWithAppBarAndBackButton(
        title = stringResource(R.string.settings_proximity_sharing_screen_title),
        onBackButtonClick = { onNavigate(WalletDestination.PopBackStack.route) },
        scrollable = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            SettingSectionSubtitle(title = stringResource(R.string.settings_proximity_sharing_screen_device_engagement_settings_title))
            Text(
                text = stringResource(R.string.settings_proximity_sharing_screen_device_engagement_settings_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = FontStyle.Italic,
            )
            SettingToggle(
                title = stringResource(R.string.settings_proximity_sharing_screen_nfc_static_handover_title),
                isChecked = settingsModel.nfcStaticHandoverEnabled.observeAsState(false).value,
                onCheckedChange = { settingsModel.nfcStaticHandoverEnabled.value = it }
            )
            SettingToggle(
                title = stringResource(R.string.settings_proximity_sharing_screen_nfc_negotiated_handover_title),
                isChecked = !settingsModel.nfcStaticHandoverEnabled.observeAsState(false).value,
                onCheckedChange = { settingsModel.nfcStaticHandoverEnabled.value = !it }
            )

            SettingSectionSubtitle(title = stringResource(R.string.settings_proximity_sharing_screen_device_retrieval_title))
            Text(
                text = stringResource(R.string.settings_proximity_sharing_screen_device_retrieval_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = FontStyle.Italic,
            )
            SettingToggle(
                title = stringResource(R.string.settings_proximity_sharing_screen_device_retrieval_ble_mdoc_central_client_mode_title),
                isChecked = settingsModel.bleCentralClientMode.observeAsState(false).value,
                onCheckedChange = { settingsModel.bleCentralClientMode.value = it }
            )
            SettingToggle(
                title = stringResource(R.string.settings_proximity_sharing_screen_device_retrieval_ble_mdoc_peripheral_server_mode_title),
                isChecked = settingsModel.blePeripheralServerMode.observeAsState(false).value,
                onCheckedChange = { settingsModel.blePeripheralServerMode.value = it }
            )

            SettingSectionSubtitle(title = stringResource(R.string.settings_proximity_sharing_screen_options_title))
            Text(
                text = stringResource(R.string.settings_proximity_sharing_screen_options_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = FontStyle.Italic,
            )
            SettingToggle(
                title = stringResource(R.string.settings_proximity_sharing_screen_options_ble_l2cap_enabled_title),
                isChecked = settingsModel.bleL2CAP.observeAsState(false).value,
                onCheckedChange = { settingsModel.bleL2CAP.value = it }
            )
       }
    }
}