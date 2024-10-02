package com.android.identity_credential.wallet.ui.prompt.consent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.android.identity.appsupport.ui.getOutlinedImageVector
import com.android.identity.trustmanagement.TrustPoint
import com.android.identity_credential.wallet.R
import com.android.identity_credential.wallet.util.toImageBitmap
import kotlinx.coroutines.launch
import kotlin.math.floor

/**
 * ConfirmButtonState defines the possible states of the Confirm button in the  Consent Prompt.
 * This state is referenced through [val confirmButtonState] in [ConsentPromptEntryField]
 *
 * If the Confirm button state is ENABLED then the Confirm button is enabled for the user to tap.
 * This invokes the `onConfirm()` callback and closes Consent Prompt composable.
 *
 * If Confirm button state is DISABLED then user cannot tap on the Confirm button until the user has
 * scrolled to the bottom of the list where the state is changed to ENABLED.
 */
private enum class ConfirmButtonState {
    // User can confirm sending the requested credentials after scrolling to the bottom
    ENABLED,

    // Confirm button cannot be tapped in this state
    DISABLED,

    // For initializing the state flow
    INIT
}

/**
 * ConsentPromptEntryField is responsible for showing a bottom sheet modal dialog prompting the user
 * to consent to sending credential data to requesting party and user can cancel at any time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsentPromptEntryField(
    consentFields: List<ConsentField>,
    documentName: String,
    verifier: TrustPoint?,
    onConfirm: () -> Unit = {},
    onCancel: () -> Unit = {}
) {
    // used for bottom sheet
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // determine whether user needs to scroll to tap on the Confirm button
    val confirmButtonState = remember { mutableStateOf(ConfirmButtonState.INIT) }
    val scrolledToBottom = remember { mutableStateOf(false) } // remember if user scrolled to bottom

    // the index of the last row that is currently visible
    val lastVisibleRowIndexState = remember { mutableIntStateOf(0) }

    // total rows that contain data element texts, with at most 2 per row
    val totalDataElementRows = remember {
        floor(consentFields.size / 2.0).toInt().run {
            if (consentFields.size % 2 != 0) { //odd number of elements
                this + 1// 1 more row to represent the single element
            } else {
                this// even number of elements, row count remains unchanged
            }
        }
    }
    // the index of the last row that will be/is rendered in the LazyColumn
    val lastRowIndex = remember { totalDataElementRows - 1 }

    // if user has not previously scrolled to bottom of list
    if (!scrolledToBottom.value) { // else if user has already scrolled to bottom, don't change button state

        // set Confirm button state according to whether there are more rows to be shown to user than
        // what the user is currently seeing
        confirmButtonState.value =
            if (lastRowIndex > lastVisibleRowIndexState.intValue) {
                ConfirmButtonState.DISABLED // user needs to scroll to reach the bottom of the list
            } else {// last visible row index has reached the LazyColumnI last row index
                // remember that user already saw the bottom-most row even if they scroll back up
                scrolledToBottom.value = true
                ConfirmButtonState.ENABLED // user has the option to now share their sensitive data
            }
    }

    ModalBottomSheet(
        modifier = Modifier.fillMaxHeight(0.6F),
        onDismissRequest = { onCancel() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {

        ConsentPromptHeader(
            documentName = documentName,
            verifier = verifier
        )

        Box(
            modifier = Modifier
                .fillMaxHeight(0.8f)
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp)
        ) {
            DataElementsListView(
                consentFields = consentFields,
                lastVisibleRowIndexState = lastVisibleRowIndexState,
            )
        }

        // show the 2 action button on the bottom of the dialog
        ConsentPromptActions(
            confirmButtonState = confirmButtonState,
            onCancel = {
                scope.launch {
                    sheetState.hide()
                    onCancel()
                }
            },
            onConfirm = { onConfirm.invoke() }
        )
    }
}

/**
 * Show the title text according to whether there's a TrustPoint's available, and if present, show
 * the icon too.
 */
@Composable
private fun ConsentPromptHeader(
    documentName: String,
    verifier: TrustPoint?
) {
    // title of dialog, if verifier is null or verifier.displayName is null, use default text
    val title = if (verifier == null) {
        LocalContext.current.getString(R.string.consent_prompt_title, documentName)
    } else { // title is based on TrustPoint's displayName, if available
        val verifierDisplayName = verifier.displayName
            ?: "Trusted Verifier (${verifier.certificate.subjectX500Principal.name})"
        LocalContext.current.getString(
            R.string.consent_prompt_title_verifier, verifierDisplayName,
            documentName
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        // show icon if icon bytes are present
        verifier?.displayIcon?.let { iconBytes ->
            Icon(
                modifier = Modifier.size(50.dp),
                // TODO: we're computing a bitmap every recomposition and this could be slow
                bitmap = iconBytes.toImageBitmap(),
                contentDescription = stringResource(id = R.string.consent_prompt_icon_description)
            )
        } ?: Spacer(modifier = Modifier.width(24.dp))
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * List View showing  2 columns of data elements requested to be sent to requesting party.
 * Shows the document name that is used for extracting requested data.
 *
 * Report back on param [lastVisibleRowIndexState] the index of the last row that is considered to
 * be actively visible from Compose (as user scrolls and compose draws).
 */
@Composable
private fun DataElementsListView(
    consentFields: List<ConsentField>,
    lastVisibleRowIndexState: MutableIntState,
) {
    val groupedElements = consentFields.chunked(2).map { pair ->
        if (pair.size == 1) Pair(pair.first(), null)
        else Pair(pair.first(), pair.last())
    }
    val lazyListState = rememberLazyListState()
    val visibleRows = remember { derivedStateOf { lazyListState.layoutInfo.visibleItemsInfo } }

    if (visibleRows.value.isNotEmpty()) {
        // notify of the last row's index that's considered visible
        lastVisibleRowIndexState.intValue = visibleRows.value.last().index
    }
    LazyColumn(
        state = lazyListState,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .focusGroup()
            .padding(start=10.dp)
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
 * A single row containing 2 columns of data elements to consent to sending to the Verifier.
 */
@Composable
private fun DataElementsRow(
    left: ConsentField,
    right: ConsentField?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 1.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        val chipModifier = if (right != null) Modifier.weight(1f) else Modifier
        DataElementView(
            modifier = chipModifier,
            consentField = left,
        )
        right?.let {
            DataElementView(
                modifier = chipModifier,
                consentField = right,
            )
        }
    }
}

/**
 * Individual view for a DataElement.
 */
@Composable
private fun DataElementView(
    modifier: Modifier = Modifier,
    consentField: ConsentField,
) {
    FilterChip(
        modifier = modifier,
        selected = true,
        enabled = false,
        colors = FilterChipDefaults.filterChipColors(
            disabledContainerColor = Color.Transparent,
            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurface,
            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurface,
            disabledLabelColor = MaterialTheme.colorScheme.onSurface,
            disabledSelectedContainerColor = Color.Transparent,

            ),

        onClick = {},
        label = {
            if (consentField.attribute?.icon != null) {
                Icon(
                    consentField.attribute!!.icon!!.getOutlinedImageVector(),
                    contentDescription = "${consentField.attribute!!.icon!!.iconName} icon"
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = "${consentField.displayName}",
                fontWeight = FontWeight.Normal,
                style = MaterialTheme.typography.bodySmall
            )
        },
    )
}

/**
 * Bottom actions containing 2 buttons: Cancel and Confirm
 * Once user taps on Confirm, we disable buttons to prevent unintended taps.
 */
@Composable
private fun ConsentPromptActions(
    confirmButtonState: MutableState<ConfirmButtonState>,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(modifier = Modifier.fillMaxHeight()) {

        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.padding(horizontal = 10.dp)

        ) {
            // Cancel button
            Button(
                modifier = Modifier.weight(1f),
                onClick = { onCancel.invoke() }
            ) {
                Text(text = stringResource(id = R.string.consent_prompt_button_cancel))
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Confirm button
            Button(
                modifier = Modifier.weight(1f),
                // enabled when user scrolls to the bottom
                enabled = confirmButtonState.value == ConfirmButtonState.ENABLED,
                onClick = { onConfirm.invoke() }
            ) {
                Text(text = stringResource(id = R.string.consent_prompt_button_confirm))
            }
        }
        // fade out "scroll to bottom" when user reaches bottom of list (enabled via 'visible' param)
        AnimatedVisibility(
            visible = confirmButtonState.value == ConfirmButtonState.DISABLED,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Text(
                text = stringResource(id = R.string.consent_prompt_text_scroll_to_bottom),
                fontSize = 12.sp,
                style = TextStyle.Default.copy(
                    color = Color.Gray
                ),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}