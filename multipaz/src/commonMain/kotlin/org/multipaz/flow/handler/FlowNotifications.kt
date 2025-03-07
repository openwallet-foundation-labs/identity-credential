package org.multipaz.flow.handler

import org.multipaz.cbor.DataItem

/**
 * A low-level interface to convey notifications to registered clients.
 */
interface FlowNotifications {
    /**
     * Sends a notification to a target.
     *
     * @param flowName flow name/path that emits notification
     * @param state current flow state
     * @param notification notification payload
     */
    suspend fun emit(flowName: String, state: DataItem, notification: DataItem)
}
