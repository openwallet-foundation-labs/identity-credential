package com.android.mdl.appreader.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.mdl.appreader.home.DocumentElementsRequest
import com.android.mdl.appreader.home.RequestingDocumentState

@Composable
fun CreateRequestDropDown(
    modifier: Modifier = Modifier,
    selectionState: RequestingDocumentState,
    dropDownOpened: Boolean = false,
    onSelectionUpdated: (elements: DocumentElementsRequest) -> Unit,
    onConfirm: (request: RequestingDocumentState) -> Unit,
) {
    val heightValue = if (dropDownOpened) 500.dp else 0.dp
    val height by animateDpAsState(
        targetValue = heightValue,
        animationSpec = tween(300),
    )
    Card(
        modifier = modifier.height(height),
    ) {
        val cardScrollState = rememberScrollState()
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp)
                    .verticalScroll(cardScrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                modifier = Modifier.padding(vertical = 8.dp),
                text = "Request mDL",
                style = MaterialTheme.typography.titleSmall,
            )
            ChipsRow(
                left = selectionState.olderThan18,
                right = selectionState.olderThan21,
                onRequestFieldsToggled = onSelectionUpdated,
            )
            ChipsRow(
                left = selectionState.mandatoryFields,
                right = selectionState.fullMdl,
                onRequestFieldsToggled = onSelectionUpdated,
            )
            ChipsRow(
                left = selectionState.mdlForUsTransportation,
                right = selectionState.custom,
                onRequestFieldsToggled = onSelectionUpdated,
            )

            Spacer(modifier = Modifier.height(24.dp))
            ElementChip(
                modifier = Modifier.fillMaxWidth(),
                documentElementsRequest = selectionState.mVR,
                onRequestFieldsToggled = onSelectionUpdated,
            )

            Spacer(modifier = Modifier.height(8.dp))
            ElementChip(
                modifier = Modifier.fillMaxWidth(),
                documentElementsRequest = selectionState.micov,
                onRequestFieldsToggled = onSelectionUpdated,
            )

            Spacer(modifier = Modifier.height(8.dp))
            ElementChip(
                modifier = Modifier.fillMaxWidth(),
                documentElementsRequest = selectionState.euPid,
                onRequestFieldsToggled = onSelectionUpdated,
            )

            Spacer(modifier = Modifier.height(8.dp))
            ElementChip(
                modifier = Modifier.fillMaxWidth(),
                documentElementsRequest = selectionState.mdlWithLinkage,
                onRequestFieldsToggled = onSelectionUpdated,
            )

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onConfirm(selectionState) },
            ) {
                Text(text = "Done")
            }
        }
    }
}

@Composable
private fun ChipsRow(
    modifier: Modifier = Modifier,
    left: DocumentElementsRequest,
    right: DocumentElementsRequest,
    onRequestFieldsToggled: (elements: DocumentElementsRequest) -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ElementChip(
            modifier = Modifier.weight(1f),
            documentElementsRequest = left,
            onRequestFieldsToggled = onRequestFieldsToggled,
        )
        Spacer(modifier = Modifier.width(8.dp))
        ElementChip(
            modifier = Modifier.weight(1f),
            documentElementsRequest = right,
            onRequestFieldsToggled = onRequestFieldsToggled,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ElementChip(
    modifier: Modifier = Modifier,
    documentElementsRequest: DocumentElementsRequest,
    onRequestFieldsToggled: (request: DocumentElementsRequest) -> Unit,
) {
    FilterChip(
        modifier = modifier,
        selected = documentElementsRequest.isSelected,
        onClick = {
            onRequestFieldsToggled(documentElementsRequest)
        },
        colors =
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        label = {
            Text(text = stringResource(id = documentElementsRequest.title))
        },
        leadingIcon = {
            AnimatedVisibility(visible = documentElementsRequest.isSelected) {
                Icon(
                    imageVector = Icons.Filled.Done,
                    contentDescription = "",
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        },
    )
}
