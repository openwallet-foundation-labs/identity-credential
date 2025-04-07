package org.multipaz.rpc.handler

import org.multipaz.cbor.DataItem

/**
 * A low-level interface to convey notifications to registered clients.
 */
interface RpcNotifications {
    /**
     * Sends a notification to a target.
     *
     * @param target flow name/path that emits notification
     * @param state current flow state
     * @param notification notification payload
     */
    suspend fun emit(target: String, state: DataItem, notification: DataItem)
}
