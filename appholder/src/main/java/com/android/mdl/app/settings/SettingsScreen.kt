package com.android.mdl.app.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.mdl.app.theme.HolderAppTheme

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    screenState: SettingsScreenState,
    onAutoCloseChanged: (Boolean) -> Unit,
    onUseStaticHandoverChanged: (Boolean) -> Unit,
    onUseL2CAPChanged: (Boolean) -> Unit,
    onBLEServiceCacheChanged: (Boolean) -> Unit,
    onBLEDataRetrievalModeChanged: (Boolean) -> Unit,
    onBLEPeripheralDataRetrievalModeChanged: (Boolean) -> Unit,
    onWiFiAwareChanged: (Boolean) -> Unit,
    onNfcChanged: (Boolean) -> Unit,
    onDebugChanged: (Boolean) -> Unit,
) {
    Column(modifier = modifier) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingSectionTitle(title = "General")
            SettingToggle(
                title = "Auto close connection",
                subtitleOn = "Close connection after first response",
                subtitleOff = "Don't close connection after first response",
                isChecked = screenState.autoCloseEnabled,
                onCheckedChange = onAutoCloseChanged
            )
            SettingSectionTitle(title = "NFC Engagement")
            SettingToggle(
                title = "Use static handover",
                subtitleOn = "Use static handover",
                subtitleOff = "Use negotiated handover",
                isChecked = screenState.useStaticHandover,
                onCheckedChange = onUseStaticHandoverChanged
            )
            SettingSectionTitle(title = "Data retrieval options")
            SettingToggle(
                title = "Use L2CAP if available",
                subtitleOn = "Use L2CAP",
                subtitleOff = "Don't use L2CAP",
                isChecked = screenState.isL2CAPEnabled,
                enabled = screenState.isBleEnabled(),
                onCheckedChange = onUseL2CAPChanged
            )
            SettingToggle(
                title = "Clear BLE Service Cache",
                subtitleOn = "Clean the cache",
                subtitleOff = "Don't clean the cache",
                isChecked = screenState.isBleClearCacheEnabled,
                enabled = screenState.isBleEnabled(),
                onCheckedChange = onBLEServiceCacheChanged
            )
            SettingSectionTitle(title = "Data retrieval methods")
            SettingToggle(
                title = "BLE central client mode",
                subtitleOn = "BLE central client mode activated",
                subtitleOff = "BLE central client mode deactivated",
                isChecked = screenState.isBleDataRetrievalEnabled,
                onCheckedChange = onBLEDataRetrievalModeChanged
            )
            SettingToggle(
                title = "BLE peripheral server mode",
                subtitleOn = "BLE peripheral server mode activated",
                subtitleOff = "BLE peripheral server mode deactivated",
                isChecked = screenState.isBlePeripheralModeEnabled,
                onCheckedChange = onBLEPeripheralDataRetrievalModeChanged
            )
            SettingToggle(
                title = "Wifi Aware",
                subtitleOn = "Wifi Aware transfer activated",
                subtitleOff = "Wifi Aware transfer deactivated",
                isChecked = screenState.wifiAwareEnabled,
                onCheckedChange = onWiFiAwareChanged
            )
            SettingToggle(
                title = "NFC",
                subtitleOn = "NFC transfer activated",
                subtitleOff = "NFC transfer deactivated",
                isChecked = screenState.nfcEnabled,
                onCheckedChange = onNfcChanged
            )
            SettingSectionTitle(title = "Debug logging options")
            SettingToggle(
                title = "Debug",
                subtitleOn = "Debug logging activated",
                subtitleOff = "Debug logging deactivated",
                isChecked = screenState.debugEnabled,
                onCheckedChange = onDebugChanged
            )
        }
    }
}

@Composable
private fun SettingSectionTitle(
    modifier: Modifier = Modifier,
    title: String
) {
    Column(modifier = modifier) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SettingToggle(
    modifier: Modifier = Modifier,
    title: String,
    subtitleOn: String,
    subtitleOff: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            val subtitle = if (isChecked) subtitleOn else subtitleOff
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Switch(
            checked = isChecked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsScreenPreview() {
    HolderAppTheme {
        SettingsScreen(
            modifier = Modifier.fillMaxSize(),
            screenState = SettingsScreenState(),
            onAutoCloseChanged = {},
            onUseStaticHandoverChanged = {},
            onUseL2CAPChanged = {},
            onBLEServiceCacheChanged = {},
            onBLEDataRetrievalModeChanged = {},
            onBLEPeripheralDataRetrievalModeChanged = {},
            onWiFiAwareChanged = {},
            onNfcChanged = {},
            onDebugChanged = {}
        )
    }
}