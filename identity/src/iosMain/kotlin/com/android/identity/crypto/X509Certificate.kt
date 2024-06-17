package com.android.identity.crypto

import com.android.identity.cbor.Bstr
import com.android.identity.cbor.DataItem
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

actual class X509Certificate actual constructor(actual val encodedCertificate: ByteArray) {
    actual val toDataItem: DataItem
        get() = Bstr(encodedCertificate)

    @OptIn(ExperimentalEncodingApi::class)
    actual fun toPem(): String {
        val sb = StringBuilder()
        sb.append("-----BEGIN CERTIFICATE-----\n")
        sb.append(Base64.Mime.encode(encodedCertificate))
        sb.append("\n-----END CERTIFICATE-----\n")
        return sb.toString()    }

    actual fun verify(signingCertificate: X509Certificate): Boolean {
        TODO("Not yet implemented")
    }

    actual val ecPublicKey: EcPublicKey
        get() {
            TODO("Not yet implemented")
        }

    actual companion object {
        @OptIn(ExperimentalEncodingApi::class)
        actual fun fromPem(pemEncoding: String): X509Certificate {
            val encoded = Base64.Mime.decode(pemEncoding
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .trim())
            return X509Certificate(encoded)
        }

        actual fun fromDataItem(dataItem: DataItem): X509Certificate {
            return X509Certificate(dataItem.asBstr)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is X509Certificate) return false

        return encodedCertificate.contentEquals(other.encodedCertificate)
    }

    override fun hashCode(): Int {
        return encodedCertificate.contentHashCode()
    }
}
