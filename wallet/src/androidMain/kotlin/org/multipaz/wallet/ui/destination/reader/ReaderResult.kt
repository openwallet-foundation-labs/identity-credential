package org.multipaz_credential.wallet.ui.destination.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DiagnosticOption
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz_credential.wallet.R
import org.multipaz_credential.wallet.ReaderDocument
import org.multipaz_credential.wallet.ReaderModel
import org.multipaz_credential.wallet.navigation.WalletDestination
import org.multipaz_credential.wallet.ui.KeyValuePairText
import org.multipaz_credential.wallet.ui.ScreenWithAppBarAndBackButton

private const val TAG = "ReaderShowResponse"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReaderResult(
    model: ReaderModel,
    documentTypeRepository: DocumentTypeRepository,
    onNavigate: (String) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { model.response?.documents?.size ?: 0 })

    Box(
        modifier = Modifier.fillMaxHeight()
    ) {

        ScreenWithAppBarAndBackButton(
            title = stringResource(R.string.reader_result_screen_title),
            onBackButtonClick = { onNavigate(WalletDestination.PopBackStack.route) },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                if (model.error != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("An error occurred: ${model.error?.message}")
                        }
                    }
                } else if (model.response != null) {
                    val response = model.response!!
                    if (response.documents.isEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                modifier = Modifier.padding(8.dp),
                                text = stringResource(R.string.reader_result_screen_no_documents_returned),
                                style = MaterialTheme.typography.titleLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column {
                            HorizontalPager(
                                state = pagerState,
                            ) { page ->
                                ShowResultDocument(
                                    response.documents[page],
                                    page,
                                    response.documents.size
                                )
                            }
                        }
                    }
            }
            }
        }

        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .wrapContentHeight()
                .fillMaxWidth()
                .height(30.dp)
                .padding(8.dp),
        ) {
            repeat(pagerState.pageCount) { iteration ->
                val color =
                    if (pagerState.currentPage == iteration) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondary
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

@Composable
private fun ShowResultDocument(document: ReaderDocument,
                               documentIndex: Int,
                               numDocuments: Int) {
    Column(Modifier.padding(8.dp)) {
        KeyValuePairText("Document Number", "${documentIndex + 1} of ${numDocuments}")
        KeyValuePairText(keyText = "DocType", valueText = document.docType)
        for (namespace in document.namespaces) {
            KeyValuePairText("Namespace", namespace.name)
            for ((dataElementName, dataElement) in namespace.dataElements) {
                val cborValue = Cbor.decode(dataElement.value)
                val (key, value) = if (dataElement.mdocDataElement != null) {
                    Pair(
                        dataElement.mdocDataElement.attribute.displayName,
                        dataElement.mdocDataElement.renderValue(cborValue)
                    )
                } else {
                    Pair(
                        dataElementName,
                        Cbor.toDiagnostics(cborValue, setOf(
                            DiagnosticOption.PRETTY_PRINT,
                            DiagnosticOption.EMBEDDED_CBOR,
                            DiagnosticOption.BSTR_PRINT_LENGTH,
                        ))
                    )
                }
                KeyValuePairText(key, value)

                if (dataElement.bitmap != null) {
                    Row(
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Image(
                            bitmap = dataElement.bitmap.asImageBitmap(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .size(200.dp),
                            contentDescription = dataElement.mdocDataElement?.attribute?.description
                                ?: "Unknown Data Element"
                        )
                    }
                }
            }
        }
    }
}
