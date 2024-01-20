package com.android.identity.wallet.presentationlog

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import co.nstant.`in`.cbor.model.DataItem
import co.nstant.`in`.cbor.model.Map
import com.android.identity.internal.Util
import com.android.identity.mdoc.request.DeviceRequestParser
import com.android.identity.mdoc.response.DeviceResponseParser
import com.android.identity.presentationlog.PresentationLogEntry
import com.android.identity.presentationlog.PresentationLogMetadata
import com.android.mdl.appreader.R
import com.android.mdl.appreader.presentationlog.PresentationHistoryViewModel
import com.android.mdl.appreader.theme.ReaderAppTheme
import com.android.mdl.appreader.util.FormatUtil.millisecondsToFullDateTimeString
import com.android.mdl.appreader.util.FormatUtil.millisecondsToTimeString
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.time.Duration
import java.util.Locale


class PresentationHistoryFragment : BottomSheetDialogFragment() {

    private val viewModel: PresentationHistoryViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val logEntries by viewModel.fetchPresentationLogHistory().collectAsState()
                ReaderAppTheme {
                    PresentationHistoryContainer(
                        modifier = Modifier
                            .fillMaxSize(),
                        title = stringResource(id = R.string.title_presentation_history),
                        logEntries = logEntries,
                        onDeleteSelected = { entryIds ->
                            viewModel.deleteSelectedEntries(entryIds)
                        },
                        onDeleteAll = {
                            viewModel.deleteAllEntries()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PresentationHistoryContainer(
    modifier: Modifier = Modifier,
    title: String,
    logEntries: List<PresentationLogEntry>,
    onDeleteSelected: (List<Long>) -> Unit,
    onDeleteAll: () -> Unit
) {
    val selectedEntries = remember { mutableStateMapOf<Long, Boolean>() }

    Column(modifier = modifier) {
        BottomSheetHandle(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        Text(
            text = title,
            modifier = Modifier
                .fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(16.dp))

        HistoryActions(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            enabledDeleteSelected = selectedEntries.isNotEmpty(),
            onDeleteSelected = {
                onDeleteSelected.invoke(selectedEntries.keys.toList())
            },
            enabledDeleteAll = logEntries.isNotEmpty(),
            onDeleteAll = {
                onDeleteAll.invoke()
            }
        )

        if (logEntries.isEmpty()) {
            Text(
                modifier = Modifier
                    .fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                color = Color.LightGray,
                text = "No recent history",
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                LazyColumn(modifier = Modifier.focusGroup()) {
                    items(logEntries.size) { index ->
                        val logEntry = logEntries[index]
                        LogEntryRowContainer(
                            entry = logEntry,
                            onSelectedLogEntry = { entryId, checked ->
                                if (!checked) {
                                    selectedEntries.remove(entryId)
                                } else {
                                    selectedEntries[entryId] = true
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomSheetHandle(
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.Center) {
        Spacer(
            modifier = Modifier
                .size(64.dp, 4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Gray)
        )
    }
}

@Composable
private fun LogEntryRowContainer(
    modifier: Modifier = Modifier,
    entry: PresentationLogEntry,
    onSelectedLogEntry: (entryId: Long, checked: Boolean) -> Unit
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        LogEntryRow(
            logEntry = entry,
            onSelectedLogEntry = { entryId, checked ->
                onSelectedLogEntry.invoke(entryId, checked)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogEntryRow(
    modifier: Modifier = Modifier,
    logEntry: PresentationLogEntry,
    onSelectedLogEntry: (entryId: Long, checked: Boolean) -> Unit
) {
    var isChecked by remember { mutableStateOf(false) }

    val request = logEntry.getRequest()
    val response = logEntry.getResponse()
    val metadata = logEntry.getMetadata()
        ?: throw IllegalStateException("Expected PresentationLogEntry ${logEntry.id} to have the Metadata component persisted to StorageEngine but none could be found")

    FilterChip(
        modifier = modifier,
        selected = isChecked,
        onClick = {
            isChecked = !isChecked
            onSelectedLogEntry.invoke(logEntry.id, isChecked)
        },
        label = {
            Column(modifier = Modifier.fillMaxWidth()) {
                MetadataLogView(metadata = metadata)
                RequestLogView(request = request)
                ResponseLogView(response = response)
            }
        },

        leadingIcon = {
            Checkbox(
                checked = isChecked,
                onCheckedChange = {
                    isChecked = !isChecked
                    onSelectedLogEntry.invoke(logEntry.id, isChecked)
                }
            )
        }
    )
}

@Composable
fun MetadataLogView(metadata: PresentationLogMetadata) {
    val dateTimeStart = millisecondsToFullDateTimeString(metadata.transactionStartTime)
    Text(
        text = dateTimeStart,
        style = MaterialTheme.typography.titleMedium
    )
    val duration = Duration.ofMillis(metadata.transactionEndTime - metadata.transactionStartTime)
    val dateTimeEnd =
        "Ended ${duration.seconds} seconds later (at ${millisecondsToTimeString(metadata.transactionEndTime)})"
    Text(
        text = dateTimeEnd,
        style = MaterialTheme.typography.titleSmall
    )
    Spacer(modifier = Modifier.height(16.dp))

    var metadataInfo = "Transaction status: ${metadata.presentationTransactionStatus.name}\n"
    metadataInfo += "Engagement type: ${metadata.engagementType.name}\n"
    metadataInfo += "Error: ${metadata.error}\n"
    metadataInfo += "Session transcript length: ${metadata.sessionTranscript.size}\n"
    val locationText =
        "Location (lat,long): (${metadata.locationLatitude},${metadata.locationLongitude})"
    val context = LocalContext.current
    Text(
        text = metadataInfo,
        style = MaterialTheme.typography.bodyMedium
    )
    Text(
        modifier = Modifier
            .clickable {
                val uri: String =
                    java.lang.String.format(
                        Locale.ENGLISH, "geo:%f,%f?q=%f,%f(mDL Presentation on $dateTimeStart)",
                        metadata.locationLatitude,
                        metadata.locationLongitude,
                        metadata.locationLatitude,
                        metadata.locationLongitude
                    )
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                context.startActivity(intent)
            }
            .padding(16.dp),
        text = buildAnnotatedString {
            append(locationText)
            addStyle(
                style = SpanStyle(
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline
                ),
                start = 0,
                end = locationText.length - 1
            )
        },
    )
}

@Composable
fun RequestLogView(request: DeviceRequestParser.DeviceRequest?) {
    var showRequestDetails by remember { mutableStateOf(false) }

    if (request == null) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp)
                .border(
                    2.dp,
                    SolidColor(Color.Green),
                    RoundedCornerShape(10.dp)
                ),
            text = "Request is null"
        )
    }

    request?.let {
        val multipleDocumentRequests = request.documentRequests.size > 1

        if (multipleDocumentRequests) {
            Text(
                text = "${request.documentRequests.size} Document Requests",
                style = MaterialTheme.typography.titleSmall,
                color = Color.Cyan
            )
        }


        request.documentRequests.forEach { docRequest ->
            val requestHeader = "Request (v${request.version}) for Doc: ${docRequest.docType}"
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp)
                    .border(
                        2.dp,
                        SolidColor(Color.Green),
                        RoundedCornerShape(10.dp)
                    )
                    .clickable {
                        showRequestDetails = !showRequestDetails
                    }
            ) {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = requestHeader
                )

                if (showRequestDetails) {
                    val itemsRequest: DataItem = Util.cborDecode(docRequest.itemsRequest)
                    val nameSpaces = Util.cborMapExtractMap(itemsRequest, "nameSpaces")
                    val itemsMap = Util.castTo(Map::class.java, nameSpaces)
                    itemsMap.keys.forEach { key ->
                        val text = "[$key] => ${itemsMap[key]}"
                        Text(
                            modifier = Modifier
                                .padding(start = 5.dp)
                                .border(
                                    1.dp,
                                    SolidColor(MaterialTheme.colorScheme.outline),
                                    CutCornerShape(5.dp)
                                ),
                            text = text
                        )
                    }
                    val requestInfo = docRequest.requestInfo


                    val requestText =
                        "request info: ${requestInfo.size}, Reader Authentication: ${docRequest.readerAuth}"
                    Text(
                        modifier = Modifier
                            .padding(start = 5.dp)
                            .border(
                                1.dp,
                                SolidColor(MaterialTheme.colorScheme.outline),
                                CutCornerShape(5.dp)
                            ),
                        text = requestText
                    )
                }
            }
        }
    }
}

@Composable
fun ResponseLogView(response: DeviceResponseParser.DeviceResponse?) {
    var showResponseDetails by remember { mutableStateOf(false) }

    var responseHeader = "Response " +
            if (response == null) {
                "is null"
            } else {
                "(v ${response.version}) status: ${response.status}"
            }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp)
            .border(
                2.dp,
                SolidColor(Color.Yellow),
                RoundedCornerShape(10.dp)
            )
            .clickable {
                showResponseDetails = !showResponseDetails
            }
    ) {
        Text(
            modifier = Modifier.padding(16.dp),
            text = responseHeader
        )
        if (showResponseDetails) {
            val statusInfo = "[OK=0|General Err=10|Cbor Decoding Err=11|Cbor Violation Err=12]"
            Text(
                modifier = Modifier.padding(16.dp),
                text = statusInfo
            )
            response?.let {
                response.documents.forEach { document ->

                    document.issuerNamespaces.forEach { nameSpace ->
                        val names = document.getIssuerEntryNames(nameSpace)
                        var nameSpaceText = "[Issuer NameSpace] $nameSpace\n"
                        names.forEach { name ->
                            nameSpaceText += "[IssuerEntry $name] ${
                                if (name != "portrait")
                                    String(
                                        document.getIssuerEntryData(
                                            nameSpace,
                                            name
                                        )
                                    ) else "[bytes]"

                            }\n"
                        }
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 5.dp)
                                .border(
                                    1.dp,
                                    SolidColor(MaterialTheme.colorScheme.outline),
                                    CutCornerShape(5.dp)
                                ),
                            text = nameSpaceText
                        )
                    }

                    document.deviceNamespaces.forEach { nameSpace ->
                        val names = document.getDeviceEntryNames(nameSpace)
                        var nameSpaceText = "[Device NameSpace] $nameSpace\n"
                        names.forEach { name ->
                            nameSpaceText += "[DeviceEntry $name] ${
                                String(
                                    document.getDeviceEntryData(
                                        nameSpace,
                                        name
                                    )
                                )
                            }\n"
                        }
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 5.dp)
                                .border(
                                    1.dp,
                                    SolidColor(MaterialTheme.colorScheme.outline),
                                    CutCornerShape(5.dp)
                                ),
                            text = nameSpaceText
                        )
                    }

                    var text = "[Document] ${document.docType}\n"
                    text += "[Device NameSpaces] ${document.deviceNamespaces.joinToString()}\n"
                    text += "[DeviceSigned was authenticated] ${document.deviceSignedAuthenticated}\n"
                    text += "[DeviceSigned auth via signature ECDSA or MAC] ${document.deviceSignedAuthenticatedViaSignature}\n"
                    text += "[Issuer NameSpaces] ${document.issuerNamespaces.joinToString()}\n"
                    text += "[IssuerSigned was authenticated] ${document.issuerSignedAuthenticated}\n"
                    text += "[Issuer Certificate Chain Count] ${document.issuerCertificateChain.size}\n"
                    text += "[Issuer entries digest fail count] ${document.numIssuerEntryDigestMatchFailures}\n"
                    text += "[ValidityInfo (MSO) expectedUpdate] ${
                        document.validityInfoExpectedUpdate?.let { it1 ->
                            millisecondsToFullDateTimeString(
                                it1.toEpochMilli()
                            )
                        }
                    }\n"
                    text += "[ValidityInfo valid from] ${millisecondsToFullDateTimeString(document.validityInfoValidFrom.toEpochMilli())}\n"
                    text += "[ValidityInfo valid until] ${millisecondsToFullDateTimeString(document.validityInfoValidUntil.toEpochMilli())}\n"
                    text += "[ValidityInfo signed date] ${millisecondsToFullDateTimeString(document.validityInfoSigned.toEpochMilli())}"
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 5.dp)
                            .border(
                                1.dp,
                                SolidColor(MaterialTheme.colorScheme.outline),
                                CutCornerShape(5.dp)
                            ),
                        text = text
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryActions(
    modifier: Modifier = Modifier,
    enabledDeleteSelected: Boolean,
    onDeleteSelected: () -> Unit,
    enabledDeleteAll: Boolean,
    onDeleteAll: () -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        TextButton(
            modifier = Modifier.weight(1f),
            enabled = enabledDeleteSelected,
            onClick = {
                if (enabledDeleteSelected) {
                    onDeleteSelected.invoke()
                }
            }
        ) {
            Text(text = stringResource(id = R.string.btn_log_delete_selected))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            modifier = Modifier.weight(1f),
            enabled = enabledDeleteAll,
            onClick = {
                if (enabledDeleteAll) {
                    onDeleteAll.invoke()
                }
            }
        ) {
            Text(text = stringResource(id = R.string.btn_log_delete_all))
        }
    }
}