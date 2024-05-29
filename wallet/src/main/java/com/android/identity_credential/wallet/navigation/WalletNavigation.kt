package com.android.identity_credential.wallet.navigation

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.android.identity_credential.wallet.DocumentModel
import com.android.identity_credential.wallet.PermissionTracker
import com.android.identity_credential.wallet.ProvisioningViewModel
import com.android.identity_credential.wallet.QrEngagementViewModel
import com.android.identity_credential.wallet.WalletApplication
import com.android.identity_credential.wallet.ui.destination.about.AboutScreen
import com.android.identity_credential.wallet.ui.destination.addtowallet.AddToWalletScreen
import com.android.identity_credential.wallet.ui.destination.document.DocumentDetailsScreen
import com.android.identity_credential.wallet.ui.destination.document.DocumentInfoScreen
import com.android.identity_credential.wallet.ui.destination.document.CredentialInfoScreen
import com.android.identity_credential.wallet.ui.destination.main.MainScreen
import com.android.identity_credential.wallet.ui.destination.provisioncredential.ProvisionDocumentScreen
import com.android.identity_credential.wallet.ui.destination.qrengagement.QrEngagementScreen
import com.android.identity_credential.wallet.ui.destination.settings.SettingsScreen

/**
 * Defines the correlation of WalletDestination routes to composable screens
 */
@Composable
fun WalletNavigation(
    navController: NavHostController,
    application: WalletApplication,
    provisioningViewModel: ProvisioningViewModel,
    permissionTracker: PermissionTracker,
    sharedPreferences: SharedPreferences,
    qrEngagementViewModel: QrEngagementViewModel,
    documentModel: DocumentModel
) {

    // lambda navigateTo takes in a route string and navigates to the corresponding Screen
    // or perform a pop of the back stack
    val navigateTo: (String) -> Unit = { routeWithArgs ->
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

    val credentialStore = application.documentStore
    NavHost(
        navController = navController,
        startDestination = WalletDestination.Main.route
    ) {
        /**
         * Main Screen
         */
        composable(WalletDestination.Main.route) {
            MainScreen(
                onNavigate = navigateTo,
                qrEngagementViewModel = qrEngagementViewModel,
                documentModel = documentModel,
                settingsModel = application.settingsModel,
                context = application.applicationContext,
            )
        }

        /**
         * About Screen
         */
        composable(WalletDestination.About.route) {
            AboutScreen(onNavigate = navigateTo)
        }

        /**
         * Settings Screen
         */
        composable(WalletDestination.Settings.route) {
            SettingsScreen(
                settingsModel = application.settingsModel,
                documentStore = application.documentStore,
                onNavigate = navigateTo
            )
        }

        /**
         * Add To Wallet Screen
         */
        composable(WalletDestination.AddToWallet.route) {
            AddToWalletScreen(
                documentModel = documentModel,
                provisioningViewModel = provisioningViewModel,
                onNavigate = navigateTo,
                documentStore = application.documentStore,
                walletServerProvider = application.walletServerProvider,
                settingsModel = application.settingsModel,
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
                        onNavigate = navigateTo,
                    )
                }
                "credentials" -> {
                    CredentialInfoScreen(
                        documentId = cardId,
                        documentModel = documentModel,
                        onNavigate = navigateTo,
                    )
                }
                else -> {
                    DocumentInfoScreen(
                        context = application.applicationContext,
                        documentId = cardId,
                        documentModel = documentModel,
                        settingsModel = application.settingsModel,
                        onNavigate = navigateTo,
                    )
                }
            }
        }

        /**
         * Provision document Screen
         */
        composable(WalletDestination.ProvisionDocument.route) {
            ProvisionDocumentScreen(
                context = application.applicationContext,
                provisioningViewModel = provisioningViewModel,
                onNavigate = navigateTo,
                permissionTracker = permissionTracker,
                walletServerProvider = application.walletServerProvider,
                documentStore = application.documentStore
            )
        }

        composable(WalletDestination.QrEngagement.route) {
            QrEngagementScreen(
                qrEngagementViewModel = qrEngagementViewModel,
                onNavigate = navigateTo
            )
        }
    }
}