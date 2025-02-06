package com.android.identity.testapp.ui

import androidx.compose.runtime.Composable
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.crypto.X509Cert
import com.android.identity.crypto.X509CertChain
import com.android.identity.util.fromBase64Url
import org.multipaz.compose.ui.certificateviewer.CertificateViewer

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
