package com.android.identity_credential.wallet.navigation

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.android.identity_credential.wallet.CredentialInformationViewModel
import com.android.identity_credential.wallet.PermissionTracker
import com.android.identity_credential.wallet.ProvisioningViewModel
import com.android.identity_credential.wallet.QrEngagementViewModel
import com.android.identity_credential.wallet.WalletApplication
import com.android.identity_credential.wallet.ui.destination.about.AboutScreen
import com.android.identity_credential.wallet.ui.destination.addtowallet.AddToWalletScreen
import com.android.identity_credential.wallet.ui.destination.credential.CredentialDetailsScreen
import com.android.identity_credential.wallet.ui.destination.credential.CredentialInfoScreen
import com.android.identity_credential.wallet.ui.destination.main.MainScreen
import com.android.identity_credential.wallet.ui.destination.provisioncredential.ProvisionCredentialScreen
import com.android.identity_credential.wallet.ui.destination.qrengagement.QrEngagementScreen

/**
 * Defines the correlation of WalletDestination routes to composable screens
 */
@Composable
fun WalletNavigation(
    navController: NavHostController,
    application: WalletApplication,
    provisioningViewModel: ProvisioningViewModel,
    credentialInformationViewModel: CredentialInformationViewModel,
    permissionTracker: PermissionTracker,
    sharedPreferences: SharedPreferences,
    qrEngagementViewModel: QrEngagementViewModel
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

    val credentialStore = application.credentialStore
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
                credentialStore = credentialStore,
                sharedPreferences = sharedPreferences,
                qrEngagementViewModel = qrEngagementViewModel,
                permissionTracker = permissionTracker
            )
        }

        /**
         * About Screen
         */
        composable(WalletDestination.About.route) {
            AboutScreen(onNavigate = navigateTo)
        }

        /**
         * Add To Wallet Screen
         */
        composable(WalletDestination.AddToWallet.route) {
            AddToWalletScreen(
                provisioningViewModel = provisioningViewModel,
                onNavigate = navigateTo,
                issuingAuthorityRepository = application.issuingAuthorityRepository,
                credentialStore = application.credentialStore
            )
        }

        /**
         * Credential Details Screen
         * Credential Info Screen
         */
        composable(
            route = WalletDestination.CredentialInfo.routeWithArgs,
            arguments = WalletDestination.CredentialInfo.getArguments()
        ) { backStackEntry ->
            val credentialId = WalletDestination.CredentialInfo
                .Argument.CREDENTIAL_ID
                .extractFromBackStackEntry(backStackEntry) ?: ""
            val section = WalletDestination.CredentialInfo
                .Argument.SECTION
                .extractFromBackStackEntry(backStackEntry)

            if (section == "details") {
                CredentialDetailsScreen(
                    credentialId = credentialId,
                    onNavigate = navigateTo,
                    credentialStore = application.credentialStore,
                    credentialTypeRepository = application.credentialTypeRepository
                )
            } else {
                CredentialInfoScreen(
                    credentialId = credentialId,
                    onNavigate = navigateTo,
                    credentialStore = application.credentialStore,
                    issuingAuthorityRepository = application.issuingAuthorityRepository,
                    androidKeystoreSecureArea = application.androidKeystoreSecureArea,
                    credentialInformationViewModel = credentialInformationViewModel
                )
            }
        }

        /**
         * Provision Credential Screen
         */
        composable(WalletDestination.ProvisionCredential.route) {
            ProvisionCredentialScreen(
                provisioningViewModel = provisioningViewModel,
                onNavigate = navigateTo,
                permissionTracker = permissionTracker,
                issuingAuthorityRepository = application.issuingAuthorityRepository,
                credentialStore = application.credentialStore
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