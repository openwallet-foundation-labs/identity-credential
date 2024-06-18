package com.android.identity.crypto

import com.android.identity.cbor.Bstr
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.DataItem

/**
 * A chain of certificates.
 *
 * @param certificates the certificates in the chain.
 */
data class X509CertChain(
    val certificates: List<X509Cert>
) {

    /**
     * Encodes the certificate chain as CBOR.
     *
     * If the chain has only one item a [Bstr] with the sole certificate is returned.
     * Otherwise an array of [Bstr] is returned.
     *
     * Use [fromDataItem] to decode the returned data item.
     */
    val toDataItem: DataItem
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
        fun fromDataItem(dataItem: DataItem): X509CertChain {
            val certificates: List<X509Cert> =
                if (dataItem is CborArray) {
                    dataItem.items.map { item -> item.asX509Cert }.toList()
                } else {
                    listOf(dataItem.asX509Cert)
                }
            return X509CertChain(certificates)
        }
    }
}
