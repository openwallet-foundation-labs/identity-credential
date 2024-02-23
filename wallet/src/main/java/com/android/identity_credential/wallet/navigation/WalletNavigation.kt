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
import com.android.identity_credential.wallet.ui.destination.consentprompt.ConsentPrompt
import com.android.identity_credential.wallet.ui.destination.consentprompt.ConsentPromptData
import com.android.identity_credential.wallet.ui.destination.credential.CredentialDetailsScreen
import com.android.identity_credential.wallet.ui.destination.credential.CredentialInfoScreen
import com.android.identity_credential.wallet.ui.destination.main.MainScreen
import com.android.identity_credential.wallet.ui.destination.provisioncredential.ProvisionCredentialScreen
import com.android.identity_credential.wallet.ui.destination.qrengagement.QrEngagementScreen
import com.android.identity_credential.wallet.util.unparcelize

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

    // the lambda 'navigateTo' performs navigation functionality hoisted from child composables by
    // taking in a route string param and navigating to the corresponding Screen, or performing a
    // pop of the back stack (simple and with arguments).
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

    /**
     * NavHost definition of routes and composables
     */
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
                credentialStore = application.credentialStore,
                sharedPreferences = sharedPreferences,
                qrEngagementViewModel = qrEngagementViewModel,
                permissionTracker = permissionTracker
            )
        }

        /**
         * About Screen
         */
        composable(WalletDestination.About.route) {
            AboutScreen(onNavigate = navigateTo, loggerModel = application.loggerModel)
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
         * Credential Info Screen is shown when a credentialId is passed in
         * Credential Details Screen shows when "section=details" is also passed in
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

        /**
         * QR Engagement Screen
         */
        composable(WalletDestination.QrEngagement.route) {
            QrEngagementScreen(
                qrEngagementViewModel = qrEngagementViewModel,
                onNavigate = navigateTo
            )
        }

        /**
         * Consent Prompt bottom sheet modal dialog expects 4 arguments to show
         */
        composable(
            route = WalletDestination.ConsentPrompt.route,
            arguments = WalletDestination.ConsentPrompt.getArguments()
        ) { backStackEntry ->
            val parcelableCredentialRequest = WalletDestination.ConsentPrompt
                .Argument.CREDENTIAL_REQUEST
                .extractParcelableFromBackStackEntry(backStackEntry)

            val docType = WalletDestination.ConsentPrompt
                .Argument.DOCUMENT_TYPE
                .extractFromBackStackEntry(backStackEntry)

            val docName = WalletDestination.ConsentPrompt
                .Argument.DOCUMENT_NAME
                .extractFromBackStackEntry(backStackEntry)

            val credentialId = WalletDestination.ConsentPrompt
                .Argument.CREDENTIAL_ID
                .extractFromBackStackEntry(backStackEntry)

            val verifierName = WalletDestination.ConsentPrompt
                .Argument.VERIFIER_NAME
                .extractFromBackStackEntry(backStackEntry) ?: ""

            ConsentPrompt(
                consentData = ConsentPromptData(
                    credentialRequest = parcelableCredentialRequest!!.unparcelize(),
                    docType = docType!!,
                    documentName = docName!!,
                    credentialId = credentialId!!, // needed to finish processing request and send response
                    verifierName = verifierName
                ),
                credentialTypeRepository = application.credentialTypeRepository
            )
        }
    }
}