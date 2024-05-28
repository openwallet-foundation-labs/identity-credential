package com.android.identity.flow.environment

/**
 * Simple interface to convey notifications to registered clients.
 */
interface Notifications {
    suspend fun emitNotification(clientId: String, issuingAuthorityIdentifier: String, documentIdentifier: String)
}
