package com.android.identity.mdoc.nfc

import com.android.identity.cbor.DataItem
import com.android.identity.mdoc.connectionmethod.ConnectionMethod
import com.android.identity.mdoc.transport.MdocTransport
import kotlin.time.Duration

data class NfcEngagementResult(
    val encodedDeviceEngagement: ByteArray,
    val handover: DataItem,
    val transport: MdocTransport,
    val elapsedTime: Duration,
)