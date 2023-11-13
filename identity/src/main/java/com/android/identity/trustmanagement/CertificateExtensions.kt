package com.android.identity.trustmanagement

import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal

private const val DIGITAL_SIGNATURE = 0
private const val KEY_CERT_SIGN = 5

fun X509Certificate.hasKeyUsageDocumentSigner(): Boolean {
    if (this.keyUsage == null)
    {
        return false
    }
    return this.keyUsage[DIGITAL_SIGNATURE]
}

fun X509Certificate.hasKeyUsageCaCertificate(): Boolean {
    if (this.keyUsage == null)
    {
        return false
    }
    return this.keyUsage[KEY_CERT_SIGN]
}

fun X500Principal.getCommonName(defaultValue: String): String {
    return readRdn(this.name, BCStyle.CN, defaultValue)
}

fun X500Principal.getOrganisation(defaultValue: String): String {
    return readRdn(this.name, BCStyle.O, defaultValue)
}

fun X500Principal.organisationalUnit(defaultValue: String): String {
    return readRdn(this.name, BCStyle.OU, defaultValue)
}

fun X500Principal.countryCode(defaultValue: String): String {
    return readRdn(this.name, BCStyle.C, defaultValue)
}

private fun readRdn(name: String, field: ASN1ObjectIdentifier, defaultValue: String): String {
    val x500name = X500Name(name)
    for (rdn in x500name.getRDNs(field)) {
        val attributes = rdn.typesAndValues
        for (attribute in attributes) {
            return attribute.value.toString()
        }
    }
    return defaultValue
}