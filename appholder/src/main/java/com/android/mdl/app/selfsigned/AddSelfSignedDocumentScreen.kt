package com.android.mdl.app.selfsigned

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.with
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.mdl.app.R
import com.android.mdl.app.composables.gradientFor
import com.android.mdl.app.composables.keystoreNameFor
import com.android.mdl.app.document.DocumentColor
import com.android.mdl.app.document.DocumentType
import com.android.mdl.app.document.SecureAreaImplementationState
import com.android.mdl.app.theme.HolderAppTheme

@Composable
fun AddSelfSignedDocumentScreen(
    viewModel: AddSelfSignedViewModel,
    onNext: () -> Unit
) {
    val screenState by viewModel.screenState.collectAsState()

    AddSelfSignedDocumentScreenContent(
        modifier = Modifier.fillMaxSize(),
        screenState = screenState,
        onDocumentTypeChanged = viewModel::updateDocumentType,
        onCardArtSelected = viewModel::updateCardArt,
        onDocumentNameChanged = viewModel::updateDocumentName,
        onKeystoreImplementationChanged = viewModel::updateKeystoreImplementation,
        onUserAuthenticationChanged = viewModel::updateUserAuthentication,
        onAuthTimeoutChanged = viewModel::updateUserAuthenticationTimeoutSeconds,
        onPassphraseChanged = viewModel::updatePassphrase,
        onNumberOfMsoChanged = viewModel::updateNumberOfMso,
        onMaxUseOfMsoChanged = viewModel::updateMaxUseOfMso,
        onNext = onNext
    )
}

@Composable
private fun AddSelfSignedDocumentScreenContent(
    modifier: Modifier,
    screenState: AddSelfSignedScreenState,
    onDocumentTypeChanged: (newType: DocumentType) -> Unit,
    onCardArtSelected: (newCardArt: DocumentColor) -> Unit,
    onDocumentNameChanged: (newValue: String) -> Unit,
    onKeystoreImplementationChanged: (newImplementation: SecureAreaImplementationState) -> Unit,
    onUserAuthenticationChanged: (isOn: Boolean) -> Unit,
    onAuthTimeoutChanged: (newValue: Int) -> Unit,
    onPassphraseChanged: (newValue: String) -> Unit,
    onNumberOfMsoChanged: (newValue: Int) -> Unit,
    onMaxUseOfMsoChanged: (newValue: Int) -> Unit,
    onNext: () -> Unit
) {
    Scaffold(modifier = modifier) { paddingValues ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.fillMaxWidth())
            DocumentTypeChooser(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                currentType = screenState.documentType,
                onDocumentTypeSelected = onDocumentTypeChanged
            )
            CardArtChooser(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                currentCardArt = screenState.cardArt,
                onCardArtSelected = onCardArtSelected
            )
            DocumentNameInput(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                value = screenState.documentName,
                onValueChanged = onDocumentNameChanged
            )
            KeystoreImplementationChooser(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                currentImplementation = screenState.secureAreaImplementationState,
                onKeystoreImplementationChanged = onKeystoreImplementationChanged
            )
            if (screenState.isAndroidKeystoreSelected) {
                UserAuthenticationToggle(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    isOn = screenState.userAuthentication,
                    timeoutSeconds = screenState.userAuthenticationTimeoutSeconds,
                    onUserAuthenticationChanged = onUserAuthenticationChanged,
                    onAuthTimeoutChanged = onAuthTimeoutChanged
                )
            } else {
                BouncyCastlePassphraseInput(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    value = screenState.passphrase,
                    onValueChanged = onPassphraseChanged
                )
            }
            CounterInput(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                label = stringResource(id = R.string.txt_number_mso),
                value = screenState.numberOfMso,
                onValueChange = onNumberOfMsoChanged
            )
            CounterInput(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                label = stringResource(id = R.string.txt_max_use_mso),
                value = screenState.maxUseOfMso,
                onValueChange = onMaxUseOfMsoChanged
            )
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                onClick = onNext
            ) {
                Text(text = "Next")
            }
        }
    }
}

@Composable
private fun DocumentTypeChooser(
    modifier: Modifier = Modifier,
    currentType: DocumentType,
    onDocumentTypeSelected: (newType: DocumentType) -> Unit
) {
    LabeledUserInput(
        modifier = modifier,
        label = stringResource(id = R.string.txt_document_type)
    ) {
        var expanded by remember { mutableStateOf(false) }
        OutlinedContainerHorizontal(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
        ) {
            ValueLabel(
                modifier = Modifier.weight(1f),
                label = stringResource(id = documentNameFor(currentType))
            )
            DropDownIndicator()
        }
        DropdownMenu(
            modifier = Modifier
                .fillMaxWidth(0.8f),
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            TextDropDownRow(
                label = stringResource(id = documentNameFor(DocumentType.MDL)),
                onSelected = {
                    onDocumentTypeSelected(DocumentType.MDL)
                    expanded = false
                }
            )
            TextDropDownRow(
                label = stringResource(id = documentNameFor(DocumentType.MVR)),
                onSelected = {
                    onDocumentTypeSelected(DocumentType.MVR)
                    expanded = false
                }
            )
            TextDropDownRow(
                label = stringResource(id = documentNameFor(DocumentType.MICOV)),
                onSelected = {
                    onDocumentTypeSelected(DocumentType.MICOV)
                    expanded = false
                }
            )
            TextDropDownRow(
                label = stringResource(id = documentNameFor(DocumentType.EUPID)),
                onSelected = {
                    onDocumentTypeSelected(DocumentType.EUPID)
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun CardArtChooser(
    modifier: Modifier,
    currentCardArt: DocumentColor,
    onCardArtSelected: (newCardArt: DocumentColor) -> Unit
) {
    LabeledUserInput(
        modifier = modifier,
        label = stringResource(id = R.string.txt_card_art)
    ) {
        var expanded by remember { mutableStateOf(false) }
        OutlinedContainerHorizontal(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            outlineBrush = gradientFor(currentCardArt)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(gradientFor(currentCardArt), RoundedCornerShape(8.dp)),
            )
            ValueLabel(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .weight(1f),
                label = stringResource(id = colorNameFor(currentCardArt))
            )
            DropDownIndicator()
        }
        DropdownMenu(
            modifier = Modifier
                .fillMaxWidth(0.8f),
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            CardArtDropDownRow(
                cardArt = DocumentColor.Green,
                onSelected = {
                    onCardArtSelected(DocumentColor.Green)
                    expanded = false
                }
            )
            CardArtDropDownRow(
                cardArt = DocumentColor.Yellow,
                onSelected = {
                    onCardArtSelected(DocumentColor.Yellow)
                    expanded = false
                }
            )
            CardArtDropDownRow(
                cardArt = DocumentColor.Blue,
                onSelected = {
                    onCardArtSelected(DocumentColor.Blue)
                    expanded = false
                }
            )
            CardArtDropDownRow(
                cardArt = DocumentColor.Red,
                onSelected = {
                    onCardArtSelected(DocumentColor.Red)
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun DocumentNameInput(
    modifier: Modifier = Modifier,
    value: String,
    onValueChanged: (newValue: String) -> Unit
) {
    LabeledUserInput(
        modifier = modifier,
        label = stringResource(id = R.string.txt_document_name)
    ) {
        OutlinedContainerHorizontal(modifier = Modifier.fillMaxWidth()) {
            BasicTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                textStyle = MaterialTheme.typography.labelMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                value = value,
                onValueChange = onValueChanged,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface)
            )
        }
    }
}

@Composable
private fun BouncyCastlePassphraseInput(
    modifier: Modifier = Modifier,
    value: String,
    onValueChanged: (newValue: String) -> Unit
) {
    OutlinedContainerHorizontal(modifier = modifier) {
        Box(contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                textStyle = MaterialTheme.typography.labelMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                value = value,
                onValueChange = onValueChanged,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface)
            )
            if (value.isEmpty()) {
                Text(
                    text = stringResource(id = R.string.keystore_bouncy_castle_passphrase_hint),
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f)
                    ),
                )
            }
        }
    }
}

@Composable
private fun KeystoreImplementationChooser(
    modifier: Modifier = Modifier,
    currentImplementation: SecureAreaImplementationState,
    onKeystoreImplementationChanged: (newImplementation: SecureAreaImplementationState) -> Unit
) {
    LabeledUserInput(
        modifier = modifier,
        label = stringResource(id = R.string.txt_keystore_implementation)
    ) {
        var expanded by remember { mutableStateOf(false) }
        OutlinedContainerHorizontal(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
        ) {
            ValueLabel(
                modifier = Modifier.weight(1f),
                label = stringResource(id = keystoreNameFor(currentImplementation))
            )
            DropDownIndicator()
        }
        DropdownMenu(
            modifier = Modifier
                .fillMaxWidth(0.8f),
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            TextDropDownRow(
                label = stringResource(id = keystoreNameFor(SecureAreaImplementationState.Android)),
                onSelected = {
                    onKeystoreImplementationChanged(SecureAreaImplementationState.Android)
                    expanded = false
                }
            )
            TextDropDownRow(
                label = stringResource(id = keystoreNameFor(SecureAreaImplementationState.BouncyCastle)),
                onSelected = {
                    onKeystoreImplementationChanged(SecureAreaImplementationState.BouncyCastle)
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun UserAuthenticationToggle(
    modifier: Modifier = Modifier,
    isOn: Boolean,
    timeoutSeconds: Int,
    onUserAuthenticationChanged: (isOn: Boolean) -> Unit,
    onAuthTimeoutChanged: (authTimeout: Int) -> Unit,
) {
    Column(modifier = modifier) {
        OutlinedContainerVertical(modifier = Modifier.fillMaxWidth()) {
            val labelOn = stringResource(id = R.string.user_authentication_on)
            val labelOff = stringResource(id = R.string.user_authentication_off)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ValueLabel(
                    modifier = Modifier.weight(1f),
                    label = if (isOn) labelOn else labelOff,
                )
                Switch(
                    modifier = Modifier.padding(start = 8.dp),
                    checked = isOn,
                    onCheckedChange = onUserAuthenticationChanged
                )
            }
            AnimatedVisibility(
                modifier = Modifier.fillMaxWidth(),
                visible = isOn
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ValueLabel(
                        modifier = Modifier.weight(1f),
                        label = stringResource(id = R.string.keystore_android_user_auth_timeout)
                    )
                    NumberChanger(
                        number = timeoutSeconds,
                        onNumberChanged = onAuthTimeoutChanged,
                        counterTextStyle = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun CounterInput(
    modifier: Modifier = Modifier,
    label: String,
    value: Int,
    onValueChange: (newValue: Int) -> Unit
) {
    Column(modifier = modifier) {
        OutlinedContainerHorizontal(modifier = Modifier.fillMaxWidth()) {
            ValueLabel(
                modifier = Modifier.weight(1f),
                label = label
            )
            NumberChanger(number = value, onNumberChanged = onValueChange)
        }
    }
}

@Composable
private fun TextDropDownRow(
    modifier: Modifier = Modifier,
    label: String,
    onSelected: () -> Unit
) {
    DropdownMenuItem(
        modifier = modifier,
        text = {
            ValueLabel(
                modifier = Modifier.fillMaxWidth(),
                label = label
            )
        },
        onClick = onSelected
    )
}

@Composable
private fun CardArtDropDownRow(
    modifier: Modifier = Modifier,
    cardArt: DocumentColor,
    onSelected: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(gradientFor(cardArt), RoundedCornerShape(8.dp)),
                )
                ValueLabel(label = stringResource(id = colorNameFor(cardArt)))
            }
        },
        onClick = onSelected
    )
}

@Composable
private fun LabeledUserInput(
    modifier: Modifier = Modifier,
    label: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            ValueLabel(label = label)
        }
        content()
    }
}

@Composable
fun OutlinedContainerHorizontal(
    modifier: Modifier = Modifier,
    outlineBorderWidth: Dp = 2.dp,
    outlineBrush: Brush? = null,
    content: @Composable RowScope.() -> Unit
) {
    val brush = outlineBrush ?: SolidColor(MaterialTheme.colorScheme.outline)
    Row(
        modifier = modifier
            .heightIn(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(outlineBorderWidth, brush, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.inverseOnSurface),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()
        }
    }
}

@Composable
fun OutlinedContainerVertical(
    modifier: Modifier = Modifier,
    outlineBorderWidth: Dp = 2.dp,
    outlineBrush: Brush? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val brush = outlineBrush ?: SolidColor(MaterialTheme.colorScheme.outline)
    Row(
        modifier = modifier
            .heightIn(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(outlineBorderWidth, brush, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.inverseOnSurface),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            content()
        }
    }
}

@Composable
private fun ValueLabel(
    modifier: Modifier = Modifier,
    label: String
) {
    Text(
        modifier = modifier,
        text = label,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.labelMedium
    )
}

@Composable
private fun DropDownIndicator(
    modifier: Modifier = Modifier
) {
    Icon(
        modifier = modifier,
        imageVector = Icons.Default.ArrowDropDown,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface
    )
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun NumberChanger(
    modifier: Modifier = Modifier,
    number: Int,
    onNumberChanged: (newValue: Int) -> Unit,
    counterTextStyle: TextStyle = MaterialTheme.typography.bodyLarge
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onNumberChanged(number - 1) }) {
            Icon(imageVector = Icons.Default.Remove, contentDescription = null)
        }
        AnimatedContent(
            targetState = number,
            label = "",
            transitionSpec = {
                if (targetState > initialState) {
                    slideInVertically { -it } with slideOutVertically { it }
                } else {
                    slideInVertically { it } with slideOutVertically { -it }
                }
            }
        ) { count ->
            Text(
                text = "$count",
                textAlign = TextAlign.Center,
                style = counterTextStyle
            )
        }
        IconButton(onClick = { onNumberChanged(number + 1) }) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
        }
    }
}

@StringRes
private fun documentNameFor(documentType: DocumentType): Int {
    return when (documentType) {
        is DocumentType.MDL -> R.string.document_type_mdl
        is DocumentType.MVR -> R.string.document_type_mvr
        is DocumentType.MICOV -> R.string.document_type_micov
        is DocumentType.EUPID -> R.string.document_type_eu_pid
    }
}

@StringRes
private fun colorNameFor(cardArt: DocumentColor): Int {
    return when (cardArt) {
        is DocumentColor.Green -> R.string.document_color_green
        is DocumentColor.Yellow -> R.string.document_color_yellow
        is DocumentColor.Blue -> R.string.document_color_blue
        is DocumentColor.Red -> R.string.document_color_red
    }
}

@Composable
@Preview
private fun PreviewAddSelfSignedDocumentScreenAndroidKeystore() {
    HolderAppTheme {
        AddSelfSignedDocumentScreenContent(
            modifier = Modifier.fillMaxSize(),
            screenState = AddSelfSignedScreenState(),
            onDocumentTypeChanged = {},
            onCardArtSelected = {},
            onDocumentNameChanged = {},
            onKeystoreImplementationChanged = {},
            onUserAuthenticationChanged = {},
            onAuthTimeoutChanged = {},
            onPassphraseChanged = {},
            onNumberOfMsoChanged = {},
            onMaxUseOfMsoChanged = {},
            onNext = {}
        )
    }
}

@Composable
@Preview
private fun PreviewAddSelfSignedDocumentScreenBouncyCastleKeystore() {
    HolderAppTheme {
        AddSelfSignedDocumentScreenContent(
            modifier = Modifier.fillMaxSize(),
            screenState = AddSelfSignedScreenState(
                secureAreaImplementationState = SecureAreaImplementationState.BouncyCastle
            ),
            onDocumentTypeChanged = {},
            onCardArtSelected = {},
            onDocumentNameChanged = {},
            onKeystoreImplementationChanged = {},
            onUserAuthenticationChanged = {},
            onAuthTimeoutChanged = {},
            onPassphraseChanged = {},
            onNumberOfMsoChanged = {},
            onMaxUseOfMsoChanged = {},
            onNext = {}
        )
    }
}