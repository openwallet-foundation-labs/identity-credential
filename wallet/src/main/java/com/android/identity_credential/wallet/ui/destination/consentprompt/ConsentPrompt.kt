package com.android.identity_credential.wallet.ui.destination.consentprompt

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.identity.credential.CredentialRequest
import com.android.identity.credentialtype.CredentialTypeRepository
import com.android.identity_credential.wallet.R
import kotlinx.coroutines.launch

/**
 * ConsentPrompt composable responsible for showing a bottom sheet modal dialog prompting the user
 * to consent to sending credential data to requesting party.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsentPrompt(
    consentData: ConsentPromptData,
    credentialTypeRepository: CredentialTypeRepository,
    onConfirm: () -> Unit = {},
    onCancel: () -> Unit = {}
) {

    /**
     * Extension function for extracting the display name for an element name in a CredentialRequest.DataElement
     */
    fun CredentialTypeRepository.getDataElementDisplayName(
        dataElement: CredentialRequest.DataElement,
        docType: String
    ) = getMdocCredentialType(docType)?.namespaces
        ?.get(dataElement.nameSpaceName)?.dataElements?.get(dataElement.dataElementName)
        ?.attribute?.displayName
        ?: dataElement.dataElementName


    // used for bottom sheet
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // get the user-facing display name for each CredentialRequest.DataElement and create a list of ConsentDataElements
    val consentDataElements =
        consentData.credentialRequest.requestedDataElements.map { dataElement ->
            val displayName = credentialTypeRepository.getDataElementDisplayName(
                dataElement = dataElement,
                docType = consentData.docType
            )
            ConsentDataElement(displayName, dataElement)
        }

    ModalBottomSheet(
        modifier = Modifier.fillMaxHeight(0.6F),
        onDismissRequest = { onCancel() },
        sheetState = sheetState,
    ) {

        ConsentPromptTitle(consentData = consentData)

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            DataElementsListView(dataElements = consentDataElements)
        }

        // show the 2 action button on the bottom of the dialog
        ConsentPromptActions(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            onCancel = {
                scope.launch {
                    sheetState.hide()
                    onCancel()
                }
            },
            onConfirm = {
                onConfirm()
            }
        )
    }
}

/**
 * Composable responsible for preparing the title text according to the TrustPoint's availability,
 * and if present, show the icon (if present) along with the title (if present)
 */
@Composable
private fun ConsentPromptTitle(consentData: ConsentPromptData) {
    // title of dialog, if verifier is null or verifier.displayName is null, use default text
    val title = if (consentData.verifier?.displayName == null) {
        LocalContext.current.getString(R.string.consent_prompt_title, consentData.documentName)
    } else { // title is based on TrustPoint's displayName
        LocalContext.current.getString(
            R.string.consent_prompt_title_verifier,
            consentData.verifier.displayName,
            consentData.documentName
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp), horizontalArrangement = Arrangement.Center
    ) {
        if (consentData.verifierIconBitmap != null) {
            Icon(
                modifier = Modifier.size(24.dp),
                bitmap = consentData.verifierIconBitmap.asImageBitmap(),
                contentDescription = "",
                tint = MaterialTheme.colorScheme.primary
            )
        } else {
            Icon(
                painterResource(id = R.drawable.ic_reader),
                modifier = Modifier.size(24.dp),
                contentDescription = "",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Text(
            text = title,
            modifier = Modifier
                .fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * List View showing  2 columns of data elements requested to be sent to requesting party.
 * Shows the document name that is used for extracting requested data.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DataElementsListView(dataElements: List<ConsentDataElement>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup()
    ) {
        val grouped = dataElements.chunked(2).map { pair ->
            if (pair.size == 1) Pair(pair.first(), null)
            else Pair(pair.first(), pair.last())
        }
        items(grouped.size) { index ->
            val pair = grouped[index]
            DataElementsRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
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
    modifier: Modifier = Modifier,
    left: ConsentDataElement,
    right: ConsentDataElement?,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        val chipModifier = if (right != null) Modifier.weight(1f) else Modifier
        DataElementView(
            modifier = chipModifier,
            documentElement = left,
        )
        right?.let {
            Spacer(modifier = Modifier.width(8.dp))
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
        colors = FilterChipDefaults.filterChipColors(
            disabledContainerColor = Color.Transparent,
            disabledTrailingIconColor = Color.White,
            disabledLeadingIconColor = Color.White,
            disabledLabelColor = Color.White,
            disabledSelectedContainerColor = Color.Transparent
        ),
        onClick = {},
        label = { Text(text = "• ${documentElement.displayName}") },
    )
}

/**
 * Bottom actions containing 2 buttons: Cancel and Share
 * Once user taps on Share, we disable buttons to prevent unintended taps
 */
@Composable
private fun ConsentPromptActions(
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    val enabled = remember { mutableStateOf(true) }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(
            modifier = Modifier.weight(1f),
            enabled = enabled.value,
            onClick = {
                if (enabled.value) {
                    onCancel()
                }
            }
        ) {
            Text(text = stringResource(id = R.string.consent_prompt_cancel))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            modifier = Modifier.weight(1f),
            enabled = enabled.value,
            onClick = {

                if (enabled.value) {
                    onConfirm()
                    enabled.value = false
                }
            }
        ) {
            Text(text = stringResource(id = R.string.consent_prompt_share))
        }
    }
}