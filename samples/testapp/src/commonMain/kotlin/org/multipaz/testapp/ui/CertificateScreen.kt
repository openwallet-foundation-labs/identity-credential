package org.multipaz.testapp.ui

import androidx.compose.runtime.Composable
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborArray
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.util.fromBase64Url

/**
 * Populate and compose the certificate chain screen.
 * Leveraging overloaded composables from CertificateViewer.kt.
 *
 * @param encodedCertificate Base64 encoded Cbor data for the certificate/chain to display.
 */
@Composable
fun CertificateScreen(encodedCertificate: String) {
    when (val dataItem = Cbor.decode(encodedCertificate.fromBase64Url())) {
        is CborArray -> CertificateViewer(x509CertChain = X509CertChain.fromDataItem(dataItem))
        else -> CertificateViewer(x509Cert = X509Cert.fromDataItem(dataItem))
    }
}
