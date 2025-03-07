package org.multipaz_credential.wallet.navigation

import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.navArgument
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.Charset


sealed class WalletDestination(val routeEnum: Route) {
    /**
     * Can be overriden by any Destination's inner class using its Argument enum to return arguments.
     */
    open fun getArguments(): List<NamedNavArgument> = listOf()

    // String representation of this Destination's route
    val route = routeEnum.routeName
    val routeWithArgs = routeEnum.routePathWithArguments

    // Screens with no arguments
    object Main : WalletDestination(Route.MAIN)
    object About : WalletDestination(Route.ABOUT)
    object Settings : WalletDestination(Route.SETTINGS)
    object SettingsProximitySharing : WalletDestination(Route.SETTINGS_PROXIMITY_SHARING)
    object AddToWallet : WalletDestination(Route.ADD_TO_WALLET)
    object ProvisionDocument : WalletDestination(Route.PROVISION_DOCUMENT)

    object QrEngagement : WalletDestination(Route.QR_ENGAGEMENT)

    object Reader : WalletDestination(Route.READER)

    object EventDetails : WalletDestination(Route.EVENT_DETAILS) {
        enum class Argument(val argument: NamedNavArgument) {
            REQUESTED_FIELDS(
                navArgument("requestedFields") {
                    type = NavType.StringType
                    nullable = false
                }
            );

            fun extractFromBackStackEntry(backStackEntry: NavBackStackEntry): String? {
                return backStackEntry.arguments?.getString(argument.name)
            }
        }

        override fun getArguments(): List<NamedNavArgument> =
            Argument.values().map { it.argument }
    }

    // Screens with arguments
    object DocumentInfo : WalletDestination(Route.DOCUMENT_INFO) {
        /**
         * enum class Argument defines all the various (optional) arguments that can be passed to
         * the route (DOCUMENT_INFO)
         */
        enum class Argument(val argument: NamedNavArgument) {
            DOCUMENT_ID( // this argument is needed
                navArgument("documentId") {
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
            ACTIVITIES( // Add a new argument for the "activities" section
                navArgument("activities") {
                    type = NavType.StringType
                    nullable = true
                }
            ),
            CREDENTIALS( // this argument is optional, sections like 'credentials' can be passed
                navArgument("section") {
                    type = NavType.StringType
                    nullable = true
                }
            ),
            AUTH_REQUIRED( // this argument is optional, sections like 'details' can be passed
                navArgument("auth_required") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            ),

            ;

            // easily extract any argument from `backStackEntry`
            fun extractFromBackStackEntry(backStackEntry: NavBackStackEntry) =
                backStackEntry.arguments?.getString(argument.name)

            fun extractBooleanFromBackStackEntry(backStackEntry: NavBackStackEntry) =
                backStackEntry.arguments?.getBoolean(argument.name)
        }

        /**
         * Return a list of all arguments that can be optionally passed to this route
         */
        override fun getArguments(): List<NamedNavArgument> =
            Argument.values().map { it.argument }.toList()
    }

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
                navArgument("save_state") {
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
                        return URLDecoder.decode(argPairStr.split("=")[1], "UTF-8")
                    }
                }
                return null
            }
        }
    }


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
                    argumentValue.toString()
                }
            val argName = when (this) {
                is DocumentInfo -> {
                    enumArgumentObj as DocumentInfo.Argument
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
            val encodedArg = URLEncoder.encode(argVal, "UTF-8")
            ret += "$argName=$encodedArg&"
        }
        return ret
    }
}



/**
 * A Route is used to define identifiers of Screens that can be navigated to. A Route can also be used
 * for performing navigation events (popBackStack) that lead the user to a different Screen.
 */
enum class Route(val routeName: String, val argumentsStr: String = "") {
    MAIN("main"),
    ABOUT("about"),
    SETTINGS("settings"),
    SETTINGS_PROXIMITY_SHARING("settings_proximity_sharing"),
    ADD_TO_WALLET("add_to_wallet"),
    DOCUMENT_INFO("document_info",
        "documentId={documentId}&section={section}&auth_required={auth_required}"),
    ACTIVITIES("activities","documentId={documentId}"),
    PROVISION_DOCUMENT("provision_document"),
    QR_ENGAGEMENT("qr_engagement"),
    READER("reader_select_request"),
    EVENT_DETAILS("event_details"),

    // a Route for popping the back stack showing a different Screen
    POP_BACK_STACK("pop_back_stack"),
    ;

    // return route name formatted to show arguments, such as 'card_info?arg1={, if any are provided
    val routePathWithArguments: String
        get() = if (argumentsStr.isEmpty()) routeName else "$routeName?$argumentsStr"
}

