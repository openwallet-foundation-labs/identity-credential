@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.android.identity_credential.wallet

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
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
import androidx.compose.material3.CenterAlignedTopAppBar
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.android.identity.issuance.CredentialExtensions.credentialConfiguration
import com.android.identity.issuance.CredentialExtensions.issuingAuthorityIdentifier
import com.android.identity.issuance.CredentialExtensions.state
import com.android.identity.issuance.evidence.EvidenceRequestIcaoPassiveAuthentication
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
import com.android.identity_credential.mrtd.MrtdNfcReader

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var application: WalletApplication

    private val provisioningViewModel: ProvisioningViewModel by viewModels()
    private val credentialInformationViewModel: CredentialInformationViewModel by viewModels()

    private val permissionTracker = PermissionTracker(this, mapOf(
        Manifest.permission.CAMERA to "This application requires camera permission to scan",
        Manifest.permission.NFC to "NFC permission is required to operate"
    ))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        application = getApplication() as WalletApplication

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
                    Text("Wallet", modifier = Modifier.padding(16.dp))
                    Divider()
                    NavigationDrawerItem(
                        icon = { Icon(imageVector = Icons.Filled.Add, contentDescription = null) },
                        label = { Text(text = "Add to Wallet") },
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
                        label = { Text(text = "About Wallet") },
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
    fun MainScreenContent(navigation: NavHostController,
                          scope: CoroutineScope,
                          drawerState: DrawerState) {
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    title = {
                        Text(
                            "Wallet",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
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
                                contentDescription = "Localized description"
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.Center,
            ) {
                if (application.credentialStore.listCredentials().size == 0) {
                    MainScreenNoCredentialsAvailable(navigation)
                } else {
                    MainScreenCredentialPager(navigation)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            modifier = Modifier.padding(8.dp),
                            text = "Hold to Reader"
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
                text = "No credentials in wallet, start by\n" +
                "adding credentials.",
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(onClick = {
                navigation.navigate("AddToWalletScreen")
            }) {
                Text("Add to Wallet")
            }
        }
    }

    @Composable
    fun MainScreenCredentialPager(navigation: NavHostController) {

        Column() {

            val credentialIds = application.credentialStore.listCredentials()
            val pagerState = rememberPagerState(pageCount = {
                credentialIds.size
            })
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
                        contentDescription = "Artwork for $credentialName",
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
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    title = {
                        Text(
                            "About Wallet",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                navigation.popBackStack()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Back Arrow"
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        modifier = Modifier.padding(8.dp),
                        text = "TODO: About Screen"
                    )
                }
            }
        }
    }

    @Composable
    fun AddToWalletScreen(navigation: NavHostController) {
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    title = {
                        Text(
                            "Add to Wallet",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                navigation.popBackStack()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Back Arrow"
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        modifier = Modifier.padding(8.dp),
                        text = "Select the issuer for provisioning."
                    )
                }

                for (issuer in application.issuingAuthorityRepository.getIssuingAuthorities()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ssXXX", Locale.US)
        df.timeZone = TimeZone.getTimeZone("UTC")
        val stateTimeString = df.format(Date(credential.state.timestamp))

        // TODO: this is needed to make the UI update when _lastRefreshedAt_ is
        //  updated. Figure out a way to do this without logging.
        Logger.d(TAG, "Last refresh in UI at ${credentialInformationViewModel.lastHousekeepingAt.value}")

        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    title = {
                        Text(
                            "Credential Information",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                navigation.popBackStack()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Back Arrow"
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Name: ${credential.credentialConfiguration.displayName}")
                Text("Issuer: ${issuer.configuration.name}")
                val state = credential.state
                Text("State: ${state.condition}")
                Text("CPO pending: ${state.numAvailableCPO}")
                Text("State Last Refresh: $stateTimeString")
                Divider()
                Text("Num PendingAuthKey: ${credential.pendingAuthenticationKeys.size}")
                Text("Num AuthKey: ${credential.authenticationKeys.size}")
                Divider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(onClick = {
                        credentialInformationViewModel.housekeeping(application, credential)
                    }) {
                        Text("Refresh")
                    }
                    Button(onClick = {
                        navigation.navigate("CredentialInfo/$credentialId/Details")
                    }) {
                        Text("View")
                    }
                    Button(onClick = {
                        // TODO: run issuer deletion flow
                        application.credentialStore.deleteCredential(credentialId)
                        navigation.popBackStack()
                    }) {
                        Text("Delete")
                    }
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

        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    title = {
                        Text(
                            "Credential Details",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                navigation.popBackStack()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Back Arrow"
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.Center,
            ) {
                if (portraitBitmap != null) {
                    Row(
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Image(
                            bitmap = portraitBitmap.asImageBitmap(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .size(200.dp),
                            contentDescription = "Portrait of Holder",
                        )
                    }
                    Divider(modifier = Modifier.padding(8.dp))
                }
                for (section in viewCredentialData.sections) {
                    if (section != viewCredentialData.sections[0]) {
                        Divider(modifier = Modifier.padding(8.dp))
                    }
                    for ((key, value) in section.keyValuePairs) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "$key:",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                                )
                            Text(
                                text = "$value",
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
                if (signatureOrUsualMark != null) {
                    Divider(modifier = Modifier.padding(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Image(
                            bitmap = signatureOrUsualMark.asImageBitmap(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .size(75.dp),
                            contentDescription = "Signature / Usual Mark of Holder",
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun ProvisionCredentialScreen(navigation: NavHostController) {
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    title = {
                        Text(
                            "Provisioning",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        if (provisioningViewModel.state.value != ProvisioningViewModel.State.PROOFING_COMPLETE) {
                            IconButton(
                                onClick = {
                                    navigation.popBackStack()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowBack,
                                    contentDescription = "Back Arrow"
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.Center,
            ) {
                when (provisioningViewModel.state.value) {
                    ProvisioningViewModel.State.IDLE -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                modifier = Modifier.padding(8.dp),
                                text = "TODO: Idle"
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
                                text = "TODO: Creating CredentialKey"
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
                            else -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        modifier = Modifier.padding(8.dp),
                                        text = "Unknown evidence type ${evidenceRequest.evidenceType}"
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
                                text = "TODO: Submitting evidence"
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
                                text = "Something went wrong: ${provisioningViewModel.error}"
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
                                text = "Unexpected state: ${provisioningViewModel.state.value}"
                            )
                        }
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
            Text(
                modifier = Modifier.padding(8.dp),
                text = evidenceRequest.message
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            if (evidenceRequest.rejectButtonText != null) {
                Button(onClick = {
                    provisioningViewModel.provideEvidence(EvidenceResponseMessage(false))
                }) {
                    Text(evidenceRequest.rejectButtonText)
                }
            }
            Button(onClick = {
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
            onValueChange = { inputText = it },
            label = { Text("Answer") }
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
                text = evidenceRequest.message
            )
        }

        val radioOptions = evidenceRequest.possibleValues
        val (selectedOption, onOptionSelected) = remember { mutableStateOf(radioOptions[0] ) }
        Column {
            radioOptions.forEach { text ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = (text == selectedOption),
                            onClick = {
                                onOptionSelected(text)
                            }
                        )
                        .padding(horizontal = 16.dp)
                ) {
                    RadioButton(
                        selected = (text == selectedOption),
                        onClick = { onOptionSelected(text) }
                    )
                    Text(
                        text = text,
                        modifier = Modifier.padding(start = 16.dp)
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
        val requiredPermissions = listOf(Manifest.permission.CAMERA, Manifest.permission.NFC)
        permissionTracker.PermissionCheck(requiredPermissions) {
            var visualScan by remember { mutableStateOf(true) }  // start with visual scan
            var status by remember { mutableStateOf<MrtdNfcReader.Status>(MrtdNfcReader.Initial) }
            val scope = rememberCoroutineScope()
            if (visualScan) {
                AndroidView(
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
                                        val mrzInfo = passportCapture.readFromCamera(surfaceProvider)
                                        status = MrtdNfcReader.Initial
                                        visualScan = false
                                        val nfcData = passportNfcScanner.scanCard(mrzInfo) { st ->
                                            status = st
                                        }
                                        provisioningViewModel.provideEvidence(
                                            EvidenceResponseIcaoPassiveAuthentication(mapOf(
                                                1 to nfcData.dg1,
                                                2 to nfcData.dg2
                                            ), nfcData.sod)
                                        )
                                    } catch (err: Exception) {
                                        Logger.e(TAG, "Error scanning MRTD: $err")
                                    }
                                }
                            }
                        }
                    }
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(when(status) {
                            is MrtdNfcReader.Initial -> "Waiting to scan"
                            else -> "Reading..."
                        })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(when(status) {
                            is MrtdNfcReader.Initial -> "Initial"
                            is MrtdNfcReader.Connected -> "Connected"
                            is MrtdNfcReader.AttemptingPACE -> "Attempting PACE"
                            is MrtdNfcReader.PACESucceeded -> "PACE Succeeded"
                            is MrtdNfcReader.PACENotSupported -> "PACE Not Supported"
                            is MrtdNfcReader.PACEFailed -> "PACE Not Supported"
                            is MrtdNfcReader.AttemptingBAC -> "Attempting BAC"
                            is MrtdNfcReader.BACSucceeded -> "BAC Succeeded"
                            is MrtdNfcReader.ReadingData -> {
                                val s = status as MrtdNfcReader.ReadingData
                                "Reading: ${s.read * 100 / s.total}%"
                            }
                            is MrtdNfcReader.Finished -> "Finished"
                        })
                    }
                }
            }
        }
    }
}
