package com.android.identity.appsupport.ui.certificateviewer

import com.android.identity.asn1.ASN1
import com.android.identity.asn1.OID
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.X509Cert
import com.android.identity.util.unsignedBigIntToString
import com.android.identity.util.toHex
import kotlinx.datetime.Instant
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.DateTimeComponents.Companion.Format
import kotlinx.datetime.format.DateTimeFormat
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlin.collections.mapValues

/**
 * View Model immutable data.
 * All data fields of the Certificate as needed for display on the screen.
 * Also defining and using two extension methods to map Certificate fields.
 */
internal data class CertificateViewData(
    val type: String,
    val serialNumber: String,
    val version: String,
    val issued: String,
    val expired: String,
    val subject: Map<String, String> = emptyMap(),
    val issuer: Map<String, String> = emptyMap(),
    val pkAlgorithm: String,
    val pkNamedCurve: String,
    val pkValue: String,
    val extensions: List<Triple<Boolean, String, String>> = emptyList(),
) {

    companion object {

        val notAvail = "" // Composables wouldn't show keys with empty data.

        /** Map Certificate data to View fields. */
        fun from(cert: X509Cert): CertificateViewData {
            val type = "X.509"

            val version = runCatching { cert.version.toString() }
                .getOrElse { _ -> "" }
                .let { versionString ->
                    // Per RFC 5280.
                    when (versionString) {
                        "" -> "1" // No version means version 1.
                        "1" -> "2"
                        "2" -> "3"
                        else -> versionString
                    }
                }

            val serialNumber: String = with(cert.serialNumber) {
                if (value.size <= 8) {
                    toLong().toString()
                } else {
                    value.unsignedBigIntToString()
                }
            }

            val issued = cert.validityNotBefore.formatWithRfc1123()

            val expired = cert.validityNotAfter.formatWithRfc1123()

            val subject = cert.subject.components.mapValues { it.value.value }

            val issuer = cert.issuer.components.mapValues { it.value.value }

            val pkAlgorithm: String = OID.lookupByOid(cert.signatureAlgorithmOid)?.description
                    ?: "Unexpected algorithm OID ${cert.signatureAlgorithmOid}"

            val pkNamedCurve: String =
                if (cert.signatureAlgorithm in listOf(Algorithm.RS256, Algorithm.RS384, Algorithm.RS512))
                    notAvail // RSA doesn't have a named curve.
                else {
                    runCatching { cert.ecPublicKey.curve.name }.getOrElse { e ->
                        e.message ?: notAvail }
                }

            val pkValue: String = runCatching { cert.signature.toHex(byteDivider = " ") }
                .getOrElse { e -> e.message ?: notAvail }

            val extensions = formatExtensions(cert)

            return CertificateViewData(
                type,
                serialNumber,
                version,
                issued,
                expired,
                subject,
                issuer,
                pkAlgorithm,
                pkNamedCurve,
                pkValue,
                extensions
            )
        }

        private fun formatExtensions(cert: X509Cert): List<Triple<Boolean, String, String>> {

            return cert.extensions.map { ext ->
                    val displayValue = when (ext.oid) {
                        // Known extensions data is formatted specifically by OID.
                        OID.X509_EXTENSION_SUBJECT_KEY_IDENTIFIER.oid ->
                            cert.subjectKeyIdentifier!!.toHex(byteDivider = " ")

                        OID.X509_EXTENSION_KEY_USAGE.oid ->
                            cert.keyUsage.joinToString(", ") { it.description }

                        OID.X509_EXTENSION_AUTHORITY_KEY_IDENTIFIER.oid ->
                            cert.authorityKeyIdentifier!!.toHex(byteDivider = " ")

                        else -> {
                            try {
                                // Most extensions are ASN.1 so we opportunistically try and decode
                                // and if it works, pretty print that.
                                ASN1.print(ASN1.decode(ext.data.toByteArray())!!)
                            } catch (_: Throwable) {
                                // If decoding fails, fall back to hex encoding.
                                ext.data.toByteArray().toHex(byteDivider = " ")
                            }
                        }
                    }
                    val oidEntry = OID.lookupByOid(ext.oid)
                    val oid = if (oidEntry != null) {
                        "${ext.oid} ${oidEntry.description}"
                    } else {
                        ext.oid
                    }
                    Triple(ext.isCritical, oid, displayValue)
                }

        }

        private fun Instant.formatWithRfc1123(): String {
            // Modified RFC_1123: Thu, Jan 4 2029 00:00
            val rfc1123mod: DateTimeFormat<DateTimeComponents> = Format {
                dayOfWeek(DayOfWeekNames.ENGLISH_ABBREVIATED)
                chars(", ")
                monthName(MonthNames.ENGLISH_ABBREVIATED)
                char(' ')
                dayOfMonth(Padding.NONE)
                char(' ')
                year()
                char(' ')
                hour()
                char(':')
                minute()
            }
            return format(rfc1123mod)
        }
    }
}



