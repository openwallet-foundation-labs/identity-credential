package com.android.identity.android.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Common interface for all Listeners defined in each _Helper class such as DeviceRetrievalHelper,
 * VerificationHelper, etc... so we can run an extension function
 */
interface HelperListener



/**
 * Extension function to simplify calling a Listener's callback functions in a coroutine, so long as
 * callbacks are not inhibited, otherwise the call will be ignored..
 *
 * ex:
 * scope.launchIfAllowed(inhibitCallbacks, listener) { onDeviceDisconnected(transportSpecificTermination) }
 *
 * There is private extension function [executeIfAllowed] inside all Helper classes ([DeviceRetrievalHelper], [VerificationHelper], etc..)
 * that wraps around this "scope extension" functionality to work for their own Listener interface.
 *
 * Sample use
 * listener.executeIfAllowed(inhibitCallbacks) { onHandoverSelectMessageSent() }
 *
 * This makes it very convenient (and straightforward) to call a Listener's function in a coroutine.
 */
fun < T : HelperListener> CoroutineScope?.launchIfAllowed(
    inhibitCallbacks: Boolean,
    listener: T?,
    callback: suspend T.() -> Unit
) {
    this?.run { // there is a (non-null) coroutine scope available to use
        listener?.let{// ignore making a callback if not valid
            inhibitCallbacks.ifFalse {
                launch {
                    callback.invoke(it)
                }
            }
        }
    }
}