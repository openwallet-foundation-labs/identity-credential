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
        get() = if (certificates.size == 1) {
            certificates[0].toDataItem
        } else {
            CborArray.builder().run {
                certificates.forEach { certificate -> add(certificate.toDataItem) }
                return end().build()
            }

        }

    companion object {
        /**
         * Decodes a certificate chain from CBOR.
         *
         * See [Certificate.toDataItem] for the expected encoding.
         *
         * @param dataItem the CBOR data item to decode.
         * @return the certificate chain.
         */
        fun fromDataItem(dataItem: DataItem): CertificateChain {
            val certificates: List<Certificate> =
                if (dataItem is CborArray) {
                    dataItem.items.map { item -> item.asCertificate }.toList()
                } else {
                    listOf(dataItem.asCertificate)
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
    get() = certificates.map { certificate -> certificate.javaX509Certificate }
