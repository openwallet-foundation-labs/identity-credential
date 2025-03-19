package org.multipaz.compose.certificateviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.multipaz.compose.certificateviewer.CertificateViewData
import org.multipaz.asn1.ASN1Integer
import org.multipaz.asn1.OID
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.datetime.FormatStyle
import multipazproject.multipaz_compose.generated.resources.Res
import multipazproject.multipaz_compose.generated.resources.certificate_viewer_critical_ext
import multipazproject.multipaz_compose.generated.resources.certificate_viewer_k_common_name
import multipazproject.multipaz_compose.generated.resources.certificate_viewer_k_country_name
import multipazproject.multipaz_compose.generated.resources.certificate_viewer_k_locality_name
import multipazproject.multipaz_compose.generated.resources.certificate_viewer_k_org_name
import multipazproject.multipaz_compose.generated.resources.certificate_viewer_k_org_unit_name
import multipazproject.multipaz_compose.generated.resources.certificate_viewer_k_other_name
import multipazproject.multipaz_compose.generated.resources.certificate_viewer_k_pk_algorithm
import multipazproject.multipaz_compose.generated.resources.certificate_viewer_k_pk_named_curve
import multipazproject.multipaz_compose.generated.resources.certificate_viewer_k_pk_value
import multipazproject.multipaz_compose.generated.resources.certificate_viewer_k_serial_number
import multipazproject.multipaz_compose.generated.resources.certificate_viewer_k_state_name
import multipazproject.multipaz_compose.generated.resources.certificate_viewer_k_type
import multipazproject.multipaz_compose.generated.resources.certificate_viewer_k_valid_from
import multipazproject.multipaz_compose.generated.resources.certificate_viewer_k_valid_until
import multipazproject.multipaz_compose.generated.resources.certificate_viewer_non_critical_ext
import multipazproject.multipaz_compose.generated.resources.certificate_viewer_oid
import multipazproject.multipaz_compose.generated.resources.certificate_viewer_sub_basic_info
import multipazproject.multipaz_compose.generated.resources.certificate_viewer_sub_extensions
import multipazproject.multipaz_compose.generated.resources.certificate_viewer_sub_issuer
import multipazproject.multipaz_compose.generated.resources.certificate_viewer_sub_public_key_info
import multipazproject.multipaz_compose.generated.resources.certificate_viewer_sub_subject
import multipazproject.multipaz_compose.generated.resources.certificate_viewer_valid_now
import multipazproject.multipaz_compose.generated.resources.certificate_viewer_validity_in_the_future
import multipazproject.multipaz_compose.generated.resources.certificate_viewer_validity_in_the_past
import multipazproject.multipaz_compose.generated.resources.certificate_viewer_value
import multipazproject.multipaz_compose.generated.resources.certificate_viewer_version_text
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.multipaz.compose.datetime.durationFromNowText
import org.multipaz.compose.datetime.formattedDateTime
import kotlin.math.max
import kotlin.time.Duration.Companion.hours

private val PAGER_INDICATOR_HEIGHT = 30.dp
private val PAGER_INDICATOR_PADDING = 8.dp
private val indent = arrayOf(0.dp, 8.dp, 16.dp, 24.dp)

/**
 * Shows a X.509 certificate chain.
 *
 * @param x509CertChain the [X509CertChain] to show.
 */
@Composable
fun CertificateViewer(
    x509CertChain: X509CertChain,
) {
    CertificateViewerInternal(x509CertChain.certificates)
}

/**
 * Shows a X.509 certificate.
 *
 * @param x509Cert the [X509Cert] to show.
 */
@Composable
fun CertificateViewer(
    x509Cert: X509Cert,
) {
    CertificateViewerInternal(listOf(x509Cert))
}

@Composable
private fun CertificateViewerInternal(certificates: List<X509Cert>) {
    check(certificates.isNotEmpty())
    val certDataList = certificates.map { CertificateViewData.from(it) }
    Box(
        modifier = Modifier.fillMaxHeight()
    ) {
        val listSize = certDataList.size
        val pagerState = rememberPagerState(pageCount = { listSize })

        Column(
            modifier = Modifier.then(
                if (listSize > 1)
                    Modifier.padding(bottom = PAGER_INDICATOR_HEIGHT + PAGER_INDICATOR_PADDING)
                else // No pager, no padding.
                    Modifier
            )
        ) {
            HorizontalPager(
                state = pagerState,
            ) { page ->
                Column {
                    CertificateView(certDataList[page])
                }
            }
        }

        if (listSize > 1) { // Don't show pager for single cert on the list.
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .wrapContentHeight()
                    .fillMaxWidth()
                    .height(PAGER_INDICATOR_HEIGHT)
                    .padding(PAGER_INDICATOR_PADDING),
            ) {
                repeat(pagerState.pageCount) { iteration ->
                    val color =
                        if (pagerState.currentPage == iteration) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                                .copy(alpha = .2f)
                        }
                    Box(
                        modifier = Modifier
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(color)
                            .size(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CertificateView(data: CertificateViewData) {
    val clipboardManager = LocalClipboardManager.current
    Column {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Little bit of an easter-egg but very useful: Copy the PEM-encoded certificate
            // to the clipboard when user taps the "Basic Information" string.
            //
            Subtitle(
                stringResource(Res.string.certificate_viewer_sub_basic_info),
                modifier = Modifier.clickable {
                    // TODO: Use setClip() when it's ready so we can set MIME type to application/x-pem-file
                    clipboardManager.setText(AnnotatedString(data.pem))
                }
            )
            KeyValuePairLine(
                stringResource(Res.string.certificate_viewer_k_type),
                stringResource(Res.string.certificate_viewer_version_text, data.version)
            )
            KeyValuePairLine(
                stringResource(Res.string.certificate_viewer_k_serial_number),
                data.serialNumber
            )
            KeyValuePairLine(
                stringResource(Res.string.certificate_viewer_k_valid_from),
                formattedDateTime(
                    instant = data.validFrom,
                    dateStyle = FormatStyle.FULL,
                    timeStyle = FormatStyle.LONG,
                )
            )
            KeyValuePairLine(
                stringResource(Res.string.certificate_viewer_k_valid_until),
                formattedDateTime(
                    instant = data.validUntil,
                    dateStyle = FormatStyle.FULL,
                    timeStyle = FormatStyle.LONG,
                )
            )

            val now = Clock.System.now()
            if (now > data.validUntil) {
                KeyValuePairLine(
                    "Validity Info",
                    buildAnnotatedString {
                        withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.error)) {
                            append(
                                stringResource(
                                    Res.string.certificate_viewer_validity_in_the_past,
                                    durationFromNowText(data.validUntil)
                                )
                            )
                        }
                    }
                )
            } else if (data.validFrom > now) {
                KeyValuePairLine(
                    "Validity Info",
                    buildAnnotatedString {
                        withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.error)) {
                            append(
                                stringResource(
                                    Res.string.certificate_viewer_validity_in_the_future,
                                    durationFromNowText(data.validFrom)
                                )
                            )
                        }
                    }
                )
            } else {
                KeyValuePairLine(
                    "Validity Info",
                    stringResource(
                        Res.string.certificate_viewer_valid_now,
                        durationFromNowText(data.validUntil)
                    )
                )
            }

            if (data.subject.isNotEmpty()) {
                Subtitle(stringResource(Res.string.certificate_viewer_sub_subject))
                data.subject.forEach { (oid, value) ->
                    val res = oidToResourceMap[oid]
                    if (res != null) {
                        KeyValuePairLine(stringResource(res), value)
                    } else {
                        KeyValuePairLine(stringResource(Res.string.certificate_viewer_k_other_name, oid), value)
                    }
                }
            }

            if (data.issuer.isNotEmpty()) {
                Subtitle(stringResource(Res.string.certificate_viewer_sub_issuer))
                data.issuer.forEach { (oid, value) ->
                    val res = oidToResourceMap[oid]
                    if (res != null) {
                        KeyValuePairLine(stringResource(res), value)
                    } else {
                        KeyValuePairLine(stringResource(Res.string.certificate_viewer_k_other_name, oid), value)
                    }
                }
            }

            Subtitle(stringResource(Res.string.certificate_viewer_sub_public_key_info))
            KeyValuePairLine(
                stringResource(Res.string.certificate_viewer_k_pk_algorithm),
                data.pkAlgorithm
            )
            if (data.pkNamedCurve != null) {
                KeyValuePairLine(
                    stringResource(Res.string.certificate_viewer_k_pk_named_curve),
                    data.pkNamedCurve!!
                )
            }
            KeyValuePairLine(
                stringResource(Res.string.certificate_viewer_k_pk_value),
                data.pkValue
            )

            if (data.extensions.isNotEmpty()) {
                Subtitle(stringResource(Res.string.certificate_viewer_sub_extensions))
                Column {
                    data.extensions.forEach { (isCritical, oid, value) ->
                        SubHeading1(
                            stringResource(
                                if (isCritical) Res.string.certificate_viewer_critical_ext
                                else Res.string.certificate_viewer_non_critical_ext
                            )
                        )
                        OidValuePairColumn(oid, value)
                    }
                }
            }
        }
    }
}

@Composable
private fun Subtitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        modifier = modifier
            .padding(top = 12.dp)
            .fillMaxWidth(),
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun SubHeading1(text: String) {
    Text(
        modifier = Modifier
            .padding(start = indent[1], top = 6.dp)
            .fillMaxWidth(),
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun SubHeading2(text: String) {
    Text(
        modifier = Modifier
            .padding(start = indent[2], top = 6.dp)
            .fillMaxWidth(),
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun KeyValuePairLine(
    key: String,
    valueText: AnnotatedString,
) {
    if (valueText.isEmpty()) {
        return
    }

    Column(
        Modifier.fillMaxWidth()
            .padding(start = indent[1], bottom = 6.dp)
    ) {
        Text(
            text = key,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        SelectionContainer {
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}


@Composable
private fun KeyValuePairLine(
    key: String,
    valueText: String,
) {
    if (valueText.isEmpty()) {
        return
    }
    return KeyValuePairLine(key, AnnotatedString(valueText))
}

@Composable
private fun OidValuePairColumn(
    oid: String,
    valueText: String
) {
    if (valueText.isEmpty()) {
        return
    }

    Column(
        Modifier.fillMaxWidth().padding(bottom = 6.dp)
    ) {
        SubHeading2(stringResource(Res.string.certificate_viewer_oid))
        SelectionContainer {
            Text(
                modifier = Modifier.padding(start = indent[2]),
                text = oid,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        SubHeading2(stringResource(Res.string.certificate_viewer_value))
        SelectionContainer {
            DisplayIndentedText(valueText)
        }
    }
}

@Composable
private fun DisplayIndentedText(text: String, indentationStep: Dp = 6.dp) {
    Column(Modifier.padding(start = indent[2])) {
        text.lines().forEach { line ->
            val numSpaces = max(0, line.indexOfFirst { it != ' ' })
            Text(
                modifier = Modifier.padding(start = indentationStep * numSpaces),
                text = line.trimStart(),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private val oidToResourceMap: Map<String, StringResource> by lazy {
    mapOf(
        OID.COMMON_NAME.oid to Res.string.certificate_viewer_k_common_name,
        OID.SERIAL_NUMBER.oid to Res.string.certificate_viewer_k_serial_number,
        OID.COUNTRY_NAME.oid to Res.string.certificate_viewer_k_country_name,
        OID.LOCALITY_NAME.oid to Res.string.certificate_viewer_k_locality_name,
        OID.STATE_OR_PROVINCE_NAME.oid to Res.string.certificate_viewer_k_state_name,
        OID.ORGANIZATION_NAME.oid to Res.string.certificate_viewer_k_org_name,
        OID.ORGANIZATIONAL_UNIT_NAME.oid to Res.string.certificate_viewer_k_org_unit_name,
        // TODO: Add support for other OIDs from RFC 5280 Annex A, as needed.
    )
}
