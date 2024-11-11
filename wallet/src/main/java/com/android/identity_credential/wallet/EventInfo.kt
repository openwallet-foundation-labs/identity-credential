package com.android.identity_credential.wallet

import com.android.identity_credential.wallet.logging.EventLogger

data class EventInfo(
    val timestamp: String,
    val requesterInfo: EventLogger.RequesterInfo?,
    val requestedFields: String,
)
