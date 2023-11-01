package com.android.mdl.appreader.settings

import android.content.res.Configuration
import androidx.compose.foundation.clickable
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
import com.android.mdl.appreader.theme.ReaderAppTheme
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date

@Composable
fun VicalDetailsScreen(
    vicalItem: VicalItem?,
    onSelectCertificate: (item: CertificateItem) -> Unit,
    onDeleteVical: () -> Unit
) {
    if (vicalItem == null) {
        Title(title = "No vical provided")
    } else {
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Title(title = vicalItem.title)
                Line(modifier = Modifier, text = "Vical provider " + vicalItem.vicalProvider)
                Line(modifier = Modifier, text = "Date " + displayDate(vicalItem.date))
                Line(modifier = Modifier, text = "Vical issue ID " + vicalItem.vicalIssueID ?: "-")
                Line(modifier = Modifier, text = "Next update " + displayDate(vicalItem.nextUpdate))
                Subtitle(title = "Certificates")
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(vertical = 0.dp, horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(vicalItem.certificateItems) { certificateItem ->
                        Text(
                            modifier = Modifier.clickable { onSelectCertificate(certificateItem) },
                            text = certificateItem.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            Button(onClick = onDeleteVical) {
                Text(text = "Delete")
            }
        }
    }
}

private fun displayDate(instant: Instant?): String {
    if (instant == null) {
        return "-"
    }
    return DateTimeFormatter.ofPattern("yyyy-MM-dd")
        .withZone(ZoneOffset.UTC)
        .format(instant)
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun previewVicalDetailsScreen() {
    ReaderAppTheme {
        VicalDetailsScreen(
            vicalItem =
            VicalItem(
                title = "Test vical 1",
                vicalProvider = "Provider 1",
                date = LocalDateTime.now().toInstant(ZoneOffset.UTC),
                vicalIssueID = 1234,
                nextUpdate = LocalDateTime.now().plusDays(10).toInstant(ZoneOffset.UTC),
                certificateItems = listOf(
                    CertificateItem(
                        title = "Test 1",
                        commonNameSubject = "*.google.com",
                        organisationSubject = "<Not part of certificate>",
                        organisationalUnitSubject = "<Not part of certificate>",
                        commonNameIssuer = "GTS CA 1C3",
                        organisationIssuer = "Google Trust Services LLC",
                        organisationalUnitIssuer = "<Not part of certificate>",
                        notBefore = Date.from(
                            LocalDateTime.now().minusDays(365).toInstant(ZoneOffset.UTC)
                        ),
                        notAfter = Date.from(
                            LocalDateTime.now().plusDays(365).toInstant(ZoneOffset.UTC)
                        ),
                        sha255Fingerprint = "03 5C 31 E7 A9 F3 71 2B 27 1C 5A 8D 82 E5 6C 5B 92 BC FC 28 7F72D7 4A B6 9D 61 BF 53 EF 3E 67",
                        sha1Fingerprint = "9D 80 9B CF 63 AA86 29 E9 3C 78 9A EA DA 15 56 7E BF 56 D8",
                        docTypes = emptyList(),
                        certificate = null
                    ),
                    CertificateItem(
                        title = "Test 2",
                        commonNameSubject = "*.google.com",
                        organisationSubject = "<Not part of certificate>",
                        organisationalUnitSubject = "<Not part of certificate>",
                        commonNameIssuer = "GTS CA 1C3",
                        organisationIssuer = "Google Trust Services LLC",
                        organisationalUnitIssuer = "<Not part of certificate>",
                        notBefore = Date.from(
                            LocalDateTime.now().minusDays(100).toInstant(
                                ZoneOffset.UTC
                            )
                        ),
                        notAfter = Date.from(
                            LocalDateTime.now().plusDays(100).toInstant(ZoneOffset.UTC)
                        ),
                        sha255Fingerprint = "03 5C 31 E7 A9 F3 71 2B 27 1C 5A 8D 82 E5 6C 5B 92 BC FC 28 7F72D7 4A B6 9D 61 BF 53 EF 3E 67",
                        sha1Fingerprint = "9D 80 9B CF 63 AA86 29 E9 3C 78 9A EA DA 15 56 7E BF 56 D8",
                        docTypes = emptyList(),
                        certificate = null
                    )
                ),
                vical = null
            ),
            onSelectCertificate = {},
            onDeleteVical = {}
        )
    }
}