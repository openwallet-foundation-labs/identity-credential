package com.android.identity.securearea.cloud

import com.android.identity.cbor.Bstr
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.Simple
import com.android.identity.cbor.Tstr
import com.android.identity.crypto.CertificateChain
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.crypto.EcPublicKey

class MessageGenerator(command: String) {
    private val builder = CborArray.builder()

    init {
        add(command)
    }

    fun add(value: ByteArray?): MessageGenerator {
        builder.add(value?.let { Bstr(it) } ?: Simple.NULL)
        return this
    }

    fun add(value: String?): MessageGenerator {
        builder.add(value?.let { Tstr(it) } ?: Simple.NULL)
        return this
    }

    fun add(value: Int): MessageGenerator {
        builder.add(value.toLong())
        return this
    }

    fun add(value: Long): MessageGenerator {
        builder.add(value)
        return this
    }

    fun add(value: EcPublicKey?): MessageGenerator {
        if (value == null) {
            builder.add(Simple.NULL)
        } else {
            builder.add(Cbor.encode(value.toCoseKey().toDataItem))
        }
        return this
    }

    fun add(value: EcPrivateKey?): MessageGenerator {
        if (value == null) {
            builder.add(Simple.NULL)
        } else {
            builder.add(Cbor.encode(value.toCoseKey().toDataItem))
        }
        return this
    }

    fun add(value: CertificateChain?): MessageGenerator {
        if (value == null) {
            builder.add(Simple.NULL)
        } else {
            builder.add(Cbor.encode(value.dataItem))
        }
        return this
    }

    fun generate(): ByteArray {
        return Cbor.encode(builder.end().build())
    }
}