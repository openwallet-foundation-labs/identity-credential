package com.android.mdl.appreader.trustmanagement

import org.multipaz.util.toHex
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier
import java.security.cert.X509Certificate

/**
 * Get the Subject Key Identifier Extension from the
 * X509 certificate in hexadecimal format.
 */
fun X509Certificate.getSubjectKeyIdentifier(): String {
    val extensionValue = this.getExtensionValue(Extension.subjectKeyIdentifier.id)
    val octets = DEROctetString.getInstance(extensionValue).octets
    val subjectKeyIdentifier = SubjectKeyIdentifier.getInstance(octets)
    return subjectKeyIdentifier.keyIdentifier.toHex()
}