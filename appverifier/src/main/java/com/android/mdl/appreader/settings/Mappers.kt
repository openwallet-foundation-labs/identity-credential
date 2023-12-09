package com.android.mdl.appreader.settings

import com.android.mdl.appreader.VerifierApp
import com.android.mdl.appreader.trustmanagement.getCommonName
import com.android.mdl.appreader.trustmanagement.getOrganisation
import com.android.mdl.appreader.trustmanagement.getSubjectKeyIdentifier
import com.android.mdl.appreader.trustmanagement.organisationalUnit
import java.lang.StringBuilder
import java.security.MessageDigest
import java.security.cert.X509Certificate

fun X509Certificate.toCertificateItem(docTypes: List<String> = emptyList()): CertificateItem {
    val subject = this.subjectX500Principal
    val issuer = this.issuerX500Principal
    val sha255Fingerprint = hexWithSpaces(
        MessageDigest.getInstance("SHA-256").digest(
            this.encoded
        )
    )
    val sha1Fingerprint = hexWithSpaces(
        MessageDigest.getInstance("SHA-1").digest(
            this.encoded
        )
    )
    val defaultValue = "<Not part of certificate>"

    return CertificateItem(
        title = subject.name,
        commonNameSubject = subject.getCommonName(defaultValue),
        organisationSubject = subject.getOrganisation(defaultValue),
        organisationalUnitSubject = subject.organisationalUnit(defaultValue),
        commonNameIssuer = issuer.getCommonName(defaultValue),
        organisationIssuer = issuer.getOrganisation(defaultValue),
        organisationalUnitIssuer = issuer.organisationalUnit(defaultValue),
        notBefore = this.notBefore,
        notAfter = this.notAfter,
        sha255Fingerprint = sha255Fingerprint,
        sha1Fingerprint = sha1Fingerprint,
        docTypes = docTypes,
        supportsDelete = VerifierApp.certificateStorageEngineInstance.get(this.getSubjectKeyIdentifier()) != null ,
        certificate = this
    )
}

private fun hexWithSpaces(byteArray: ByteArray): String {
    val stringBuilder = StringBuilder()
    byteArray.forEach {
        if (stringBuilder.isNotEmpty()) {
            stringBuilder.append(" ")
        }
        stringBuilder.append(String.format("%02X", it))
    }
    return stringBuilder.toString()
}