package com.android.identity.appsupport.ui.x509chain

import com.android.identity.asn1.ASN1Integer
import com.android.identity.asn1.OID
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.X500Name
import com.android.identity.crypto.X509Cert
import com.android.identity.util.unsignedBigIntToString
import com.android.identity.util.toHex
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.offsetAt
import kotlin.time.Duration.Companion.hours

/** All data fields of the Cert as needed for display as text strings. */
data class X509VewData(
    val type: String,
    val serialNumber: String,
    val version: String,
    val issued: String,
    val expired: String,
    val subjectCountry: String,
    val subjectCommonName: String,
    val subjectOrg: String,
    val issuerCountry: String,
    val issuerCommonName: String,
    val issuerOrg: String,
    val pkAlgorithm: String,
    val pkNamedCurve: String,
    val pkValue: String,
) {
    companion object {
        /** This data is used for the simple preview of the composable,
         * but would work only in an Android project a.t.m.
         */
        val previewCertificate: X509Cert
            get() {
                val key = Crypto.createEcPrivateKey(EcCurve.P256)
                val now = Instant.fromEpochSeconds(Clock.System.now().epochSeconds)
                val x509 = X509Cert.Builder(
                    publicKey = key.publicKey,
                    signingKey = key,
                    signatureAlgorithm = key.curve.defaultSigningAlgorithm,
                    serialNumber = ASN1Integer(1),
                    subject = X500Name.fromName("CN=Foobar1"),
                    issuer = X500Name.fromName("CN=Foobar2"),
                    validFrom = now - 1.hours,
                    validUntil = now + 1.hours
                )
                    .includeSubjectKeyIdentifier()
                    .includeAuthorityKeyIdentifierAsSubjectKeyIdentifier()
                    .build()
                return x509
            }
    }
}

/** Map Certificate data to View fields */
fun X509Cert.toX509ViewData(): X509VewData {
    val notAvail = "<Not Part Of Certificate>" //sme as in Chrome cert view

    val type = "X.509 Certificate"

    val serialNumber: String = with(serialNumber) {
        if (value.size <= 8) {
            toLong().toString()
        } else {
            value.unsignedBigIntToString()
        }
    }

    val version = version.toString()

    val issued = validityNotBefore.formatWithIsoFormat()

    val expired = validityNotAfter.formatWithIsoFormat()

    val subjectCountry = subject.components[OID.COUNTRY_NAME.oid]?.value ?: notAvail

    val subjectCommonName = subject.components[OID.COMMON_NAME.oid]?.value ?: notAvail

    val subjectOrg = subject.components[OID.ORGANIZATION_NAME.oid]?.value ?: notAvail

    val issuerCountry = issuer.components[OID.COUNTRY_NAME.oid]?.value ?: notAvail

    val issuerCommonName = issuer.components[OID.COMMON_NAME.oid]?.value ?: notAvail

    val issuerOrg = issuer.components[OID.ORGANIZATION_NAME.oid]?.value ?: notAvail

    val pkAlgorithm: String = runCatching { signatureAlgorithm.name }
        .getOrElse { e -> e.message ?: notAvail }

    val pkNamedCurve: String = runCatching { ecPublicKey.curve.name }
        .getOrElse { e -> e.message ?: notAvail }

    val pkValue: String = runCatching { this.signature.toHex(byteDivider = " ") }
        .getOrElse { e -> e.message ?: notAvail }

    return X509VewData(
        type,
        serialNumber,
        version,
        issued,
        expired,
        subjectCountry,
        subjectCommonName,
        subjectOrg,
        issuerCountry,
        issuerCommonName,
        issuerOrg,
        pkAlgorithm,
        pkNamedCurve,
        pkValue,
    )
}

/**
 * The Date Time format can be customized by creating specific DateTimeComponents<> structure
 * as needed for KMP compatibility.
 */
private fun Instant.formatWithIsoFormat(timeZone: TimeZone = TimeZone.currentSystemDefault()): String =
    format(DateTimeComponents.Formats.ISO_DATE_TIME_OFFSET, timeZone.offsetAt(this))

