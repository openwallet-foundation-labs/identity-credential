package com.android.identity.crypto

import com.android.identity.cbor.Bstr
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.DataItem
import java.security.cert.X509Certificate

/**
 * A chain of certificates.
 *
 * @param certificates the certificates in the chain.
 */
data class CertificateChain(
    val certificates: List<Certificate>
) {

    /**
     * Encodes the certificate chain as CBOR.
     *
     * If the chain has only one item a [Bstr] with the sole certificate is returned.
     * Otherwise an array of [Bstr] is returned.
     *
     * Use [fromDataItem] to decode the returned data item.
     */
    val dataItem: DataItem
        get() {
            if (certificates.size == 1) {
                return certificates[0].dataItem
            } else {
                val builder = CborArray.builder()
                certificates.forEach { certificate -> builder.add(certificate.dataItem) }
                return builder.end().build()
            }
        }

    companion object {

        /**
         * Decodes a certificate chain from CBOR.
         *
         * See [Certificate.dataItem] for the expected encoding.
         *
         * @param dataItem the CBOR data item to decode.
         * @return the certificate chain.
         */
        fun fromDataItem(dataItem: DataItem): CertificateChain {
            val certificates = mutableListOf<Certificate>()
            if (dataItem is CborArray) {
                dataItem.items.forEach() { dataItem -> certificates.add(dataItem.asCertificate) }
            } else {
                certificates.add(dataItem.asCertificate)
            }
            return CertificateChain(certificates)
        }
    }
}

// TODO: move to identity-jvm library

/**
 * Converts the certificate chain to a list of Java X.509 certificates.
 */
val CertificateChain.javaX509Certificates: List<X509Certificate>
    get() {
        val ret = mutableListOf<X509Certificate>()
        certificates.forEach() { certificate -> ret.add(certificate.javaX509Certificate) }
        return ret
    }
