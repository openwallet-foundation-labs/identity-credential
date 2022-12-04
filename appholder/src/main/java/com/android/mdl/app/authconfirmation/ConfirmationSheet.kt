package com.android.mdl.app.authconfirmation

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.mdl.app.R
import com.android.mdl.app.authconfirmation.ConfirmationSheetData.DocumentElement
import com.android.mdl.app.theme.HolderAppTheme

@Composable
fun ConfirmationSheet(
    modifier: Modifier = Modifier,
    title: String,
    isTrustedReader: Boolean = false,
    isSendingInProgress: Boolean = false,
    sheetData: List<ConfirmationSheetData> = emptyList(),
    onElementToggled: (element: RequestedElement) -> Unit = { },
    onConfirm: () -> Unit = {},
    onCancel: () -> Unit = {}
) {
    Column(modifier = modifier) {
        BottomSheetHandle(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )
        if (isTrustedReader) {
            TrustedReaderCheck(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
        }
        Text(
            text = title,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.4f)
        ) {
            DocumentElements(sheetData, onElementToggled)
            if (isSendingInProgress) {
                LoadingIndicator(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                )
            }
        }
        SheetActions(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            enabled = !isSendingInProgress,
            onCancel = onCancel,
            onConfirm = onConfirm
        )
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
private fun TrustedReaderCheck(
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.Center) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                modifier = Modifier.size(24.dp),
                imageVector = Icons.Default.Check,
                contentDescription = "",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun DocumentTitle(
    modifier: Modifier = Modifier,
    document: ConfirmationSheetData
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            textAlign = TextAlign.Center,
            text = document.documentName,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun ChipsRow(
    modifier: Modifier = Modifier,
    left: DocumentElement,
    right: DocumentElement?,
    onElementToggled: (element: RequestedElement) -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        val chipModifier = if (right != null) Modifier.weight(1f) else Modifier
        ElementChip(
            modifier = chipModifier,
            documentElement = left,
            onElementToggled = onElementToggled
        )
        right?.let {
            Spacer(modifier = Modifier.width(8.dp))
            ElementChip(
                modifier = chipModifier,
                documentElement = right,
                onElementToggled = onElementToggled
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ElementChip(
    modifier: Modifier = Modifier,
    documentElement: DocumentElement,
    onElementToggled: (element: RequestedElement) -> Unit
) {
    var isChecked by remember { mutableStateOf(true) }
    FilterChip(
        modifier = modifier,
        selected = isChecked,
        onClick = {
            isChecked = !isChecked
            onElementToggled(documentElement.requestedElement)
        },
        label = { Text(text = documentElement.displayName) },
        leadingIcon = {
            AnimatedVisibility(visible = isChecked) {
                Icon(
                    imageVector = Icons.Filled.Done,
                    contentDescription = "",
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        }
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DocumentElements(
    sheetData: List<ConfirmationSheetData>,
    onElementToggled: (element: RequestedElement) -> Unit
) {
    LazyColumn(modifier = Modifier.focusGroup()) {
        sheetData.forEach { document ->
            stickyHeader {
                DocumentTitle(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    document = document
                )
            }
            val grouped = document.elements.chunked(2).map { pair ->
                if (pair.size == 1) Pair(pair.first(), null)
                else Pair(pair.first(), pair.last())
            }
            items(grouped.size) { index ->
                val items = grouped[index]
                ChipsRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    left = items.first,
                    right = items.second,
                    onElementToggled = onElementToggled
                )
            }
        }
    }
}

@Composable
private fun LoadingIndicator(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun SheetActions(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(
            modifier = Modifier.weight(1f),
            enabled = enabled,
            onClick = {
                if (enabled) {
                    onCancel()
                }
            }
        ) {
            Text(text = stringResource(id = R.string.bt_cancel))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            modifier = Modifier.weight(1f),
            enabled = enabled,
            onClick = {
                if (enabled) {
                    onConfirm()
                }
            }
        ) {
            Text(text = stringResource(id = R.string.btn_send_data))
        }
    }
}

@Composable
@Preview(name = "Default", showBackground = true)
@Preview(name = "Default", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
private fun PreviewConfirmationSheet() {
    HolderAppTheme {
        ConfirmationSheet(
            modifier = Modifier.fillMaxSize(),
            title = "Title"
        )
    }
}

@Composable
@Preview(name = "Default With Trusted Reader", showBackground = true)
@Preview(name = "Default With Trusted Reader", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
private fun PreviewConfirmationSheetTrustedReader() {
    HolderAppTheme {
        ConfirmationSheet(
            modifier = Modifier.fillMaxSize(),
            title = "Title",
            isTrustedReader = true
        )
    }
}

@Composable
@Preview(name = "Document With Trusted Reader", showBackground = true)
@Preview(name = "Document With Trusted Reader", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
private fun PreviewConfirmationSheetWithDocumentAndTrustedReader() {
    HolderAppTheme {
        ConfirmationSheet(
            modifier = Modifier.fillMaxSize(),
            title = "Trusted verifier 'Google' is requesting the following information",
            isTrustedReader = true,
            sheetData = listOf(
                ConfirmationSheetData(
                    documentName = "Driving Licence  |  mDL",
                    elements = (1..11).map { DocumentElement("Property $it", RequestedElement("$it", "namespace")) }
                )
            )
        )
    }
}

@Composable
@Preview(name = "Sending progress", showBackground = true)
@Preview(name = "Sending progress", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
private fun PreviewConfirmationSendingProgress() {
    HolderAppTheme {
        ConfirmationSheet(
            modifier = Modifier.fillMaxSize(),
            title = "Trusted verifier 'Google' is requesting the following information",
            isSendingInProgress = true,
            sheetData = listOf(
                ConfirmationSheetData(
                    documentName = "Driving Licence  |  mDL",
                    elements = (1..11).map { DocumentElement("Property $it", RequestedElement("$it", "namespace")) }
                )
            )
        )
    }
}