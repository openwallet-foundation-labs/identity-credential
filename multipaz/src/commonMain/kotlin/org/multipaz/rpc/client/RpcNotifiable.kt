package org.multipaz.rpc.client

import kotlinx.coroutines.flow.FlowCollector

interface RpcNotifiable<out NotificationT> {
    /**
     * Collect notifications for this object.
     *
     * [RpcNotifiable] interface could be thought of implementing Kotlin `Flow`, but `Flow`
     * documentation suggests it is not stable for inheritance yet.
     */
    suspend fun collect(collector: FlowCollector<NotificationT>) {
        // This method is unused on the back-end side (as notifications come from the back-end,
        // and collected on the front-end). RPC stubs will provide their own implementation for
        // this method. If an object is designed to work both through RPC dispatch and by passing
        // a "naked" implementation object (i.e. without any stubs), an implementation for this
        // method must be provided. In most cases calling `collectImpl` extension method generated
        // by the annotation processor is sufficient. `dispose` typically also has to be
        // implemented in this case similarly by calling `disposeImpl`
        throw UnsupportedOperationException()
    }

    /**
     * Must be called when this object is no longer used and should not receive notifications.
     */
    suspend fun dispose() {}
}