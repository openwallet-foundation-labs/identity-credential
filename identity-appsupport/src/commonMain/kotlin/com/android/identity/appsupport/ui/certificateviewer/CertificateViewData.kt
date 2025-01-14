package com.android.identity.appsupport.ui.certificateviewer

import com.android.identity.asn1.ASN1String
import com.android.identity.asn1.OID
import com.android.identity.crypto.X509Cert
import com.android.identity.util.unsignedBigIntToString
import com.android.identity.util.toHex
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.offsetAt
import kotlin.collections.mapValues
import kotlin.text.toIntOrNull

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
    val extensions: Map<String, String> = emptyMap()
) {

    companion object {

        /** Map Certificate data to View fields. */
        fun from(cert: X509Cert): CertificateViewData {
            val notAvail =
                "" // TODO: Can't populate from data marker. Process properly in Composables.
            val type = "X.509"

            val serialNumber: String = with(cert.serialNumber) {
                if (value.size <= 8) {
                    toLong().toString()
                } else {
                    value.unsignedBigIntToString()
                }
            }

            val version = cert.version.toString()

            val issued = cert.validityNotBefore.formatWithIsoFormat()

            val expired = cert.validityNotAfter.formatWithIsoFormat()

            val subject = refineKeyNames(cert.subject.components)

            val issuer = refineKeyNames(cert.issuer.components)

            val pkAlgorithm: String = runCatching { cert.signatureAlgorithm.name }
                .getOrElse { e -> e.message ?: notAvail }

            val pkNamedCurve: String = runCatching { cert.ecPublicKey.curve.name }
                .getOrElse { e -> e.message ?: notAvail }

            val pkValue: String = runCatching { cert.signature.toHex(byteDivider = " ") }
                .getOrElse { e -> e.message ?: notAvail }

            val extensionsMap = cert.criticalExtensionOIDs.associateWith { key ->
                cert.getExtensionValue(key)?.toHex(byteDivider = " ") ?: notAvail
            }
                .plus(cert.nonCriticalExtensionOIDs.associateWith { key ->
                    cert.getExtensionValue(key)?.toHex(byteDivider = " ") ?: notAvail
                })

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
                extensionsMap
            )
        }

        private fun refineKeyNames(components: Map<String, ASN1String>): Map<String, String> =
            components
                .mapKeys { (key, _) ->
                    key.substringBefore("=", key)
                }
                .mapValues { (_, value) ->
                    value.value
                }

        /**
         * The Date Time format can be customized by creating specific DateTimeComponents<> structure
         * as needed for KMP compatibility.
         */
        private fun Instant.formatWithIsoFormat(timeZone: TimeZone = TimeZone.currentSystemDefault()) =
            format(DateTimeComponents.Formats.ISO_DATE_TIME_OFFSET, timeZone.offsetAt(this))

    }
}



