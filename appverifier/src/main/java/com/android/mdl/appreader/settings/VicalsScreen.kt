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
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Date

@Composable
fun VicalsScreen(
    screenState: VicalsScreenState,
    onSelectVical: (item: VicalItem) -> Unit,
    onImportVical: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .weight(1f)
        ) {
            if (screenState.vicals.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "No vicals provided",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(screenState.vicals) { vicalItem ->
                        Text(
                            modifier = Modifier.clickable { onSelectVical(vicalItem) },
                            text = vicalItem.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
        Button(onClick = onImportVical) {
            Text(text = "Import")
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun previewVicalsScreen() {
    ReaderAppTheme {
        VicalsScreen(
            screenState = VicalsScreenState(
                listOf(
                    VicalItem(
                        title = "Test vical 1",
                        vicalProvider = "Provider 1",
                        date = LocalDateTime.now().toInstant(ZoneOffset.UTC),
                        vicalIssueID = 1234,
                        nextUpdate = LocalDateTime.now().plusDays(10).toInstant(ZoneOffset.UTC),
                        certificateItems = emptyList(),
                        vical = null
                    ),
                    VicalItem(
                        title = "Test vical 2",
                        vicalProvider = "Provider 2",
                        date = LocalDateTime.now().toInstant(ZoneOffset.UTC),
                        vicalIssueID = 1234,
                        nextUpdate = LocalDateTime.now().plusDays(10).toInstant(ZoneOffset.UTC),
                        certificateItems = emptyList(),
                        vical = null
                    )
                )
            ),
            onSelectVical = {},
            onImportVical = {}
        )
    }
}