package com.android.identity.wallet.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.identity.wallet.theme.HolderAppTheme
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Date

@Composable
fun CaCertificateDetailsScreen(
    certificateItem: CertificateItem?,
    onDeleteCertificate: () -> Unit = {},
) {
    if (certificateItem == null) {
        Title(title = "No certificate provided")
    } else {
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier =
                    Modifier
                        .verticalScroll(scrollState)
                        .weight(1f)
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Title(title = certificateItem.title)
                Subtitle(title = "Issued to")
                Line(
                    modifier = Modifier,
                    text = "Common Name (CN) " + certificateItem.commonNameSubject,
                )
                Line(
                    modifier = Modifier,
                    text = "Organisation (O) " + certificateItem.organisationSubject,
                )
                Line(
                    modifier = Modifier,
                    text = "Organisational Unit (OU) " + certificateItem.organisationalUnitSubject,
                )
                Subtitle(title = "Issued by")
                Line(
                    modifier = Modifier,
                    text = "Common Name (CN) " + certificateItem.commonNameIssuer,
                )
                Line(
                    modifier = Modifier,
                    text = "Organisation (O) " + certificateItem.organisationIssuer,
                )
                Line(
                    modifier = Modifier,
                    text = "Organisational Unit (OU) " + certificateItem.organisationalUnitIssuer,
                )
                Subtitle(title = "Fingerprints")
                Line(modifier = Modifier, "SHA-256 fingerprint")
                Line(modifier = Modifier.padding(16.dp), certificateItem.sha255Fingerprint)
                Line(modifier = Modifier, "SHA-1 fingerprint")
                Line(modifier = Modifier.padding(16.dp), certificateItem.sha1Fingerprint)
                if (certificateItem.docTypes.isNotEmpty()) {
                    Subtitle(title = "Supported mdoc types")
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(certificateItem.docTypes) { docType ->
                            Line(modifier = Modifier, text = docType)
                        }
                    }
                }
            }
            if (certificateItem.supportsDelete) {
                Button(onClick = onDeleteCertificate) {
                    Text(text = "Delete")
                }
            }
        }
    }
}

@Composable
fun Title(title: String) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
fun Subtitle(title: String) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
fun Line(
    modifier: Modifier,
    text: String,
) {
    Text(
        modifier = modifier.fillMaxWidth(),
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewCaCertificatesScreen() {
    HolderAppTheme {
        CaCertificateDetailsScreen(
            certificateItem =
                CertificateItem(
                    title = "Test 1",
                    commonNameSubject = "*.google.com",
                    organisationSubject = "<Not part of certificate>",
                    organisationalUnitSubject = "<Not part of certificate>",
                    commonNameIssuer = "GTS CA 1C3",
                    organisationIssuer = "Google Trust Services LLC",
                    organisationalUnitIssuer = "<Not part of certificate>",
                    notBefore = Date.from(LocalDateTime.now().minusDays(365).toInstant(ZoneOffset.UTC)),
                    notAfter = Date.from(LocalDateTime.now().plusDays(365).toInstant(ZoneOffset.UTC)),
                    sha255Fingerprint = "03 5C 31 E7 A9 F3 71 2B 27 1C 5A 8D 82 E5 6C 5B 92 BC FC 28 7F72D7 4A B6 9D 61 BF 53 EF 3E 67",
                    sha1Fingerprint = "9D 80 9B CF 63 AA86 29 E9 3C 78 9A EA DA 15 56 7E BF 56 D8",
                    docTypes = listOf("Doc type 1", "Doc type 2"),
                    supportsDelete = true,
                    trustPoint = null,
                ),
            onDeleteCertificate = {},
        )
    }
}
