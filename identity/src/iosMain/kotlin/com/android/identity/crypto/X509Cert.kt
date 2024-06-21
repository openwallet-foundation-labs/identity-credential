package com.android.identity.crypto

import com.android.identity.cbor.Bstr
import com.android.identity.cbor.DataItem
import com.android.identity.swiftcrypto.SwiftCrypto
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

actual class X509Cert actual constructor(actual val encodedCertificate: ByteArray) {
    actual val toDataItem: DataItem
        get() = Bstr(encodedCertificate)

    @OptIn(ExperimentalEncodingApi::class)
    actual fun toPem(): String {
        val sb = StringBuilder()
        sb.append("-----BEGIN CERTIFICATE-----\n")
        sb.append(Base64.Mime.encode(encodedCertificate))
        sb.append("\n-----END CERTIFICATE-----\n")
        return sb.toString()    }

    actual val ecPublicKey: EcPublicKey
        get() {
            // Need to dig the curve out of SubjectPublicKeyInfo in the certificate which
            // involves parsing ASN.1
            TODO("Not yet implemented")
        }

    @OptIn(ExperimentalForeignApi::class)
    private fun getEcPublicKey(ecCurve: EcCurve): EcPublicKey {
        val keyData = SwiftCrypto.x509CertGetKey(encodedCertificate.toNSData())
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
        return when (ecCurve) {
            EcCurve.P256, EcCurve.P384, EcCurve.P521,
            EcCurve.BRAINPOOLP256R1, EcCurve.BRAINPOOLP320R1,
            EcCurve.BRAINPOOLP384R1, EcCurve.BRAINPOOLP512R1 -> {
                val data = keyData.toByteArray()
                val componentSize = (data.size - 1)/2
                val x = data.sliceArray(IntRange(1, componentSize))
                val y = data.sliceArray(IntRange(componentSize + 1, data.size - 1))
                EcPublicKeyDoubleCoordinate(ecCurve, x, y)
            }
            else -> TODO("Need to add support for curve $ecCurve")
        }
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
