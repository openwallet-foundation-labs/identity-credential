package org.multipaz_credential.wallet.logging

import org.multipaz.cbor.annotation.CborSerializable
import kotlinx.datetime.Instant

@CborSerializable
sealed class Event(
    open val timestamp: Instant,
    var id: String = ""  // filled by EventLogger
) {
    companion object
}