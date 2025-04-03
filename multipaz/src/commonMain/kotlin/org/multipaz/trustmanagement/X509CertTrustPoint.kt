package org.multipaz.trustmanagement

import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.crypto.X509Cert
import org.multipaz.util.toHex

/**
 * A [TrustPoint] for trusting a Certificate Authority using X.509 certificates
 *
 * @param certificate the root X509 certificate for the CA.
 */
data class X509CertTrustPoint(
    val certificate: X509Cert,
    override val metadata: TrustPointMetadata,
    override val trustManager: TrustManager
) : TrustPoint(metadata) {

    override val identifier: String
        get() = "x509-ski:${certificate.subjectKeyIdentifier!!.toHex()}"
}
