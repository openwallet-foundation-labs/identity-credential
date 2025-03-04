package com.android.identity_credential.wallet.ui.destination.main

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.android.identity.prompt.PromptModel
import com.android.identity.securearea.AndroidKeystoreSecureArea
import com.android.identity.util.Logger
import com.android.identity_credential.wallet.DocumentModel
import com.android.identity_credential.wallet.QrEngagementViewModel
import com.android.identity_credential.wallet.R
import com.android.identity_credential.wallet.SettingsModel
import com.android.identity_credential.wallet.WalletApplication
import com.android.identity_credential.wallet.navigation.WalletDestination
import com.android.identity_credential.wallet.ui.ScreenWithAppBar
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "MainScreen"

@Composable
fun MainScreen(
    onNavigate: (String) -> Unit,
    qrEngagementViewModel: QrEngagementViewModel,
    documentModel: DocumentModel,
    settingsModel: SettingsModel,
    promptModel: PromptModel,
    context: Context,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope { promptModel }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(stringResource(R.string.wallet_drawer_title), modifier = Modifier.padding(16.dp))
                HorizontalDivider()
                NavigationDrawerItem(
                    icon = { Icon(imageVector = Icons.Filled.Add, contentDescription = null) },
                    label = { Text(text = stringResource(R.string.wallet_drawer_add)) },
                    selected = false,
                    onClick = {
                        scope.launch {
                            drawerState.close()
                            onNavigate(WalletDestination.AddToWallet.route)
                        }
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(imageVector = Icons.Filled.Settings, contentDescription = null) },
                    label = { Text(text = stringResource(R.string.wallet_drawer_settings)) },
                    selected = false,
                    onClick = {
                        scope.launch {
                            drawerState.close()
                            onNavigate(WalletDestination.Settings.route)
                        }
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(imageVector = Icons.Filled.Fingerprint, contentDescription = null) },
                    label = { Text(text = stringResource(R.string.wallet_drawer_identity_reader)) },
                    selected = false,
                    onClick = {
                        scope.launch {
                            drawerState.close()
                            onNavigate(WalletDestination.Reader.route)
                        }
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(imageVector = Icons.Filled.Info, contentDescription = null) },
                    label = { Text(text = stringResource(R.string.wallet_drawer_about)) },
                    selected = false,
                    onClick = {
                        scope.launch {
                            drawerState.close()
                            onNavigate(WalletDestination.About.route)
                        }
                    }
                )
            }
        },
    ) {
        MainScreenContent(
            onNavigate = onNavigate,
            settingsModel = settingsModel,
            qrEngagementViewModel = qrEngagementViewModel,
            documentModel = documentModel,
            scope = scope,
            drawerState = drawerState,
            context = context
        )
    }
}

private fun isBluetoothEnabled(context: Context): Boolean {
    val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    val bluetoothAdapter = bluetoothManager?.adapter
    return bluetoothAdapter?.isEnabled ?: false
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreenContent(
    onNavigate: (String) -> Unit,
    settingsModel: SettingsModel,
    qrEngagementViewModel: QrEngagementViewModel,
    documentModel: DocumentModel,
    scope: CoroutineScope,
    drawerState: DrawerState,
    context: Context,
) {
    val hasProximityPresentationPermissions = rememberMultiplePermissionsState(
        WalletApplication.MDOC_PROXIMITY_PERMISSIONS
    )

    var showProximityPresentationPermissionsMissing by remember { mutableStateOf(false) }
    if (showProximityPresentationPermissionsMissing) {
        AlertDialog(
            title = {
                Text(stringResource(R.string.proximity_permissions_qr_alert_dialog_title))
            },
            text = {
                Text(stringResource(R.string.proximity_permissions_qr_alert_dialog_content))
            },
            onDismissRequest = { showProximityPresentationPermissionsMissing = false },
            dismissButton = {
                TextButton(onClick = { showProximityPresentationPermissionsMissing = false }) {
                    Text(stringResource(R.string.proximity_permissions_qr_alert_dialog_dismiss_button))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showProximityPresentationPermissionsMissing = false
                        hasProximityPresentationPermissions.launchMultiplePermissionRequest()
                    }
                ) {
                    Text(stringResource(R.string.proximity_permissions_qr_alert_dialog_confirm_button))
                }
            },
        )
    }

    var showDeviceLockNotSetupWarning by remember { mutableStateOf(false) }
    if (showDeviceLockNotSetupWarning) {
        AlertDialog(
            title = {
                Text(text = stringResource(R.string.qr_lskf_warning_dialog_title))
            },
            text = {
                Text(text = stringResource(R.string.qr_lskf_warning_dialog_text))
            },
            onDismissRequest = { showDeviceLockNotSetupWarning = false },
            confirmButton = {
                TextButton(
                    onClick = { showDeviceLockNotSetupWarning = false }
                ) {
                    Text(text = stringResource(R.string.qr_lskf_warning_dismiss_btn))
                }
            },
        )
    }

    var showBluetoothDisabled by remember { mutableStateOf(false) }
    if (showBluetoothDisabled) {
        AlertDialog(
            title = {
                Text(stringResource(R.string.qr_alert_dialog_bt_disabled_title))
            },
            text = {
                Text(stringResource(R.string.qr_alert_dialog_bt_disabled_text))
            },
            onDismissRequest = { showBluetoothDisabled = false },
            dismissButton = {
                TextButton(onClick = { showBluetoothDisabled = false }) {
                    Text(stringResource(R.string.proximity_permissions_qr_alert_dialog_dismiss_button))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBluetoothDisabled = false
                        openBluetoothSettings(context)
                    }
                ) {
                    Text(stringResource(R.string.qr_alert_dialog_bt_enable_button))
                }
            },
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }
    ScreenWithAppBar(title = stringResource(R.string.wallet_screen_title),
        navigationIcon = {
            IconButton(
                onClick = {
                    scope.launch {
                        drawerState.apply {
                            Logger.d(TAG, "isClosed = $isClosed")
                            if (isClosed) open() else close()
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = stringResource(R.string.accessibility_menu_icon)
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) {
        if (!settingsModel.screenLockIsSetup.value!!) {
            LaunchedEffect(snackbarHostState) {
                when (snackbarHostState.showSnackbar(
                    message = context.getString(R.string.no_screenlock_snackbar_text),
                    actionLabel = context.getString(R.string.no_screenlock_snackbar_action_label),
                    duration = SnackbarDuration.Indefinite,
                    withDismissAction = false
                )) {
                    SnackbarResult.Dismissed -> {
                    }
                    SnackbarResult.ActionPerformed -> {
                        val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ContextCompat.startActivity(context, intent, null)
                    }
                }
            }
        }

        if (documentModel.documentInfos.isEmpty()) {
            MainScreenNoDocumentsAvailable(onNavigate, context)
        } else {
            if (!hasProximityPresentationPermissions.allPermissionsGranted &&
                !settingsModel.hideMissingProximityPermissionsWarning.value!!) {
                LaunchedEffect(snackbarHostState) {
                    when (snackbarHostState.showSnackbar(
                        message = context.getString(R.string.proximity_permissions_snackbar_text),
                        actionLabel = context.getString(R.string.proximity_permissions_snackbar_action_label),
                        duration = SnackbarDuration.Indefinite,
                        withDismissAction = true
                    )) {
                        SnackbarResult.Dismissed -> {
                            settingsModel.hideMissingProximityPermissionsWarning.value = true
                        }
                        SnackbarResult.ActionPerformed -> {
                            hasProximityPresentationPermissions.launchMultiplePermissionRequest()
                        }
                    }
                }
            }
            // Display a snackbar notification about bluetooth, if it's disabled. Note that we won't
            // display it if the proximity presentations permissions are not granted (covered by the
            // snackbar notification above), because we can't trigger the Bluetooth Enable intent if
            // we haven't been granted BLUETOOTH_CONNECT permission.
            if (!isBluetoothEnabled(context) &&
                hasProximityPresentationPermissions.allPermissionsGranted &&
                !settingsModel.hideMissingBluetoothPermissionsWarning.value!!) {
                LaunchedEffect(snackbarHostState) {
                    when (snackbarHostState.showSnackbar(
                        message = "Bluetooth must be enabled for proximity presentations",
                        actionLabel = context.getString(R.string.proximity_permissions_snackbar_action_label),
                        duration = SnackbarDuration.Indefinite,
                        withDismissAction = true
                    )) {
                        SnackbarResult.Dismissed -> {
                            settingsModel.hideMissingBluetoothPermissionsWarning.value = true
                        }
                        SnackbarResult.ActionPerformed -> {
                            openBluetoothSettings(context)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.25f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.nfc_icon),
                        contentDescription = stringResource(R.string.wallet_screen_nfc_icon_content_description),
                        modifier = Modifier.size(96.dp),
                    )
                    Text(
                        modifier = Modifier.padding(8.dp),
                        text = stringResource(R.string.wallet_screen_nfc_presentation_instructions)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.25f))

            MainScreenDocumentPager(
                onNavigate = onNavigate,
                documentModel = documentModel,
                settingsModel = settingsModel
            )

            Spacer(modifier = Modifier.weight(0.5f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp, top = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                OutlinedButton(
                    onClick = {
                        if (!AndroidKeystoreSecureArea.Capabilities().secureLockScreenSetup) {
                            showDeviceLockNotSetupWarning = true
                        } else if (!hasProximityPresentationPermissions.allPermissionsGranted) {
                            showProximityPresentationPermissionsMissing = true
                        } else if (!isBluetoothEnabled(context)) {
                            showBluetoothDisabled = true
                        } else {
                            qrEngagementViewModel.startQrEngagement()
                            onNavigate(WalletDestination.QrEngagement.route)
                        }
                    },
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.qr_icon),
                        contentDescription = stringResource(R.string.wallet_screen_qr_icon_content_description),
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                    Text(
                        text = stringResource(R.string.wallet_screen_show_qr)
                    )
                }
            }
        }
    }
}

private fun openBluetoothSettings(context: Context) {
    Logger.i(TAG, "Opening Bluetooth enablement dialog")
    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
    enableBtIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(enableBtIntent)
    } catch (e: SecurityException) {
        // The linter complains if we don't handle this exception. We check for
        // permissions before bringing up this warning, but maybe we'll hit this
        // if the code changes or there's an unexpected race condition.
        Logger.e(TAG, "Permission failure trying to open BT enablement.", e)
    }
}

@Composable
fun MainScreenNoDocumentsAvailable(
    onNavigate: (String) -> Unit,
    context: Context
) {
    var showDeviceLockNotSetupWarning by remember { mutableStateOf(false) }
    if (showDeviceLockNotSetupWarning) {
        AlertDialog(
            title = {
                Text(text = stringResource(R.string.add_cred_lskf_warning_dialog_title))
            },
            text = {
                Text(text = stringResource(R.string.add_cred_lskf_warning_dialog_text))
            },
            onDismissRequest = { showDeviceLockNotSetupWarning = false },
            confirmButton = {
                TextButton(
                    onClick = { showDeviceLockNotSetupWarning = false }
                ) {
                    Text(text = stringResource(R.string.add_cred_lskf_warning_dismiss_btn))
                }
            },
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            text = stringResource(R.string.welcome_to_your_open_wallet_empty),
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Left
        )
        Text(
            modifier = Modifier.padding(8.dp),
            text = stringResource(R.string.wallet_screen_empty),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Left
        )
    }
    Image(
        painter = painterResource(R.drawable.welcome_image),
        contentDescription = stringResource(R.string.welcome_to_your_wallet_image),
        modifier = Modifier.padding(8.dp)
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Button(
            modifier = Modifier.padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            onClick = {
            if (!AndroidKeystoreSecureArea.Capabilities().secureLockScreenSetup) {
                showDeviceLockNotSetupWarning = true
            } else {
                onNavigate(WalletDestination.AddToWallet.route)
            }

        }) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Add icon",
                modifier = Modifier.padding(end = 8.dp, top = 12.dp, bottom = 12.dp)
            )
            Text(stringResource(R.string.wallet_screen_add))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreenDocumentPager(
    onNavigate: (String) -> Unit,
    documentModel: DocumentModel,
    settingsModel: SettingsModel,
) {
    val pagerState = rememberPagerState(
        initialPage = documentModel.getCardIndex(settingsModel.focusedCardId.value!!) ?: 0,
        pageCount = { documentModel.documentInfos.size }
    )

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            settingsModel.focusedCardId.value = documentModel.documentInfos[page].documentId
        }
    }

    Column {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.height(200.dp)
        ) { page ->
            val card = documentModel.documentInfos[page]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    bitmap = card.documentArtwork.asImageBitmap(),
                    contentDescription =
                    stringResource(R.string.accessibility_artwork_for, card.name),
                    colorFilter = if (card.attentionNeeded) {
                        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.1f) })
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxSize().clickable(onClick = {
                        onNavigate(
                            WalletDestination.DocumentInfo
                                .getRouteWithArguments(
                                    listOf(
                                        Pair(
                                            WalletDestination.DocumentInfo.Argument.DOCUMENT_ID,
                                            card.documentId
                                        )
                                    )
                                )
                        )
                    })
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .wrapContentHeight()
                .fillMaxWidth()
                .height(30.dp)
                .padding(8.dp),
        ) {
            repeat(pagerState.pageCount) { iteration ->
                val color =
                    if (pagerState.currentPage == iteration) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondary
                    }
                Box(
                    modifier = Modifier
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(color)
                        .size(8.dp)
                )
            }
        }
    }
}
