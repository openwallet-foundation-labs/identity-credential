package com.android.identity.appsupport.ui.x509chain

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.identity.crypto.X509Cert
import identitycredential.identity_appsupport.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Show the single certificate data in a composable.
 */
@Composable
internal fun X509ChainItem(
    certificateData: X509Cert?,
    info: String?,
    warning: String?,
    ) {
    if (certificateData == null) {
        Title(stringResource(Res.string.no_cert_data_warning))
    } else {
        val data = certificateData.toX509ViewData()

        Column {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                info?.let { InfoCard(it) }
                warning?.let { WarningCard(it) }

                Subtitle(stringResource(Res.string.sub_basic_info))
                KeyValuePairLine(stringResource(Res.string.k_type), data.type)
                KeyValuePairLine(stringResource(Res.string.k_serial_number), data.serialNumber)
                KeyValuePairLine(stringResource(Res.string.k_version), data.version)
                KeyValuePairLine(stringResource(Res.string.k_issued), data.issued)
                KeyValuePairLine(stringResource(Res.string.k_expired), data.expired)

                Subtitle(stringResource(Res.string.sub_subject))
                KeyValuePairLine(stringResource(Res.string.k_country_name), data.subjectCountry)
                KeyValuePairLine(stringResource(Res.string.k_common_name), data.subjectCommonName)
                KeyValuePairLine(stringResource(Res.string.k_organization), data.subjectOrg)

                Subtitle(stringResource(Res.string.sub_issuer))
                KeyValuePairLine(stringResource(Res.string.k_country_name), data.issuerCountry)
                KeyValuePairLine(stringResource(Res.string.k_common_name), data.issuerCommonName)
                KeyValuePairLine(stringResource(Res.string.k_organization), data.issuerOrg)

                Subtitle(stringResource(Res.string.sub_public_key_info))
                KeyValuePairLine(stringResource(Res.string.k_pk_algorithm), data.pkAlgorithm)
                KeyValuePairLine(stringResource(Res.string.k_pk_named_curve), data.pkNamedCurve)
                KeyValuePairLine(stringResource(Res.string.k_pk_value),data.pkValue)
            }
        }
    }
}

@Composable
internal fun Title(text: String) {
    Column {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = text,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        HorizontalDivider(color = Color.Gray)
    }
}

@Composable
private fun Subtitle(text: String) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun KeyValuePairLine(
    key: String,
    valueText: String
) {
    Column(
        Modifier
            .padding(8.dp)
            .fillMaxWidth()) {
        Text(
            text = key,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun InfoCard(text: String) {
    Column(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(
                modifier = Modifier.padding(end = 16.dp),
                imageVector = Icons.Filled.Info,
                contentDescription = stringResource(Res.string.ic_descr_info),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Text(
                text = text,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun WarningCard(text: String) {
    Column(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.errorContainer),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(
                modifier = Modifier.padding(end = 16.dp),
                imageVector = Icons.Filled.Warning,
                contentDescription = stringResource(Res.string.ic_descr_error),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )

            Text(
                text = text,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Preview
@Composable
private fun PreviewX509ChainItem() {
    X509ChainItem(X509VewData.previewCertificate, null, null)
}
