package com.android.mdl.appreader.settings

import com.android.mdl.appreader.VerifierApp
import com.android.mdl.appreader.issuerauth.getCommonName
import com.android.mdl.appreader.issuerauth.getOrganisation
import com.android.mdl.appreader.issuerauth.organisationalUnit
import com.android.mdl.appreader.issuerauth.vical.Vical
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
    );
    val sha1Fingerprint = hexWithSpaces(
        MessageDigest.getInstance("SHA-1").digest(
            this.encoded
        )
    );
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
        certificate = this
    )
}

fun Vical.toVicalItem(): VicalItem {
    val name = VerifierApp.vicalStoreInstance.determineFileName(this)
    val certificateItems: MutableList<CertificateItem> = mutableListOf()
    for (info in this.certificateInfos()) {
        if (info != null) {
            certificateItems.add(
                info.certificate
                    .toCertificateItem(info.docTypes.map { s -> s.toString() }.toList())
            )
        }
    }
    return VicalItem(
        title = name,
        vicalProvider = this.vicalProvider(),
        date = this.date(),
        vicalIssueID = this.vicalIssueID(),
        nextUpdate = this.nextUpdate(),
        certificateItems = certificateItems,
        vical = this
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