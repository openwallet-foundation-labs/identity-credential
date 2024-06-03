package com.android.identity.flow.environment

import kotlinx.coroutines.flow.SharedFlow

/**
 * Simple interface to convey notifications to registered clients.
 */
interface Notifications {
    /**
     * Sends a notification to a target.
     *
     * Implementations of this method must also emit the notification on [eventFlow].
     *
     * @param targetId the target of the notification.
     * @param payload the payload of the notification.
     */
    suspend fun emit(targetId: String, payload: ByteArray)

    /**
     * A [SharedFlow] which can be used to listen in on emitted notifications.
     */
    val eventFlow: SharedFlow<Pair<String, ByteArray>>
}
