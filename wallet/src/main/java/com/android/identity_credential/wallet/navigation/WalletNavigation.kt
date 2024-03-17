package com.android.identity_credential.wallet.navigation

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.android.identity_credential.wallet.CardViewModel
import com.android.identity_credential.wallet.PermissionTracker
import com.android.identity_credential.wallet.ProvisioningViewModel
import com.android.identity_credential.wallet.QrEngagementViewModel
import com.android.identity_credential.wallet.WalletApplication
import com.android.identity_credential.wallet.ui.destination.about.AboutScreen
import com.android.identity_credential.wallet.ui.destination.addtowallet.AddToWalletScreen
import com.android.identity_credential.wallet.ui.destination.credential.CardDetailsScreen
import com.android.identity_credential.wallet.ui.destination.credential.CardInfoScreen
import com.android.identity_credential.wallet.ui.destination.credential.CardKeysScreen
import com.android.identity_credential.wallet.ui.destination.main.MainScreen
import com.android.identity_credential.wallet.ui.destination.provisioncredential.ProvisionCredentialScreen
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
    cardViewModel: CardViewModel
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
                qrEngagementViewModel = qrEngagementViewModel,
                cardViewModel = cardViewModel,
                settingsModel = application.settingsModel,
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
         * Settings Screen
         */
        composable(WalletDestination.Settings.route) {
            SettingsScreen(
                settingsModel = application.settingsModel,
                onNavigate = navigateTo
            )
        }

        /**
         * Add To Wallet Screen
         */
        composable(WalletDestination.AddToWallet.route) {
            AddToWalletScreen(
                cardViewModel = cardViewModel,
                provisioningViewModel = provisioningViewModel,
                onNavigate = navigateTo,
                credentialStore = application.credentialStore,
                issuingAuthorityRepository = application.issuingAuthorityRepository
            )
        }

        /**
         * Card Info Screen
         * Card Details Screen
         * Card Keys Screen
         */
        composable(
            route = WalletDestination.CardInfo.routeWithArgs,
            arguments = WalletDestination.CardInfo.getArguments()
        ) { backStackEntry ->
            val cardId = WalletDestination.CardInfo
                .Argument.CARD_ID
                .extractFromBackStackEntry(backStackEntry) ?: ""
            val section = WalletDestination.CardInfo
                .Argument.SECTION
                .extractFromBackStackEntry(backStackEntry)

            when (section) {
                "details" -> {
                    CardDetailsScreen(
                        cardId = cardId,
                        cardViewModel = cardViewModel,
                        onNavigate = navigateTo,
                    )
                }
                "keys" -> {
                    CardKeysScreen(
                        cardId = cardId,
                        cardViewModel = cardViewModel,
                        onNavigate = navigateTo,
                    )
                }
                else -> {
                    CardInfoScreen(
                        cardId = cardId,
                        cardViewModel = cardViewModel,
                        settingsModel = application.settingsModel,
                        onNavigate = navigateTo,
                    )
                }
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