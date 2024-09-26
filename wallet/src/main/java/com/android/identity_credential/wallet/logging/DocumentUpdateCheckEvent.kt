package com.android.identity_credential.wallet.logging

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class DocumentUpdateCheckEvent(
    override var timestamp: Instant = Clock.System.now(),
    var documentId: String = "",
    ) : Event(timestamp)