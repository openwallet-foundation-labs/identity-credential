package org.multipaz_credential.wallet

import org.multipaz_credential.wallet.logging.EventLogger

data class EventInfo(
    val timestamp: String,
    val requesterInfo: EventLogger.RequesterInfo?,
    val requestedFields: String,
)
