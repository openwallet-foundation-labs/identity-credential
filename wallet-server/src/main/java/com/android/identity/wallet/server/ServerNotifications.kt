package com.android.identity.wallet.server

import com.android.identity.flow.environment.Notifications
import com.android.identity.util.Logger

class ServerNotifications : Notifications {
    companion object {
        private const val TAG = "ServerNotifications"
    }
    override suspend fun emitNotification(
        clientId: String,
        issuingAuthorityIdentifier: String,
        documentIdentifier: String
    ) {
        Logger.w(TAG, "emitNotification not yet implemented - " +
                "clientId:$clientId " +
                "issuingAuthorityIdentifier:$issuingAuthorityIdentifier " +
                "documentIdentifier:$documentIdentifier")
    }
}