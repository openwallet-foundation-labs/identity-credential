package com.android.identity_credential.wallet.navigation

import android.os.Build
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.android.identity.credential.CredentialRequest
import com.android.identity_credential.wallet.util.ParcelableCredentialRequest


sealed class WalletDestination(val routeEnum: Route) : DestinationArguments() {

    // String representation of this Destination's route
    val route = routeEnum.routeName
    val routeWithArgs = routeEnum.routePathWithArguments

    // Screens with no arguments

    /**
     * Destination: Main screen
     */
    object Main : WalletDestination(Route.MAIN)

    /**
     * Destination: About screen
     */
    object About : WalletDestination(Route.ABOUT)

    /**
     * Destination: Add to wallet screen
     */
    object AddToWallet : WalletDestination(Route.ADD_TO_WALLET)

    /**
     * Destination: Provision Credential screen
     */
    object ProvisionCredential : WalletDestination(Route.PROVISION_CREDENTIAL)

    /**
     * Destination: QR Engagement screen
     */
    object QrEngagement : WalletDestination(Route.QR_ENGAGEMENT)



    //////////////////////   Screens with arguments   //////////////////////


    /**
     * Destination: Credential Info screen accepts arguments
     */
    object CredentialInfo : WalletDestination(Route.CREDENTIAL_INFO) {
        /**
         * enum class Argument defines all the various (optional) arguments that can be passed to the route (CREDENTIAL_INFO)
         */
        enum class Argument(val argument: NamedNavArgument) {
            CREDENTIAL_ID( // this argument is needed
                navArgument("credentialId") {
                    type = NavType.StringType
                    nullable = false // and cannot be optional
                }
            ),
            SECTION( // this argument is optional, sections like 'details' can be passed
                navArgument("section") {
                    type = NavType.StringType
                    nullable = true
                }
            ),

            ;

            // easily extract any String argument from `backStackEntry`, ex: CredentialInfo.Argument.CREDENTIAL_ID.extractFromBackStackEntry()
            fun extractFromBackStackEntry(backStackEntry: NavBackStackEntry) =
                backStackEntry.arguments?.getString(argument.name)
        }

        /**
         * Return a list of all arguments that can be optionally passed to this route
         */
        override fun getArguments(): List<NamedNavArgument> =
            Argument.values().map { it.argument }.toList()
    }

    /**
     * Destination: Consent Prompt modal dialog accepts mandatory arguments
     */
    object ConsentPrompt : WalletDestination(Route.CONSENT_PROMPT) {
        /**
         * enum class Argument defines all the various (optional) arguments that can be passed to the route (CONSENT_PROMPT)
         */
        enum class Argument(val argument: NamedNavArgument) {
            CREDENTIAL_REQUEST(
                navArgument("credentialRequest") {
                    type = CredentialRequestParamType()
                }
            ),
            DOCUMENT_TYPE(
                navArgument("docType") {
                    type = NavType.StringType
                }
            ),

            DOCUMENT_NAME(
                navArgument("docName") {
                    type = NavType.StringType
                }
            ),

            CREDENTIAL_ID(
                navArgument("credentialId") {
                    type = NavType.StringType
                }
            ),

            VERIFIER_NAME(
                navArgument("verifierName") {
                    type = NavType.StringType
                    nullable = true
                }
            ),
            ;

            // easily extract a Parcelable from `backStackEntry`,
            fun extractParcelableFromBackStackEntry(backStackEntry: NavBackStackEntry) =
                when (this) {
                    CREDENTIAL_REQUEST -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                            backStackEntry.arguments?.getParcelable(
                                argument.name,
                                ParcelableCredentialRequest::class.java
                            )
                        else
                            backStackEntry.arguments?.getParcelable<ParcelableCredentialRequest>(
                                argument.name
                            )

                    }
                    else -> null
                }

            // easily extract any String argument from `backStackEntry`
            fun extractFromBackStackEntry(backStackEntry: NavBackStackEntry) =
                backStackEntry.arguments?.getString(argument.name)
        }

        /**
         * Return a list of all arguments that can be optionally passed to this route
         */
        override fun getArguments(): List<NamedNavArgument> =
            Argument.values().map { it.argument }.toList()
    }


    /**
     * Destination: the remaining screen after performing a "Pop back stack" operation,
     * accepts optional arguments
     */
    object PopBackStack : WalletDestination(Route.POP_BACK_STACK) {
        enum class Argument(val argument: NamedNavArgument) {
            ROUTE( // this argument is needed
                navArgument("route") {
                    type = NavType.StringType
                    nullable = false // and cannot be optional
                }
            ),
            INCLUSIVE( // also not optional
                navArgument("inclusive") {
                    type = NavType.BoolType
                }
            ),

            SAVE_STATE( // optional
                navArgument("saveState") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )

            ;

            /**
             * If route for navigation is 'pop_back_stack' then extract any
             * arguments such as route to pop back to, inclusive and save state.
             * It expects the route to be produced by the sealed class's function [getRouteWithArguments]
             * which produces a route string of the following format for example
             *  pop_back_stack?route=main&inclusive=false
             */
            fun extractFromRouteString(routeWithArgs: String): String? {
                if (!routeWithArgs.contains("?")) {
                    return null
                }
                // get everything after ? and split on &
                val argsStrList = routeWithArgs.split("?")[1].split("&")
                argsStrList.forEach { argPairStr ->
                    // find matching pair and return the string value
                    if (argPairStr.startsWith("${argument.name}=")) {
                        return argPairStr.split("=")[1]
                    }
                }
                return null
            }
        }
    }


    /** sealed class functions **/


    /**
     * Generate and return the string representation of the route of the currently referenced WalletDestination
     * with the list of passed arguments.
     */
    fun <T, V> getRouteWithArguments(
        // list of arguments were T is enum "Argument" of a sealed class defining acceptable arguments
        // and V is the value for the argument; it can be a list in which the default "joinToString()" provides the contents of the list
        argumentList: List<Pair<T, V>>
    ): String {
        if (argumentList.isEmpty()) return route

        // prepare to form the route with arguments
        var ret = "$route?"
        argumentList.forEach { argPair ->
            // the Argument enum for any type
            val enumArgumentObj = argPair.first
            val argumentValue = argPair.second
            val argVal =
                if (argumentValue is List<*>) {
                    argumentValue.joinToString()
                } else {
                    argumentValue
                }
            val argName = when (this) {
                is CredentialInfo -> {
                    enumArgumentObj as CredentialInfo.Argument
                    enumArgumentObj.argument.name
                }

                is PopBackStack -> {
                    enumArgumentObj as PopBackStack.Argument
                    enumArgumentObj.argument.name
                }

                else -> {
                    throw Exception("Error! Attempted to pass argument '$enumArgumentObj' to WalletDestination '$this' but the Argument object is not defined in 'getRouteWithArguments'")
                }
            }
            ret += "$argName=$argVal&"
        }
        return ret
    }
}

/**
 * Allows any WalletDestination to override this function and return a list of arguments that can be passed
 * during navigation to the destination's route.
 */
abstract class DestinationArguments {
    /**
     * Used during the NavHost definition of a route.
     * WalletDestinations without arguments don't need to override this function.
     */
    open fun getArguments(): List<NamedNavArgument> = listOf()
}

/**
 * A Route is used to define identifiers of Screens that can be navigated to. A Route can also be used
 * for performing navigation events (popBackStack) which ultimately lead the user to a different Screen.
 */
enum class Route(val routeName: String, val argumentsStr: String = "") {
    MAIN("main"),
    ABOUT("about"),
    ADD_TO_WALLET("add_to_wallet"),
    CREDENTIAL_INFO("credential_info", "credentialId={credentialId}&section={section}"),
    PROVISION_CREDENTIAL("provision_credential"),
    QR_ENGAGEMENT("qr_engagement"),
    CONSENT_PROMPT("consent_prompt"),

    // a Route for popping the back stack showing a different Screen
    POP_BACK_STACK("pop_back_stack"),
    ;

    // return route name formatted to show arguments, such as 'credential_info?arg1={, if any are provided
    val routePathWithArguments: String
        get() = if (argumentsStr.isEmpty()) routeName else "$routeName?$argumentsStr"
}

