package org.multipaz.wallet

import org.multipaz.wallet.logging.EventLogger

data class EventInfo(
    val timestamp: String,
    val requesterInfo: EventLogger.RequesterInfo?,
    val requestedFields: String,
)
