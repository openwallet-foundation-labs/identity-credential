/*
 * Copyright (C) 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.android.identity_credential.wallet

import android.Manifest
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.camera.view.PreviewView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.android.identity.issuance.CredentialExtensions.credentialConfiguration
import com.android.identity.issuance.CredentialExtensions.issuingAuthorityIdentifier
import com.android.identity.issuance.CredentialExtensions.state
import com.android.identity.issuance.evidence.EvidenceRequestIcaoPassiveAuthentication
import com.android.identity.issuance.evidence.EvidenceRequestIcaoNfcTunnel
import com.android.identity.issuance.evidence.EvidenceRequestMessage
import com.android.identity.issuance.evidence.EvidenceRequestQuestionMultipleChoice
import com.android.identity.issuance.evidence.EvidenceRequestQuestionString
import com.android.identity.issuance.evidence.EvidenceResponseIcaoPassiveAuthentication
import com.android.identity.issuance.evidence.EvidenceResponseMessage
import com.android.identity.issuance.evidence.EvidenceResponseQuestionMultipleChoice
import com.android.identity.issuance.evidence.EvidenceResponseQuestionString
import com.android.identity.issuance.evidence.EvidenceType
import com.android.identity.util.Logger
import com.android.identity_credential.wallet.ui.theme.IdentityCredentialTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.android.identity_credential.mrtd.MrtdNfcScanner
import com.android.identity_credential.mrtd.MrtdMrzScanner
import com.android.identity_credential.mrtd.MrtdNfc
import com.android.identity_credential.mrtd.MrtdNfcDataReader
import com.android.identity_credential.mrtd.MrtdNfcReader

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var application: WalletApplication

    private val provisioningViewModel: ProvisioningViewModel by viewModels()
    private val credentialInformationViewModel: CredentialInformationViewModel by viewModels()
    private lateinit var sharedPreferences: SharedPreferences

    private val permissionTracker: PermissionTracker = if (Build.VERSION.SDK_INT >= 31) {
        PermissionTracker(this, mapOf(
            Manifest.permission.CAMERA to R.string.permission_camera,
            Manifest.permission.NFC to R.string.permission_nfc,
            Manifest.permission.BLUETOOTH_ADVERTISE to R.string.permission_bluetooth_advertise,
            Manifest.permission.BLUETOOTH_SCAN to R.string.permission_bluetooth_scan,
            Manifest.permission.BLUETOOTH_CONNECT to R.string.permission_bluetooth_connect
        ))
    } else {
        PermissionTracker(this, mapOf(
            Manifest.permission.CAMERA to R.string.permission_camera,
            Manifest.permission.NFC to R.string.permission_nfc,
            Manifest.permission.ACCESS_FINE_LOCATION to R.string.permission_bluetooth_connect
        ))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        application = getApplication() as WalletApplication
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        permissionTracker.updatePermissions()

        setContent {
            IdentityCredentialTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "MainScreen") {
                        composable("MainScreen") {
                            MainScreen(navController)
                        }
                        composable("AboutScreen") {
                            AboutScreen(navController)
                        }
                        composable("AddToWalletScreen") {
                            AddToWalletScreen(navController)
                        }
                        composable("CredentialInfo/{credentialId}") { backStackEntry ->
                            CredentialInfoScreen(navController,
                                backStackEntry.arguments?.getString("credentialId")!!)
                        }
                        composable("CredentialInfo/{credentialId}/Details") { backStackEntry ->
                            CredentialDetailsScreen(navController,
                                backStackEntry.arguments?.getString("credentialId")!!)
                        }
                        composable("ProvisionCredentialScreen") {
                            ProvisionCredentialScreen(navController)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun MainScreen(navigation: NavHostController) {
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
                                navigation.navigate("AddToWalletScreen")
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
                                navigation.navigate("AboutScreen")
                            }
                        }
                    )
                }
            },
        ) {
            MainScreenContent(navigation, scope, drawerState)
        }
    }

    @Composable
    fun MainScreenContent(
        navigation: NavHostController,
        scope: CoroutineScope,
        drawerState: DrawerState
    ) {
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
            }) {
            if (application.credentialStore.listCredentials().isEmpty()) {
                MainScreenNoCredentialsAvailable(navigation)
            } else {
                val blePermissions: List<String> = if (Build.VERSION.SDK_INT >= 31) {
                    listOf(Manifest.permission.BLUETOOTH_ADVERTISE,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT)
                } else {
                    listOf(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                permissionTracker.PermissionCheck(permissions = blePermissions) {
                    MainScreenCredentialPager(navigation)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            modifier = Modifier.padding(8.dp),
                            text = stringResource(R.string.wallet_screen_hold_to_reader)
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun MainScreenNoCredentialsAvailable(navigation: NavHostController) {
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
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(onClick = {
                navigation.navigate("AddToWalletScreen")
            }) {
                Text(stringResource(R.string.wallet_screen_add))
            }
        }
    }

    @Composable
    fun MainScreenCredentialPager(navigation: NavHostController) {
        val credentialIds = application.credentialStore.listCredentials()
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

        Column() {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.height(200.dp)
            ) { page ->

                val credentialId = credentialIds[page]
                val credential = application.credentialStore.lookupCredential(credentialId)!!
                val credentialConfiguration = credential.credentialConfiguration
                val options = BitmapFactory.Options()
                options.inMutable = true
                val credentialBitmap = BitmapFactory.decodeByteArray(
                    credentialConfiguration.cardArt,
                    0,
                    credentialConfiguration.cardArt.size,
                    options)
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
                            navigation.navigate("CredentialInfo/$credentialId")
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

    @Composable
    fun AboutScreen(navigation: NavHostController) {
        ScreenWithAppBarAndBackButton(
            title = stringResource(R.string.about_screen_title),
            navigation = navigation) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    text = stringResource(R.string.about_screen_text)
                )
            }
        }
    }

    @Composable
    fun AddToWalletScreen(navigation: NavHostController) {
        ScreenWithAppBarAndBackButton(
            title = stringResource(R.string.add_screen_title),
            navigation = navigation) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    text = stringResource(R.string.add_screen_select_issuer)
                )
            }

            for (issuer in application.issuingAuthorityRepository.getIssuingAuthorities()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(onClick = {
                        provisioningViewModel.reset()
                        provisioningViewModel.start(application, issuer)
                        navigation.navigate("ProvisionCredentialScreen")
                    }) {
                        Text(issuer.configuration.name)
                    }
                }
            }
        }
    }

    @Composable
    fun CredentialInfoScreen(navigation: NavHostController,
                             credentialId: String) {
        val credential = application.credentialStore.lookupCredential(credentialId)
        if (credential == null) {
            Logger.w(TAG, "No credential for $credentialId")
            return
        }
        Logger.d(TAG, "issuer ${credential.issuingAuthorityIdentifier}")
        val issuer = application.issuingAuthorityRepository.lookupIssuingAuthority(credential.issuingAuthorityIdentifier)
        if (issuer == null) {
            Logger.w(TAG, "No IssuingAuthority for ${credential.issuingAuthorityIdentifier}")
            return
        }
        // TODO: should this be localized?
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ssXXX", Locale.US)
        df.timeZone = TimeZone.getTimeZone("UTC")
        val stateTimeString = df.format(Date(credential.state.timestamp))

        // TODO: this is needed to make the UI update when _lastRefreshedAt_ is
        //  updated. Figure out a way to do this without logging.
        Logger.d(TAG, "Last refresh in UI at ${credentialInformationViewModel.lastHousekeepingAt.value}")

        ScreenWithAppBarAndBackButton(title = stringResource(R.string.credential_title), navigation = navigation) {
            val state = credential.state
            TextField(credential.credentialConfiguration.displayName, {}, readOnly = true,
                label = {Text(stringResource(R.string.credential_label_name))}, modifier = Modifier.fillMaxWidth())
            TextField(issuer.configuration.name, {}, readOnly = true,
                label = {Text(stringResource(R.string.credential_label_issuer))}, modifier = Modifier.fillMaxWidth())
            // TODO: localize state.condition text
            TextField(state.condition.toString(), {}, readOnly = true,
                label = {Text(stringResource(R.string.credential_label_state))}, modifier = Modifier.fillMaxWidth())
            TextField(state.numAvailableCPO.toString(), {}, readOnly = true,
                label = {Text(stringResource(R.string.credential_label_cpo_pending))}, modifier = Modifier.fillMaxWidth())
            TextField(stateTimeString, {}, readOnly = true,
                label = {Text(stringResource(R.string.credential_label_last_refresh))}, modifier = Modifier.fillMaxWidth())
            Divider()
            TextField(credential.authenticationKeys.size.toString(), {}, readOnly = true,
                label = {Text(stringResource(R.string.credential_label_pending_auth_keys))}, modifier = Modifier.fillMaxWidth())
            TextField(credential.pendingAuthenticationKeys.size.toString(), {}, readOnly = true,
                label = {Text(stringResource(R.string.credential_label_auth_keys))}, modifier = Modifier.fillMaxWidth())
            Divider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = {
                    credentialInformationViewModel.housekeeping(application, credential)
                }) {
                    Text(stringResource(R.string.credential_button_refresh))
                }
                Button(onClick = {
                    navigation.navigate("CredentialInfo/$credentialId/Details")
                }) {
                    Text(stringResource(R.string.credential_button_view))
                }
                Button(onClick = {
                    // TODO: run issuer deletion flow
                    application.credentialStore.deleteCredential(credentialId)
                    navigation.popBackStack()
                }) {
                    Text(stringResource(R.string.credential_button_delete))
                }
            }
        }
    }

    @Composable
    fun CredentialDetailsScreen(navigation: NavHostController,
                                credentialId: String) {
        val credential = application.credentialStore.lookupCredential(credentialId)
        if (credential == null) {
            Logger.w(TAG, "No credential for $credentialId")
            return
        }

        val viewCredentialData = credential.getViewCredentialData(application.credentialTypeRepository)
        var portraitBitmap: Bitmap? = null
        var signatureOrUsualMark: Bitmap? = null

        if (viewCredentialData.portrait != null) {
            portraitBitmap = BitmapFactory.decodeByteArray(
                viewCredentialData.portrait,
                0,
                viewCredentialData.portrait.size)
        }
        if (viewCredentialData.signatureOrUsualMark != null) {
            signatureOrUsualMark = BitmapFactory.decodeByteArray(
                viewCredentialData.signatureOrUsualMark,
                0,
                viewCredentialData.signatureOrUsualMark.size)
        }

        ScreenWithAppBarAndBackButton(title = stringResource(R.string.details_title),
            navigation = navigation) {
            ColumnWithPortrait {
                Row(
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (portraitBitmap != null) {
                        Image(
                            bitmap = portraitBitmap.asImageBitmap(),
                            modifier = Modifier
                                .padding(8.dp)
                                .size(200.dp),
                            contentDescription = stringResource(R.string.accessibility_portrait),
                        )
                    }
                }
                for (section in viewCredentialData.sections) {
                    if (section != viewCredentialData.sections[0]) {
                        Divider(thickness = 4.dp, color = MaterialTheme.colorScheme.primary)
                    }
                    for ((key, value) in section.keyValuePairs) {
                        // TODO: localizable key?
                        TextField(value, {}, readOnly = true, modifier = Modifier.fillMaxWidth(),
                            label = { Text(key) }
                        )
                    }
                }
                if (signatureOrUsualMark != null) {
                    Row(
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Image(
                            bitmap = signatureOrUsualMark.asImageBitmap(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .size(75.dp),
                            contentDescription = stringResource(R.string.accessibility_signature),
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun ProvisionCredentialScreen(navigation: NavHostController) {
        ScreenWithAppBar(title = stringResource(R.string.provisioning_title), navigationIcon = {
            if (provisioningViewModel.state.value != ProvisioningViewModel.State.PROOFING_COMPLETE) {
                IconButton(
                    onClick = {
                        navigation.popBackStack()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.accessibility_go_back_icon)
                    )
                }
            }
        }
        ) {
            when (provisioningViewModel.state.value) {
                ProvisioningViewModel.State.IDLE -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            modifier = Modifier.padding(8.dp),
                            text = stringResource(R.string.provisioning_idle)
                        )
                    }
                }

                ProvisioningViewModel.State.CREDENTIAL_REGISTRATION -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            modifier = Modifier.padding(8.dp),
                            text = stringResource(R.string.provisioning_creating_key)
                        )
                    }
                }

                ProvisioningViewModel.State.EVIDENCE_REQUESTS_READY -> {
                    // TODO: for now we just consider the first evidence request
                    val evidenceRequest = provisioningViewModel.evidenceRequests!![0]
                    when (evidenceRequest.evidenceType) {
                        EvidenceType.QUESTION_STRING -> {
                            EvidenceRequestQuestionString(evidenceRequest as EvidenceRequestQuestionString)
                        }

                        EvidenceType.MESSAGE -> {
                            EvidenceRequestMessage(evidenceRequest as EvidenceRequestMessage)
                        }

                        EvidenceType.QUESTION_MULTIPLE_CHOICE -> {
                            EvidenceRequestQuestionMultipleChoice(
                                evidenceRequest as EvidenceRequestQuestionMultipleChoice
                            )
                        }

                        EvidenceType.ICAO_9303_PASSIVE_AUTHENTICATION -> {
                            EvidenceRequestIcaoPassiveAuthentication(
                                evidenceRequest as EvidenceRequestIcaoPassiveAuthentication
                            )
                        }

                        EvidenceType.ICAO_9303_NFC_TUNNEL -> {
                            EvidenceRequestIcaoNfcTunnel(
                                evidenceRequest as EvidenceRequestIcaoNfcTunnel)
                        }

                        else -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    modifier = Modifier.padding(8.dp),
                                    text = stringResource(R.string.provisioning_unknown_evidence_type,
                                        evidenceRequest.evidenceType.toString())
                                )
                            }
                        }
                    }
                }

                ProvisioningViewModel.State.SUBMITTING_EVIDENCE -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                            text = stringResource(R.string.provisioning_submitting)
                        )
                    }
                }

                ProvisioningViewModel.State.PROOFING_COMPLETE -> {
                    navigation.popBackStack("MainScreen", false)
                }

                ProvisioningViewModel.State.FAILED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            modifier = Modifier.padding(8.dp),
                            text = stringResource(R.string.provisioning_error,
                                provisioningViewModel.error.toString())
                        )
                    }
                }

                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            modifier = Modifier.padding(8.dp),
                            text = stringResource(R.string.provisioning_unexpected,
                                provisioningViewModel.state.value)
                        )
                    }
                }
            }
        }

    }

    @Composable
    fun EvidenceRequestMessage(evidenceRequest: EvidenceRequestMessage) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            if (evidenceRequest.message.length < 100) {
            Text(
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                text = evidenceRequest.message
            )
            } else {
                Text(
                    modifier = Modifier.padding(8.dp),
                    text = evidenceRequest.message,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            if (evidenceRequest.rejectButtonText != null) {
                Button(
                    modifier = Modifier.padding(8.dp),
                    onClick = {
                    provisioningViewModel.provideEvidence(EvidenceResponseMessage(false))
                }) {
                    Text(evidenceRequest.rejectButtonText)
                }
            }
            Button(
                modifier = Modifier.padding(8.dp),
                onClick = {
                provisioningViewModel.provideEvidence(EvidenceResponseMessage(true))
            }) {
                Text(evidenceRequest.acceptButtonText)
            }
        }
    }

    @Composable
    fun EvidenceRequestQuestionString(evidenceRequest: EvidenceRequestQuestionString) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                modifier = Modifier.padding(8.dp),
                text = evidenceRequest.message
            )
        }

        var inputText by remember { mutableStateOf(evidenceRequest.defaultValue) }
        TextField(
            value = inputText,
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            onValueChange = { inputText = it },
            label = { Text(stringResource(R.string.evidence_question_label_answer)) }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(onClick = {
                provisioningViewModel.provideEvidence(EvidenceResponseQuestionString(inputText))
            }) {
                Text(evidenceRequest.acceptButtonText)
            }
        }

    }

    @Composable
    fun EvidenceRequestQuestionMultipleChoice(
        evidenceRequest: EvidenceRequestQuestionMultipleChoice
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                text = evidenceRequest.message
            )
        }

        val radioOptions = evidenceRequest.possibleValues
        val (selectedOption, onOptionSelected) = remember {
            mutableStateOf(radioOptions.keys.iterator().next() ) }
        Column {
            radioOptions.forEach { entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = (entry.key == selectedOption),
                            onClick = {
                                onOptionSelected(entry.key)
                            }
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (entry.key == selectedOption),
                        onClick = { onOptionSelected(entry.key) }
                    )
                    Text(
                        text = entry.value,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(onClick = {
                provisioningViewModel.provideEvidence(
                    EvidenceResponseQuestionMultipleChoice(selectedOption)
                )
            }) {
                Text(evidenceRequest.acceptButtonText)
            }
        }

    }

    @Composable
    fun EvidenceRequestIcaoPassiveAuthentication(
        evidenceRequest: EvidenceRequestIcaoPassiveAuthentication) {
        EvidenceRequestIcao(MrtdNfcDataReader(evidenceRequest.requestedDataGroups)) { nfcData ->
            provisioningViewModel.provideEvidence(
                EvidenceResponseIcaoPassiveAuthentication(nfcData.dataGroups, nfcData.sod)
            )
        }
    }

    @Composable
    fun EvidenceRequestIcaoNfcTunnel(evidenceRequest: EvidenceRequestIcaoNfcTunnel) {
        EvidenceRequestIcao(NfcTunnelScanner(provisioningViewModel)){
            provisioningViewModel.finishTunnel()
        }
    }

    @Composable
    fun <ResultT> EvidenceRequestIcao(reader: MrtdNfcReader<ResultT>, onResult: (ResultT) -> Unit) {
        val requiredPermissions = listOf(Manifest.permission.CAMERA, Manifest.permission.NFC)
        permissionTracker.PermissionCheck(requiredPermissions) {
            var visualScan by remember { mutableStateOf(true) }  // start with visual scan
            var status by remember { mutableStateOf<MrtdNfc.Status>(MrtdNfc.Initial) }
            val scope = rememberCoroutineScope()
            if (visualScan) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.evidence_camera_scan_mrz_title),
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                        text = stringResource(R.string.evidence_camera_scan_mrz_instruction),
                        style = MaterialTheme.typography.bodyLarge)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    AndroidView(
                        modifier = Modifier.padding(16.dp),
                        factory = { context ->
                            PreviewView(context).apply {
                                layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                scaleType = PreviewView.ScaleType.FILL_START
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                post {
                                    scope.launch {
                                        val passportCapture = MrtdMrzScanner(this@MainActivity)
                                        val passportNfcScanner = MrtdNfcScanner(this@MainActivity)
                                        try {
                                            val mrzInfo =
                                                passportCapture.readFromCamera(surfaceProvider)
                                            status = MrtdNfc.Initial
                                            visualScan = false
                                            val result =
                                                passportNfcScanner.scanCard(mrzInfo, reader) { st ->
                                                    status = st
                                                }
                                            onResult(result)
                                        } catch (err: Exception) {
                                            Logger.e(TAG, "Error scanning MRTD: $err")
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.evidence_nfc_scan_title),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.titleLarge)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        style = MaterialTheme.typography.bodyLarge,
                        text = when(status) {
                        is MrtdNfc.Initial -> stringResource(R.string.nfc_status_initial)
                        is MrtdNfc.Connected -> stringResource(R.string.nfc_status_connected)
                        is MrtdNfc.AttemptingPACE -> stringResource(R.string.nfc_status_attempting_pace)
                        is MrtdNfc.PACESucceeded -> stringResource(R.string.nfc_status_pace_succeeded)
                        is MrtdNfc.PACENotSupported -> stringResource(R.string.nfc_status_pace_not_supported)
                        is MrtdNfc.PACEFailed -> stringResource(R.string.nfc_status_pace_failed)
                        is MrtdNfc.AttemptingBAC -> stringResource(R.string.nfc_status_attempting_bac)
                        is MrtdNfc.BACSucceeded -> stringResource(R.string.nfc_status_bac_succeeded)
                        is MrtdNfc.ReadingData -> {
                            val s = status as MrtdNfc.ReadingData
                            stringResource(R.string.nfc_status_reading_data, s.progressPercent)
                        }
                        is MrtdNfc.TunnelAuthenticating -> {
                            val s = status as MrtdNfc.TunnelAuthenticating
                            stringResource(R.string.nfc_status_tunnel_authenticating, s.progressPercent)
                        }
                        is MrtdNfc.TunnelReading -> {
                            val s = status as MrtdNfc.TunnelReading
                            stringResource(R.string.nfc_status_tunnel_reading_data, s.progressPercent)
                        }
                        is MrtdNfc.Finished -> stringResource(R.string.nfc_status_finished)
                    })
                }
            }
        }
    }
}
