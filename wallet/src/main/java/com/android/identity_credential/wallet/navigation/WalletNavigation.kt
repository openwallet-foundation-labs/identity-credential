package com.android.identity_credential.wallet.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.android.identity.prompt.PromptModel
import com.android.identity.util.fromBase64Url
import com.android.identity_credential.wallet.DocumentModel
import com.android.identity_credential.wallet.PermissionTracker
import com.android.identity_credential.wallet.ProvisioningViewModel
import com.android.identity_credential.wallet.QrEngagementViewModel
import com.android.identity_credential.wallet.ReaderModel
import com.android.identity_credential.wallet.WalletApplication
import com.android.identity_credential.wallet.ui.destination.about.AboutScreen
import com.android.identity_credential.wallet.ui.destination.addtowallet.AddToWalletScreen
import com.android.identity_credential.wallet.ui.destination.document.CredentialInfoScreen
import com.android.identity_credential.wallet.ui.destination.document.DocumentDetailsScreen
import com.android.identity_credential.wallet.ui.destination.document.DocumentInfoScreen
import com.android.identity_credential.wallet.ui.destination.document.EventDetailsScreen
import com.android.identity_credential.wallet.ui.destination.document.EventLogScreen
import com.android.identity_credential.wallet.ui.destination.main.MainScreen
import com.android.identity_credential.wallet.ui.destination.provisioncredential.ProvisionDocumentScreen
import com.android.identity_credential.wallet.ui.destination.qrengagement.QrEngagementScreen
import com.android.identity_credential.wallet.ui.destination.reader.ReaderScreen
import com.android.identity_credential.wallet.ui.destination.settings.SettingsProximitySharingScreen
import com.android.identity_credential.wallet.ui.destination.settings.SettingsScreen

/**
 * Function that takes in a NavController and route string and navigates to the corresponding
 * composable Screen or perform a pop of the back stack.
 */
fun navigateTo(navController: NavController, routeWithArgs: String) {
    if (routeWithArgs.startsWith(Route.POP_BACK_STACK.routeName)) {
        // check to see if a route to pop back to was passed in
        val routeToPopBackTo =
            WalletDestination.PopBackStack
                .Argument.ROUTE
                .extractFromRouteString(routeWithArgs)
        if (routeToPopBackTo == null) { // no route specified, simple pop back to
            navController.popBackStack()
        } else { // a route was specified, check for 2 more arguments
            val inclusive = WalletDestination.PopBackStack
                .Argument.INCLUSIVE
                .extractFromRouteString(routeWithArgs).toBoolean()
            val saveState = WalletDestination.PopBackStack
                .Argument.SAVE_STATE
                .extractFromRouteString(routeWithArgs).toBoolean()
            // pop back stack with 3 args, 1 of which is optional (save state)
            navController.popBackStack(routeToPopBackTo, inclusive, saveState)
        }
    } else { // navigate to a Screen/Dialog destination
        navController.navigate(routeWithArgs)
    }
}

/**
 * Defines the correlation of WalletDestination routes to composable screens
 */
@Composable
fun WalletNavigation(
    navController: NavHostController,
    application: WalletApplication,
    provisioningViewModel: ProvisioningViewModel,
    permissionTracker: PermissionTracker,
    qrEngagementViewModel: QrEngagementViewModel,
    documentModel: DocumentModel,
    promptModel: PromptModel,
    readerModel: ReaderModel,
) {
    val onNavigate = { routeWithArgs: String -> navigateTo(navController, routeWithArgs) }
    NavHost(
        navController = navController,
        startDestination = WalletDestination.Main.route
    ) {
        /**
         * Main Screen
         */
        composable(WalletDestination.Main.route) {
            MainScreen(
                onNavigate = onNavigate,
                qrEngagementViewModel = qrEngagementViewModel,
                documentModel = documentModel,
                settingsModel = application.settingsModel,
                promptModel = promptModel,
                context = application.applicationContext,
            )
        }

        /**
         * About Screen
         */
        composable(WalletDestination.About.route) {
            AboutScreen(onNavigate = onNavigate)
        }

        /**
         * Settings Screen
         */
        composable(WalletDestination.Settings.route) {
            SettingsScreen(
                settingsModel = application.settingsModel,
                documentStore = application.documentStore,
                onNavigate = onNavigate
            )
        }

        /**
         * Settings Proximity Sharing Screen
         */
        composable(WalletDestination.SettingsProximitySharing.route) {
            SettingsProximitySharingScreen(
                settingsModel = application.settingsModel,
                documentStore = application.documentStore,
                onNavigate = onNavigate
            )
        }

        /**
         * Add To Wallet Screen
         */
        composable(WalletDestination.AddToWallet.route) {
            AddToWalletScreen(
                provisioningViewModel = provisioningViewModel,
                onNavigate = onNavigate,
                walletServerProvider = application.walletServerProvider
            )
        }

        composable(
            route = "${WalletDestination.EventDetails.route}/{documentId}/{timestamp}",
            arguments = listOf(
                navArgument("documentId") { type = NavType.StringType },
                navArgument("timestamp") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            // Extract arguments from the back stack entry
            val encodedDocumentId = backStackEntry.arguments?.getString("documentId") ?: ""
            val documentId = String(encodedDocumentId.fromBase64Url())
            val timestamp = backStackEntry.arguments?.getString("timestamp") ?: ""

            EventDetailsScreen(
                documentModel = documentModel,
                navController = navController,
                documentId = documentId,
                timestamp = timestamp
            )
        }


        composable(
            route = WalletDestination.DocumentInfo.routeWithArgs,
            arguments = WalletDestination.DocumentInfo.getArguments()
        ) { backStackEntry ->
            val cardId = WalletDestination.DocumentInfo
                .Argument.DOCUMENT_ID
                .extractFromBackStackEntry(backStackEntry) ?: ""
            val section = WalletDestination.DocumentInfo
                .Argument.SECTION
                .extractFromBackStackEntry(backStackEntry)
            val requireAuthentication = WalletDestination.DocumentInfo
                .Argument.AUTH_REQUIRED
                .extractBooleanFromBackStackEntry(backStackEntry) ?: false

            when (section) {
                "details" -> {
                    DocumentDetailsScreen(
                        documentId = cardId,
                        documentModel = documentModel,
                        requireAuthentication = requireAuthentication,
                        onNavigate = onNavigate,
                    )
                }
                "activities" -> {
                    EventLogScreen(
                        documentId = cardId,
                        documentModel = documentModel,
                        navController = navController,
                    )
                }
                "credentials" -> {
                    CredentialInfoScreen(
                        documentId = cardId,
                        documentModel = documentModel,
                        onNavigate = onNavigate,
                    )
                }
                else -> {
                    DocumentInfoScreen(
                        context = application.applicationContext,
                        documentId = cardId,
                        documentModel = documentModel,
                        settingsModel = application.settingsModel,
                        onNavigate = onNavigate,
                    )
                }
            }
        }

        /**
         * Provision document Screen
         */
        composable(WalletDestination.ProvisionDocument.route) {
            ProvisionDocumentScreen(
                application = application,
                secureAreaRepository = application.secureAreaRepository,
                provisioningViewModel = provisioningViewModel,
                onNavigate = onNavigate,
                permissionTracker = permissionTracker,
                walletServerProvider = application.walletServerProvider,
                promptModel = promptModel,
                developerMode = application.settingsModel.developerModeEnabled.value ?: false
            )
        }

        composable(WalletDestination.QrEngagement.route) {
            QrEngagementScreen(
                qrEngagementViewModel = qrEngagementViewModel,
                onNavigate = onNavigate
            )
        }

        composable(WalletDestination.Reader.route) {
            ReaderScreen(
                model = readerModel,
                docTypeRepo = application.documentTypeRepository,
                settingsModel = application.settingsModel,
                issuerTrustManager = application.issuerTrustManager,
                onNavigate = onNavigate,
            )
        }
    }
}