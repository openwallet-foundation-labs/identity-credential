package com.android.identity.crypto

import com.android.identity.cbor.DataItem

/**
 * A data type for a X509 certificate.
 *
 * @param encodedCertificate the bytes of the X.509 certificate.
 */
expect class X509Certificate(
    encodedCertificate: ByteArray
) {
    /**
     * The encoded certificate.
     */
    val encodedCertificate: ByteArray

    /**
     * Gets an [DataItem] with the encoded X.509 certificate.
     */
    val toDataItem: DataItem

    /**
     * Encode this certificate in PEM format
     *
     * @return a PEM encoded string.
     */
    fun toPem(): String

    /**
     * The public key in the certificate.
     *
     * @throws IllegalStateException if the public key for the certificate isn't a EC key.
     */
    val ecPublicKey: EcPublicKey

    /**
     * Verifies that the certificate was signed with a public key from a given certificate.
     */
    fun verify(signingCertificate: X509Certificate): Boolean

    companion object {
        /**
         * Creates a [X509Certificate] from a PEM encoded string.
         *
         * @param pemEncoding the PEM encoded string.
         * @return a new [X509Certificate].
         */
        fun fromPem(pemEncoding: String): X509Certificate

        /**
         * Gets a [X509Certificate] from a [DataItem].
         *
         * @param dataItem the data item, must have been encoded with [toDataItem].
         * @return the certificate.
         */
        fun fromDataItem(dataItem: DataItem): X509Certificate
    }
}
