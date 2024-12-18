package com.android.identity.testapp

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.android.identity.appsupport.ui.AppTheme
import com.android.identity.secure_area_test_app.ui.CloudSecureAreaScreen
import com.android.identity.testapp.ui.AboutScreen
import com.android.identity.testapp.ui.AndroidKeystoreSecureAreaScreen
import com.android.identity.testapp.ui.ConsentModalBottomSheetListScreen
import com.android.identity.testapp.ui.ConsentModalBottomSheetScreen
import com.android.identity.testapp.ui.IsoMdocMultiDeviceTestingScreen
import com.android.identity.testapp.ui.IsoMdocProximityReadingScreen
import com.android.identity.testapp.ui.IsoMdocProximitySharingScreen
import com.android.identity.testapp.ui.PassphraseEntryFieldScreen
import com.android.identity.testapp.ui.ProvisioningTestScreen
import com.android.identity.testapp.ui.QrCodesScreen
import com.android.identity.testapp.ui.SecureEnclaveSecureAreaScreen
import com.android.identity.testapp.ui.SoftwareSecureAreaScreen
import com.android.identity.testapp.ui.StartScreen
import com.android.identity.testapp.ui.VerifierType
import identitycredential.samples.testapp.generated.resources.Res
import identitycredential.samples.testapp.generated.resources.back_button
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

class App {

    companion object {
        private const val TAG = "App"
    }

    private lateinit var snackbarHostState: SnackbarHostState

    @Composable
    @Preview
    fun Content(navController: NavHostController = rememberNavController()) {

        // Get current back stack entry
        val backStackEntry by navController.currentBackStackEntryAsState()
        // Get the name of the current screen
        val currentDestination = appDestinations.find {
            it.route == backStackEntry?.destination?.route
        } ?: StartDestination

        snackbarHostState = remember { SnackbarHostState() }
        AppTheme {
            // A surface container using the 'background' color from the theme
            Scaffold(
                topBar = {
                    AppBar(
                        currentDestination = currentDestination as Destination,
                        canNavigateBack = navController.previousBackStackEntry != null,
                        navigateUp = { navController.navigateUp() }
                    )
                },
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            ) { innerPadding ->

                NavHost(
                    navController = navController,
                    startDestination = StartDestination.route,
                    modifier = Modifier
                        .fillMaxSize()
                        //.verticalScroll(rememberScrollState())
                        .padding(innerPadding)
                ) {
                    composable(route = StartDestination.route) {
                        StartScreen(
                            onClickAbout = { navController.navigate(AboutDestination.route) },
                            onClickSoftwareSecureArea = { navController.navigate(SoftwareSecureAreaDestination.route) },
                            onClickAndroidKeystoreSecureArea = { navController.navigate(AndroidKeystoreSecureAreaDestination.route) },
                            onClickCloudSecureArea = { navController.navigate(CloudSecureAreaDestination.route) },
                            onClickSecureEnclaveSecureArea = { navController.navigate(SecureEnclaveSecureAreaDestination.route) },
                            onClickPassphraseEntryField = { navController.navigate(PassphraseEntryFieldDestination.route) },
                            onClickIssuanceTestField = { navController.navigate(ProvisioningTestDestination.route) },
                            onClickConsentSheetList = { navController.navigate(ConsentModalBottomSheetListDestination.route) },
                            onClickQrCodes = { navController.navigate(QrCodesDestination.route) },
                            onClickIsoMdocProximitySharing = { navController.navigate(IsoMdocProximitySharingDestination.route) },
                            onClickIsoMdocProximityReading = { navController.navigate(IsoMdocProximityReadingDestination.route) },
                            onClickMdocTransportMultiDeviceTesting = { navController.navigate(IsoMdocMultiDeviceTestingDestination.route) },
                        )
                    }
                    composable(route = AboutDestination.route) {
                        AboutScreen()
                    }
                    composable(route = SoftwareSecureAreaDestination.route) {
                        SoftwareSecureAreaScreen(showToast = { message -> showToast(message) })
                    }
                    composable(route = AndroidKeystoreSecureAreaDestination.route) {
                        AndroidKeystoreSecureAreaScreen(showToast = { message -> showToast(message) })
                    }
                    composable(route = SecureEnclaveSecureAreaDestination.route) {
                        SecureEnclaveSecureAreaScreen(showToast = { message -> showToast(message) })
                    }
                    composable(route = CloudSecureAreaDestination.route) {
                        CloudSecureAreaScreen(showToast = { message -> showToast(message) })
                    }
                    composable(route = PassphraseEntryFieldDestination.route) {
                        PassphraseEntryFieldScreen(showToast = { message -> showToast(message) })
                    }
                    composable(route = ProvisioningTestDestination.route) {
                        ProvisioningTestScreen()
                    }
                    composable(route = ConsentModalBottomSheetListDestination.route) {
                        ConsentModalBottomSheetListScreen(
                            onConsentModalBottomSheetClicked =
                            { mdlSampleRequest, verifierType ->
                                navController.navigate(
                                    ConsentModalBottomSheetDestination.route + "/$mdlSampleRequest/$verifierType")
                            }
                        )
                    }
                    composable(
                        route = ConsentModalBottomSheetDestination.routeWithArgs,
                        arguments = ConsentModalBottomSheetDestination.arguments
                    ) { navBackStackEntry ->
                        val mdlSampleRequest = navBackStackEntry.arguments?.getString(
                            ConsentModalBottomSheetDestination.mdlSampleRequestArg
                        )!!
                        val verifierType = VerifierType.valueOf(navBackStackEntry.arguments?.getString(
                            ConsentModalBottomSheetDestination.verifierTypeArg
                        )!!)
                        ConsentModalBottomSheetScreen(
                            mdlSampleRequest = mdlSampleRequest,
                            verifierType = verifierType,
                            showToast = { message -> showToast(message) },
                            onSheetConfirmed = { navController.popBackStack() },
                            onSheetDismissed = { navController.popBackStack() },
                        )
                    }
                    composable(route = QrCodesDestination.route) {
                        QrCodesScreen(
                            showToast = { message -> showToast(message) }
                        )
                    }
                    composable(route = IsoMdocProximitySharingDestination.route) {
                        IsoMdocProximitySharingScreen(
                            showToast = { message -> showToast(message) }
                        )
                    }
                    composable(route = IsoMdocProximityReadingDestination.route) {
                        IsoMdocProximityReadingScreen(
                            showToast = { message -> showToast(message) }
                        )
                    }
                    composable(route = IsoMdocMultiDeviceTestingDestination.route) {
                        IsoMdocMultiDeviceTestingScreen(
                            showToast = { message -> showToast(message) }
                        )
                    }
                }
            }
        }
    }

    private fun showToast(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            when (snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "OK",
                duration = SnackbarDuration.Short,
            )) {
                SnackbarResult.Dismissed -> {
                }

                SnackbarResult.ActionPerformed -> {
                }
            }
        }
    }
}

/**
 * Composable that displays the topBar and displays back button if back navigation is possible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBar(
    currentDestination: Destination,
    canNavigateBack: Boolean,
    navigateUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = { Text(stringResource(currentDestination.title)) },
        colors = TopAppBarDefaults.mediumTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = modifier,
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(onClick = navigateUp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.back_button)
                    )
                }
            }
        }
    )
}