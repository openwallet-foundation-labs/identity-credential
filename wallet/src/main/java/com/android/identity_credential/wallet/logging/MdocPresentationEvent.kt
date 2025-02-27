package com.android.identity_credential.wallet.logging

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.io.bytestring.ByteString

class MdocPresentationEvent (
    timestamp: Instant = Clock.System.now(),
    var documentId: String = "",
    var sessionTranscript: ByteString,
    var deviceRequestCbor: ByteString,
    var deviceResponseCbor: ByteString,
    val requesterInfo: EventLogger.RequesterInfo = EventLogger.RequesterInfo(
        requester = EventLogger.Requester.Anonymous(),
        shareType = EventLogger.ShareType.UNKNOWN),
    id: String = ""
) : Event(timestamp, id)