package com.android.identity_credential.wallet.ui.destination.consentprompt

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.identity.document.DocumentRequest
import com.android.identity.documenttype.DocumentTypeRepository
import com.android.identity_credential.wallet.R
import com.android.identity_credential.wallet.util.toImageBitmap
import kotlinx.coroutines.launch
import kotlin.math.floor

/**
 * ShareButtonState defines the possible states of the Share button in Consent Prompt that is
 * referenced through [ConsentPrompt.shareButtonState]
 *
 * If share button state is SHARE then the Share button is enabled for the user to tap - this
 * invokes the `onConfirm()` callback that closes Consent Prompt and proceeds to the next
 * If share button state is DISABLED then the Share button is disabled until user has scrolled to
 * the bottom of the list where the state is changed to SHARE
 */
enum class ShareButtonState {
    // user can share data after scrolling to bottom
    SHARE,

    // Share button cannot be tapped in this state
    DISABLED,

    // for initializing the state flow
    INIT,
}

/**
 * ConsentPrompt composable responsible for showing a bottom sheet modal dialog prompting the user
 * to consent to sending credential data to requesting party.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsentPrompt(
    consentData: ConsentPromptData,
    documentTypeRepository: DocumentTypeRepository,
    onConfirm: () -> Unit = {},
    onCancel: () -> Unit = {},
) {
    /**
     * Extension function for extracting the display name for an element name in a CredentialRequest.DataElement
     */
    fun DocumentTypeRepository.getDataElementDisplayName(
        dataElement: DocumentRequest.DataElement,
        docType: String,
    ) = getDocumentTypeForMdoc(docType)
        ?.mdocDocumentType
        ?.namespaces
        ?.get(dataElement.nameSpaceName)?.dataElements?.get(dataElement.dataElementName)
        ?.attribute?.displayName
        ?: dataElement.dataElementName

    // used for bottom sheet
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // get the user-facing display name for each CredentialRequest.DataElement and create a list of ConsentDataElements
    val consentDataElements =
        consentData.documentRequest.requestedDataElements
            .filter {
                // This ensures we only show the requested data element if we have it
                consentData.credentialData.hasDataElement(it.nameSpaceName, it.dataElementName)
            }
            .map { dataElement ->
                val displayName =
                    documentTypeRepository.getDataElementDisplayName(
                        dataElement = dataElement,
                        docType = consentData.docType,
                    )
                ConsentDataElement(displayName, dataElement)
            }

    // determine whether user needs to scroll to tap on the share button
    val shareButtonState = remember { mutableStateOf(ShareButtonState.INIT) }
    val scrolledToBottom = remember { mutableStateOf(false) } // remember if user scrolled to bottom

    // the index of the last row that is currently visible
    val lastVisibleRowIndexState = remember { mutableIntStateOf(0) }

    // total rows that contain data element texts, with at most 2 per row
    val totalDataElementRows =
        remember {
            floor(consentDataElements.size / 2.0).toInt().run {
                if (consentDataElements.size % 2 != 0) { // odd number of elements
                    this + 1 // 1 more row to represent the single element
                } else {
                    this // even number of elements, row count remains unchanged
                }
            }
        }
    // the index of the last row that will be/is rendered in the LazyColumn
    val lastRowIndex = remember { totalDataElementRows - 1 }

    // if user has not previously scrolled to bottom of list
    if (!scrolledToBottom.value) { // else if user has already scrolled to bottom, don't change button state

        // set Share button state according to whether there are more rows to be shown to user than
        // what the user is currently seeing
        shareButtonState.value =
            if (lastRowIndex > lastVisibleRowIndexState.intValue) {
                ShareButtonState.DISABLED // user needs to scroll to reach the bottom of the list
            } else { // last visible row index has reached the LazyColumnI last row index
                // remember that user already saw the bottom-most row even if they scroll back up
                scrolledToBottom.value = true
                ShareButtonState.SHARE // user has the option to now share their sensitive data
            }
    }

    ModalBottomSheet(
        modifier = Modifier.fillMaxHeight(0.6F),
        onDismissRequest = { onCancel() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        ConsentPromptHeader(
            modifier = Modifier,
            consentData = consentData,
        )

        Box(
            modifier =
                Modifier
                    .fillMaxHeight(0.8f)
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
        ) {
            DataElementsListView(
                dataElements = consentDataElements,
                lastVisibleRowIndexState = lastVisibleRowIndexState,
            )
        }

        // show the 2 action button on the bottom of the dialog
        ConsentPromptActions(
            shareButtonState = shareButtonState,
            onCancel = {
                scope.launch {
                    sheetState.hide()
                    onCancel()
                }
            },
            onShareButtonPress = { onConfirm.invoke() },
        )
    }
}

/**
 * Show the title text according to whether there's a TrustPoint's available, and if present, show
 * the icon too.
 */
@Composable
private fun ConsentPromptHeader(
    modifier: Modifier,
    consentData: ConsentPromptData,
) {
    // title of dialog, if verifier is null or verifier.displayName is null, use default text
    val title =
        if (consentData.verifier?.displayName == null) {
            LocalContext.current.getString(R.string.consent_prompt_title, consentData.documentName)
        } else { // title is based on TrustPoint's displayName
            LocalContext.current.getString(
                R.string.consent_prompt_title_verifier,
                consentData.verifier.displayName,
                consentData.documentName,
            )
        }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        // show icon if icon bytes are present
        consentData.verifier?.displayIcon?.let { iconBytes ->
            Icon(
                modifier = Modifier.size(24.dp),
                // TODO: we're computing a bitmap every recomposition and this could be slow
                bitmap = iconBytes.toImageBitmap(),
                contentDescription = stringResource(id = R.string.consent_prompt_icon_description),
            )
        }
        Text(
            text = title,
            modifier = modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * List View showing  2 columns of data elements requested to be sent to requesting party.
 * Shows the document name that is used for extracting requested data.
 *
 * Report back on param [lastVisibleRowIndexState] the index of the last row that is considered to
 * be actively visible from Compose (as user scrolls and compose draws)
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DataElementsListView(
    dataElements: List<ConsentDataElement>,
    // callback to update what the last visible row index is currently shown
    lastVisibleRowIndexState: MutableIntState,
) {
    val groupedElements =
        dataElements.chunked(2).map { pair ->
            if (pair.size == 1) {
                Pair(pair.first(), null)
            } else {
                Pair(pair.first(), pair.last())
            }
        }
    val lazyListState = rememberLazyListState()
    val visibleRows = remember { derivedStateOf { lazyListState.layoutInfo.visibleItemsInfo } }

    if (visibleRows.value.isNotEmpty()) {
        // notify of the last row's index that's considered visible
        lastVisibleRowIndexState.intValue = visibleRows.value.last().index
    }
    LazyColumn(
        state = lazyListState,
        modifier =
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .focusGroup(),
    ) {
        items(groupedElements.size) { index ->
            val pair = groupedElements[index]
            DataElementsRow(
                left = pair.first,
                right = pair.second,
            )
        }
    }
}

/**
 * A single row containing 2 columns of data elements to consent to sending
 */
@Composable
private fun DataElementsRow(
    left: ConsentDataElement,
    right: ConsentDataElement?,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 1.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        val chipModifier = if (right != null) Modifier.weight(1f) else Modifier
        DataElementView(
            modifier = chipModifier,
            documentElement = left,
        )
        right?.let {
            DataElementView(
                modifier = chipModifier,
                documentElement = right,
            )
        }
    }
}

/**
 * Individual view for a DataElement
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataElementView(
    modifier: Modifier = Modifier,
    documentElement: ConsentDataElement,
) {
    FilterChip(
        modifier = modifier,
        selected = true,
        enabled = false,
        colors =
            FilterChipDefaults.filterChipColors(
                disabledContainerColor = Color.Transparent,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurface,
                disabledLeadingIconColor = MaterialTheme.colorScheme.onSurface,
                disabledLabelColor = MaterialTheme.colorScheme.onSurface,
                disabledSelectedContainerColor = Color.Transparent,
            ),
        onClick = {},
        label = {
            Text(
                text = "• ${documentElement.displayName}",
                fontWeight = FontWeight.Normal,
            )
        },
    )
}

/**
 * Bottom actions containing 2 buttons: Cancel and Share
 * Once user taps on Share, we disable buttons to prevent unintended taps
 */
@Composable
private fun ConsentPromptActions(
    shareButtonState: MutableState<ShareButtonState>,
    onCancel: () -> Unit,
    onShareButtonPress: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxHeight()) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            // Cancel button
            Button(
                modifier = Modifier.weight(1f),
                onClick = { onCancel.invoke() },
            ) {
                Text(text = stringResource(id = R.string.consent_prompt_button_cancel))
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Share button
            Button(
                modifier = Modifier.weight(1f),
                // enabled when user scrolled to bottom
                enabled = shareButtonState.value == ShareButtonState.SHARE,
                onClick = { onShareButtonPress.invoke() },
            ) {
                Text(text = stringResource(id = R.string.consent_prompt_button_share))
            }
        }
        // fade out "scroll to bottom" when user reaches bottom of list
        AnimatedVisibility(
            visible = shareButtonState.value == ShareButtonState.DISABLED,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Text(
                text = stringResource(id = R.string.consent_prompt_text_scroll_to_bottom),
                fontSize = 12.sp,
                style =
                    TextStyle.Default.copy(
                        color = Color.Gray,
                    ),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}
