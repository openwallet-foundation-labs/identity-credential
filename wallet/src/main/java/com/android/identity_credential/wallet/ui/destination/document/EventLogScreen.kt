package com.android.identity_credential.wallet.ui.destination.document

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import com.android.identity_credential.wallet.DocumentModel
import com.android.identity_credential.wallet.EventInfo
import com.android.identity_credential.wallet.R
import com.android.identity_credential.wallet.navigation.WalletDestination
import com.android.identity_credential.wallet.ui.KeyValuePairText
import com.android.identity_credential.wallet.ui.ScreenWithAppBarAndBackButton

private const val TAG = "EventLogScreen"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EventLogScreen(documentId: String, documentModel: DocumentModel, onNavigate: (String) -> Unit) {
    val eventInfos = remember {
        mutableStateListOf<EventInfo>().apply {
            addAll(documentModel.getEventInfos(documentId))
        }
    }

    Box(
        modifier = Modifier.fillMaxHeight()
    ) {
        ScreenWithAppBarAndBackButton(
            title = stringResource(R.string.document_info_screen_menu_activities_log),
            onBackButtonClick = { onNavigate(WalletDestination.PopBackStack.route) }
        ) {
            if (eventInfos.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        modifier = Modifier.padding(8.dp),
                        text = stringResource(R.string.document_info_screen_menu_activities_log_screen_empty),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                LazyColumn {
                        items(eventInfos) { eventInfo ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                KeyValuePairText(eventInfo.timestamp, eventInfo.requestedFields)
                            }
                        }
                    }
                }
            }
        }
        if (eventInfos.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Button(onClick = {
                    documentModel.deleteEventInfos(documentId)
                    eventInfos.clear()
                }) {
                    Text("Delete All")
                }
            }
        }
    }
}
