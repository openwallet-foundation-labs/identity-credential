package com.android.identity_credential.wallet.logging

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class MdocPresentationEvent (
    override var timestamp: Instant = Clock.System.now(),
    var documentId: String = "",
    var sessionTranscript: ByteArray,
    var deviceRequestCbor: ByteArray,
    var deviceResponseCbor: ByteArray,
) : Event(timestamp)