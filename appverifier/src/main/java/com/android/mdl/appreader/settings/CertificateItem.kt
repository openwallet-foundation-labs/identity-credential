package com.android.mdl.appreader.settings

import java.security.cert.X509Certificate
import java.util.Date

data class CertificateItem(
    val title: String,
    val commonNameSubject: String,
    val organisationSubject: String,
    val organisationalUnitSubject: String,
    val commonNameIssuer: String,
    val organisationIssuer: String,
    val organisationalUnitIssuer: String,
    val notBefore: Date,
    val notAfter: Date,
    val sha255Fingerprint: String,
    val sha1Fingerprint: String,
    val docTypes: List<String>,
    val supportsDelete: Boolean,
    val certificate: X509Certificate?
) {
}