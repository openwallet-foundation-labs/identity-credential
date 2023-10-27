package com.android.identity.securearea.cloud

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.DataItem
import com.android.identity.cbor.Simple
import com.android.identity.crypto.CertificateChain
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.crypto.EcPublicKey
import java.io.ByteArrayInputStream

class MessageParser(encodedCbor: ByteArray, expectedCommand: String) {
    private val items: List<DataItem>
    private var idx: Int

    init {
        items = Cbor.decode(encodedCbor).asArray
        idx = 0
        val command = string
        check(command == expectedCommand) { "Expected command $expectedCommand, got $command" }
    }

    val next: DataItem
        get() {
            check(idx < items.size) { "Requested more items than available" }
            return items[idx++]
        }

    private fun skipNextIfNull(): Boolean {
        if (items[idx] == Simple.NULL) {
            idx++
            return true
        }
        return false
    }

    val string: String?
        get() = if (skipNextIfNull()) {
            null
        } else next.asTstr

    val byteString: ByteArray?
        get() = if (skipNextIfNull()) {
            null
        } else next.asBstr

    val int: Int
        get() = next.asNumber.toInt()

    val long: Long
        get() = next.asNumber

    val publicKey: EcPublicKey?
        get() = if (skipNextIfNull()) {
            null
        } else {
            Cbor.decode(byteString!!).asCoseKey.ecPublicKey
        }

    val privateKey: EcPrivateKey?
        get() = if (skipNextIfNull()) {
            null
        } else {
            Cbor.decode(byteString!!).asCoseKey.ecPrivateKey
        }

    val certificateChain: CertificateChain?
        get() {
            if (skipNextIfNull()) {
                return null
            }
            return CertificateChain.fromDataItem(Cbor.decode(byteString!!))
        }
}