package com.android.identity_credential.wallet.ui.destination.main

import android.Manifest
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.os.Build
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.android.identity.credential.CredentialStore
import com.android.identity.issuance.CredentialExtensions.credentialConfiguration
import com.android.identity.util.Logger
import com.android.identity_credential.wallet.PermissionTracker
import com.android.identity_credential.wallet.QrEngagementViewModel
import com.android.identity_credential.wallet.R
import com.android.identity_credential.wallet.WalletApplication
import com.android.identity_credential.wallet.navigation.WalletDestination
import com.android.identity_credential.wallet.ui.ScreenWithAppBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

const val TAG_M = "MainScreen"

@Composable
fun MainScreen(
    onNavigate: (String) -> Unit,
    credentialStore: CredentialStore,
    qrEngagementViewModel: QrEngagementViewModel,
    sharedPreferences: SharedPreferences,
    permissionTracker: PermissionTracker
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(stringResource(R.string.wallet_drawer_title), modifier = Modifier.padding(16.dp))
                Divider()
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
            credentialStore = credentialStore,
            sharedPreferences = sharedPreferences,
            qrEngagementViewModel = qrEngagementViewModel,
            scope = scope,
            drawerState = drawerState,
            permissionTracker = permissionTracker
        )
    }
}

val blePermissions: List<String> = if (Build.VERSION.SDK_INT >= 31) {
    listOf(
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT)
} else {
    listOf(Manifest.permission.ACCESS_FINE_LOCATION)
}

@Composable
fun MainScreenContent(
    onNavigate: (String) -> Unit,
    credentialStore: CredentialStore,
    sharedPreferences: SharedPreferences,
    qrEngagementViewModel: QrEngagementViewModel,
    scope: CoroutineScope,
    drawerState: DrawerState,
    permissionTracker: PermissionTracker
) {
    ScreenWithAppBar(title = stringResource(R.string.wallet_screen_title),
        navigationIcon = {
            IconButton(
                onClick = {
                    scope.launch {
                        drawerState.apply {
                            Logger.d(TAG_M, "isClosed = $isClosed")
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
        }) {
        if (credentialStore.listCredentials().isEmpty()) {
            MainScreenNoCredentialsAvailable(onNavigate)
        } else {
            // TODO: this can be prettier
            permissionTracker.PermissionRequests(blePermissions)
            Spacer(modifier = Modifier.weight(0.5f))
            MainScreenCredentialPager(
                onNavigate = onNavigate,
                credentialStore = credentialStore,
                sharedPreferences = sharedPreferences
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                permissionTracker.PermissionCheck(
                    permissions = blePermissions,
                    displayPermissionRequest = false
                ) {
                    Text(
                        modifier = Modifier.padding(8.dp),
                        text = stringResource(R.string.wallet_screen_hold_to_reader)
                    )
                }
            }
            Spacer(modifier = Modifier.weight(0.5f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp, top = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                // TODO: should that be hidden if no bluetooth permission available?
                Button(
                    onClick = {
                        qrEngagementViewModel.startQrEngagement()
                        onNavigate(WalletDestination.QrEngagement.route)
                    }
                ) {
                    Text(
                        text = stringResource(R.string.wallet_screen_show_qr)
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreenNoCredentialsAvailable(onNavigate: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            modifier = Modifier.padding(8.dp),
            text = stringResource(R.string.wallet_screen_empty),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Button(onClick = {
            onNavigate(WalletDestination.AddToWallet.route)
        }) {
            Text(stringResource(R.string.wallet_screen_add))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreenCredentialPager(
    onNavigate: (String) -> Unit,
    credentialStore: CredentialStore,
    sharedPreferences: SharedPreferences,
) {
    val credentialIds = credentialStore.listCredentials()
    val pagerState = rememberPagerState(pageCount = {
        credentialIds.size
    })
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            sharedPreferences.edit {
                putString(WalletApplication.PREFERENCE_CURRENT_CREDENTIAL_ID, credentialIds[page])
            }
        }
    }

    Column {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.height(200.dp)
        ) { page ->

            val credentialId = credentialIds[page]
            val credential = credentialStore.lookupCredential(credentialId)!!
            val credentialConfiguration = credential.credentialConfiguration
            val options = BitmapFactory.Options()
            options.inMutable = true
            val credentialBitmap = BitmapFactory.decodeByteArray(
                credentialConfiguration.cardArt,
                0,
                credentialConfiguration.cardArt.size,
                options
            )
            val credentialName = credentialConfiguration.displayName

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    bitmap = credentialBitmap.asImageBitmap(),
                    contentDescription =
                        stringResource(R.string.accessibility_artwork_for, credentialName),
                    modifier = Modifier.clickable(onClick = {
                        onNavigate(
                            WalletDestination.CredentialInfo
                                .getRouteWithArguments(
                                    listOf(
                                        Pair(
                                            WalletDestination.CredentialInfo.Argument.CREDENTIAL_ID,
                                            credentialId
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
