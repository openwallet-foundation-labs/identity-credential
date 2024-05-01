package com.android.mdl.appreader.settings

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.mdl.appreader.R
import com.android.mdl.appreader.theme.ReaderAppTheme

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    screenState: SettingsScreenState,
    onAutoCloseConnectionChanged: (enabled: Boolean) -> Unit,
    onUseL2CAPChanged: (enabled: Boolean) -> Unit,
    onBLEServiceCacheChanged: (enabled: Boolean) -> Unit,
    onHttpTransferChanged: (enabled: Boolean) -> Unit,
    onBLECentralClientModeChanged: (enabled: Boolean) -> Unit,
    onBLEPeripheralServerModeChanged: (enabled: Boolean) -> Unit,
    onWifiAwareTransferChanged: (enabled: Boolean) -> Unit,
    onNfcTransferChanged: (enabled: Boolean) -> Unit,
    onDebugLoggingChanged: (enabled: Boolean) -> Unit,
    onChangeReaderAuthentication: (which: Int) -> Unit,
    onOpenCaCertificates: () -> Unit,
) {
    Column(modifier = modifier) {
        val scrollState = rememberScrollState()
        var showReaderAuthOptions by rememberSaveable { mutableStateOf(false) }
        Column(
            modifier =
                Modifier
                    .padding(vertical = 16.dp)
                    .verticalScroll(scrollState),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SettingSectionTitle(title = "General")
                SettingToggle(
                    title = "Auto close connection",
                    subtitleOn = "Close connection after first request",
                    subtitleOff = "Don't close connection automatically",
                    isChecked = screenState.isAutoCloseConnectionEnabled,
                    onCheckedChange = onAutoCloseConnectionChanged,
                )
                SettingSectionTitle(title = "Data retrieval options")
                SettingToggle(
                    title = "Use L2CAP if available",
                    subtitleOn = "Use L2CAP",
                    subtitleOff = "Don't use L2CAP",
                    isChecked = screenState.isL2CAPEnabled,
                    onCheckedChange = onUseL2CAPChanged,
                )
                SettingToggle(
                    title = "Clear BLE Service Cache",
                    subtitleOn = "Clean the cache",
                    subtitleOff = "Don't clean the cache",
                    isChecked = screenState.isBleClearCacheEnabled,
                    onCheckedChange = onBLEServiceCacheChanged,
                )
                SettingSectionTitle(
                    title = "Data retrieval methods",
                    subtitle = "Used for NFC negotiated handover and reverse engagement",
                )
                SettingToggle(
                    title = "HTTP",
                    subtitleOn = "HTTP transfer activated",
                    subtitleOff = "HTTP transfer deactivated",
                    isChecked = screenState.isHttpTransferEnabled,
                    onCheckedChange = onHttpTransferChanged,
                )
                SettingToggle(
                    title = "BLE central client mode",
                    subtitleOn = "BLE central client mode activated",
                    subtitleOff = "BLE central client mode deactivated",
                    isChecked = screenState.isBleCentralClientModeEnabled,
                    onCheckedChange = onBLECentralClientModeChanged,
                )
                SettingToggle(
                    title = "BLE peripheral server mode",
                    subtitleOn = "BLE peripheral server mode activated",
                    subtitleOff = "BLE peripheral server mode deactivated",
                    isChecked = screenState.isBlePeripheralServerMode,
                    onCheckedChange = onBLEPeripheralServerModeChanged,
                )
                SettingToggle(
                    title = "WiFi Aware",
                    subtitleOn = "WiFi Aware transfer activated",
                    subtitleOff = "WiFi Aware transfer deactivated",
                    isChecked = screenState.isWifiAwareEnabled,
                    onCheckedChange = onWifiAwareTransferChanged,
                )
                SettingToggle(
                    title = "NFC",
                    subtitleOn = "NFC transfer activated",
                    subtitleOff = "NFC transfer deactivated",
                    isChecked = screenState.isNfcTransferEnabled,
                    onCheckedChange = onNfcTransferChanged,
                )
                SettingSectionTitle(title = "Debug Logging Options")
                SettingToggle(
                    title = "Debug",
                    subtitleOn = "Debug logging activated",
                    subtitleOff = "Debug logging deactivated",
                    isChecked = screenState.isDebugLoggingEnabled,
                    onCheckedChange = onDebugLoggingChanged,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            SettingSectionTitle(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = "Reader Authentication",
            )
            Spacer(modifier = Modifier.height(4.dp))
            SettingItem(
                modifier =
                    Modifier
                        .clickable { showReaderAuthOptions = true }
                        .padding(16.dp),
                title = "Use Reader Authentication",
                subtitle = readerAuthenticationFor(screenState.readerAuthentication),
            )
            Spacer(modifier = Modifier.height(16.dp))
            SettingSectionTitle(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = "CA Certificates",
            )
            Spacer(modifier = Modifier.height(4.dp))
            SettingItem(
                modifier =
                    Modifier
                        .clickable { onOpenCaCertificates() }
                        .padding(16.dp),
                title = "Show CA Certificates",
                subtitle = "Click here to show the CA Certificates",
            )
        }
        ReaderAuthenticationOptions(
            modifier = Modifier.fillMaxWidth(),
            show = showReaderAuthOptions,
            currentlySelected = screenState.readerAuthentication,
            onOptionSelected = {
                showReaderAuthOptions = false
                onChangeReaderAuthentication(it)
            },
            onDismiss = { showReaderAuthOptions = false },
        )
    }
}

@Composable
private fun ReaderAuthenticationOptions(
    modifier: Modifier = Modifier,
    show: Boolean,
    currentlySelected: Int,
    onOptionSelected: (value: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    if (show) {
        val listItems = stringArrayResource(id = R.array.readerAuthenticationNames)
        AlertDialog(
            modifier = modifier,
            onDismissRequest = onDismiss,
            confirmButton = {},
            dismissButton = {
                Button(onClick = onDismiss) {
                    Text(text = "Cancel")
                }
            },
            text = {
                LazyColumn {
                    itemsIndexed(listItems) { index, item ->
                        LabeledRadioButton(
                            label = item,
                            isSelected = index == currentlySelected,
                            onClick = { onOptionSelected(index) },
                        )
                    }
                }
            },
            title = {
                Text(
                    text = "Use Reader Authentication",
                    style = MaterialTheme.typography.titleLarge,
                )
            },
        )
    }
}

@Composable
private fun LabeledRadioButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .selectable(
                    selected = isSelected,
                    onClick = onClick,
                    role = Role.RadioButton,
                )
                .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label)
    }
}

@Composable
fun readerAuthenticationFor(readerAuthentication: Int): String {
    val values = stringArrayResource(id = R.array.readerAuthenticationNames)
    return values[readerAuthentication]
}

@Composable
private fun SettingSectionTitle(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String = "",
) {
    Column(modifier = modifier) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle.isNotBlank()) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SettingItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
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
    enabled: Boolean = true,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val subtitle = if (isChecked) subtitleOn else subtitleOff
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Switch(
            checked = isChecked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsScreenPreview() {
    ReaderAppTheme {
        SettingsScreen(
            modifier = Modifier.fillMaxSize(),
            screenState = SettingsScreenState(),
            onAutoCloseConnectionChanged = {},
            onUseL2CAPChanged = {},
            onBLEServiceCacheChanged = {},
            onHttpTransferChanged = {},
            onBLECentralClientModeChanged = {},
            onBLEPeripheralServerModeChanged = {},
            onWifiAwareTransferChanged = {},
            onNfcTransferChanged = {},
            onDebugLoggingChanged = {},
            onChangeReaderAuthentication = {},
            onOpenCaCertificates = {},
        )
    }
}
