package com.android.identity.crypto

import com.android.identity.cbor.Bstr
import com.android.identity.cbor.DataItem
import com.android.identity.SwiftBridge
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalForeignApi::class)
actual class X509Cert actual constructor(actual val encodedCertificate: ByteArray) {
    actual val toDataItem: DataItem
        get() = Bstr(encodedCertificate)

    @OptIn(ExperimentalEncodingApi::class)
    actual fun toPem(): String {
        val sb = StringBuilder()
        sb.append("-----BEGIN CERTIFICATE-----\n")
        sb.append(Base64.Mime.encode(encodedCertificate))
        sb.append("\n-----END CERTIFICATE-----\n")
        return sb.toString()
    }

    actual val ecPublicKey: EcPublicKey
        get() {
            val keyData = SwiftBridge.x509CertGetKey(encodedCertificate.toNSData())
            if (keyData == null) {
                throw IllegalStateException("Error getting key")
            }
            /* From docs for SecKeyCopyExternalRepresentation()
             *
             * The method returns data in the PKCS #1 format for an RSA key. For an elliptic curve
             * public key, the format follows the ANSI X9.63 standard using a byte string of 04 || X
             * || Y. For an elliptic curve private key, the output is formatted as the public key
             * concatenated with the big endian encoding of the secret scalar, or 04 || X || Y || K.
             * All of these representations use constant size integers, including leading zeros
             * as needed.
             */
            val data = keyData.toByteArray()
            val componentSize = (data.size - 1)/2
            val x = data.sliceArray(IntRange(1, componentSize))
            val y = data.sliceArray(IntRange(componentSize + 1, data.size - 1))

            val ecCurve = when (componentSize) {
                32 -> EcCurve.P256
                48 -> EcCurve.P384
                65 -> EcCurve.P521
                else -> throw IllegalStateException("Unsupported component size ${componentSize}")
            }
            return EcPublicKeyDoubleCoordinate(ecCurve, x, y)
    }

    actual companion object {
        @OptIn(ExperimentalEncodingApi::class)
        actual fun fromPem(pemEncoding: String): X509Cert {
            val encoded = Base64.Mime.decode(pemEncoding
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .trim())
            return X509Cert(encoded)
        }

        actual fun fromDataItem(dataItem: DataItem): X509Cert {
            return X509Cert(dataItem.asBstr)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is X509Cert) return false

        return encodedCertificate.contentEquals(other.encodedCertificate)
    }

    override fun hashCode(): Int {
        return encodedCertificate.contentHashCode()
    }
}
