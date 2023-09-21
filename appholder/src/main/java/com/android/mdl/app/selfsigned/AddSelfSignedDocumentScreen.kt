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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.mdl.app.R
import com.android.mdl.app.composables.PreviewLightDark
import com.android.mdl.app.composables.gradientFor
import com.android.mdl.app.composables.keystoreNameFor
import com.android.mdl.app.document.DocumentColor
import com.android.mdl.app.document.DocumentType
import com.android.mdl.app.document.SecureAreaImplementationState
import com.android.mdl.app.selfsigned.AddSelfSignedScreenState.AndroidAuthKeyCurveOption
import com.android.mdl.app.selfsigned.AddSelfSignedScreenState.MdocAuthStateOption
import com.android.mdl.app.theme.HolderAppTheme

@Composable
fun AddSelfSignedDocumentScreen(
    viewModel: AddSelfSignedViewModel,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val screenState by viewModel.screenState.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.loadConfiguration(context)
    }

    AddSelfSignedDocumentScreenContent(
        modifier = Modifier.fillMaxSize(),
        screenState = screenState,
        onDocumentTypeChanged = viewModel::updateDocumentType,
        onCardArtSelected = viewModel::updateCardArt,
        onDocumentNameChanged = viewModel::updateDocumentName,
        onKeystoreImplementationChanged = viewModel::updateKeystoreImplementation,
        onUserAuthenticationChanged = viewModel::updateUserAuthentication,
        onAuthTimeoutChanged = viewModel::updateUserAuthenticationTimeoutSeconds,
        onLskfAuthChanged = viewModel::updateLskfUnlocking,
        onBiometricAuthChanged = viewModel::updateBiometricUnlocking,
        onMdocAuthOptionChange = viewModel::updateMdocAuthOption,
        onAndroidAuthKeyCurveChanged = viewModel::updateAndroidAuthKeyCurve,
        onStrongBoxChanged = viewModel::updateStrongBox,
        onPassphraseChanged = viewModel::updatePassphrase,
        onNumberOfMsoChanged = viewModel::updateNumberOfMso,
        onMaxUseOfMsoChanged = viewModel::updateMaxUseOfMso,
        onValidityInDaysChanged = viewModel::updateValidityInDays,
        onMinValidityInDaysChanged = viewModel::updateMinValidityInDays,
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
    onLskfAuthChanged: (newValue: Boolean) -> Unit,
    onStrongBoxChanged: (newValue: Boolean) -> Unit,
    onBiometricAuthChanged: (newValue: Boolean) -> Unit,
    onMdocAuthOptionChange: (newValue: MdocAuthStateOption) -> Unit,
    onAndroidAuthKeyCurveChanged: (newValue: AndroidAuthKeyCurveOption) -> Unit,
    onPassphraseChanged: (newValue: String) -> Unit,
    onNumberOfMsoChanged: (newValue: Int) -> Unit,
    onMaxUseOfMsoChanged: (newValue: Int) -> Unit,
    onValidityInDaysChanged: (newValue: Int) -> Unit,
    onMinValidityInDaysChanged: (newValue: Int) -> Unit,
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
                AndroidSetupContainer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    isOn = screenState.userAuthentication,
                    timeoutSeconds = screenState.userAuthenticationTimeoutSeconds,
                    lskfAuthTypeState = screenState.allowLSKFUnlocking,
                    biometricAuthTypeState = screenState.allowBiometricUnlocking,
                    useStrongBox = screenState.useStrongBox,
                    onUserAuthenticationChanged = onUserAuthenticationChanged,
                    onAuthTimeoutChanged = onAuthTimeoutChanged,
                    onLskfAuthChanged = onLskfAuthChanged,
                    onBiometricAuthChanged = onBiometricAuthChanged,
                    onStrongBoxChanged = onStrongBoxChanged,
                )
                MdocAuthenticationAndroid(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    state = screenState.androidMdocAuthState,
                    onMdocAuthOptionChange = onMdocAuthOptionChange
                )
                AuthenticationKeyCurveAndroid(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    state = screenState.androidAuthKeyCurveState,
                    mDocAuthState = screenState.androidMdocAuthState,
                    onAndroidAuthKeyCurveChanged = onAndroidAuthKeyCurveChanged
                )
            } else {
                BouncyCastleSetupContainer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    state = screenState,
                    onPassphraseChanged = onPassphraseChanged
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
            CounterInput(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                label = stringResource(id = R.string.validity_in_days),
                value = screenState.validityInDays,
                onValueChange = onValidityInDaysChanged
            )
            CounterInput(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                label = stringResource(id = R.string.minimum_validity_in_days),
                value = screenState.minValidityInDays,
                onValueChange = onMinValidityInDaysChanged
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
private fun BouncyCastleSetupContainer(
    modifier: Modifier = Modifier,
    state: AddSelfSignedScreenState,
    onPassphraseChanged: (newValue: String) -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BouncyCastlePassphraseInput(
            value = state.passphrase,
            onValueChanged = onPassphraseChanged
        )
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
private fun AndroidSetupContainer(
    modifier: Modifier = Modifier,
    isOn: Boolean,
    timeoutSeconds: Int,
    lskfAuthTypeState: AddSelfSignedScreenState.AuthTypeState,
    biometricAuthTypeState: AddSelfSignedScreenState.AuthTypeState,
    useStrongBox: AddSelfSignedScreenState.AuthTypeState,
    onUserAuthenticationChanged: (isOn: Boolean) -> Unit,
    onAuthTimeoutChanged: (authTimeout: Int) -> Unit,
    onLskfAuthChanged: (isOn: Boolean) -> Unit,
    onBiometricAuthChanged: (isOn: Boolean) -> Unit,
    onStrongBoxChanged: (isOn: Boolean) -> Unit
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
                Column(modifier = Modifier.fillMaxWidth()) {
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val alpha = if (lskfAuthTypeState.canBeModified) 1f else .5f
                        ValueLabel(
                            modifier = Modifier
                                .weight(1f)
                                .alpha(alpha),
                            label = stringResource(id = R.string.user_auth_type_allow_lskf)
                        )
                        Checkbox(
                            checked = lskfAuthTypeState.isEnabled,
                            onCheckedChange = onLskfAuthChanged,
                            enabled = lskfAuthTypeState.canBeModified
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val alpha = if (biometricAuthTypeState.canBeModified) 1f else .5f
                        ValueLabel(
                            modifier = Modifier
                                .weight(1f)
                                .alpha(alpha),
                            label = stringResource(id = R.string.user_auth_type_allow_biometric)
                        )
                        Checkbox(
                            checked = biometricAuthTypeState.isEnabled,
                            onCheckedChange = onBiometricAuthChanged,
                            enabled = biometricAuthTypeState.canBeModified
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val alpha = if (useStrongBox.canBeModified) 1f else .5f
                        ValueLabel(
                            modifier = Modifier
                                .weight(1f)
                                .alpha(alpha),
                            label = stringResource(id = R.string.user_auth_use_strong_box)
                        )
                        Checkbox(
                            checked = useStrongBox.isEnabled,
                            onCheckedChange = onStrongBoxChanged,
                            enabled = useStrongBox.canBeModified
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun MdocAuthenticationAndroid(
    modifier: Modifier = Modifier,
    state: AddSelfSignedScreenState.MdocAuthOptionState,
    onMdocAuthOptionChange: (newValue: MdocAuthStateOption) -> Unit
) {
    LabeledUserInput(
        modifier = modifier,
        label = stringResource(id = R.string.mdoc_authentication_label)
    ) {
        var expanded by remember { mutableStateOf(false) }
        val alpha = if (state.isEnabled) 1f else .5f
        val clickModifier = if (state.isEnabled) {
            Modifier.clickable { expanded = true }
        } else {
            Modifier
        }
        OutlinedContainerHorizontal(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(alpha)
                .then(clickModifier)
        ) {
            ValueLabel(
                modifier = Modifier.weight(1f),
                label = mdocAuthOptionLabelFor(state.mDocAuthentication)
            )
            DropDownIndicator()
        }
        DropdownMenu(
            modifier = Modifier.fillMaxWidth(0.8f),
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            TextDropDownRow(
                label = stringResource(id = R.string.mdoc_auth_ecdsa),
                onSelected = {
                    onMdocAuthOptionChange(MdocAuthStateOption.ECDSA)
                    expanded = false
                }
            )
            TextDropDownRow(
                label = stringResource(id = R.string.mdoc_auth_mac),
                onSelected = {
                    onMdocAuthOptionChange(MdocAuthStateOption.MAC)
                    expanded = false
                }
            )
        }
    }
}


@Composable
private fun AuthenticationKeyCurveAndroid(
    modifier: Modifier = Modifier,
    state: AddSelfSignedScreenState.AndroidAuthKeyCurveState,
    mDocAuthState: AddSelfSignedScreenState.MdocAuthOptionState,
    onAndroidAuthKeyCurveChanged: (newValue: AndroidAuthKeyCurveOption) -> Unit
) {
    LabeledUserInput(
        modifier = modifier,
        label = stringResource(id = R.string.authentication_key_curve_label)
    ) {
        var keyCurveDropDownExpanded by remember { mutableStateOf(false) }
        val clickModifier = if (state.isEnabled) {
            Modifier.clickable { keyCurveDropDownExpanded = true }
        } else {
            Modifier
        }
        val alpha = if (state.isEnabled) 1f else .5f
        OutlinedContainerHorizontal(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(alpha)
                .then(clickModifier)
        ) {
            ValueLabel(
                modifier = Modifier.weight(1f),
                label = curveLabelFor(state.authCurve)
            )
            DropDownIndicator()
        }
        DropdownMenu(
            expanded = keyCurveDropDownExpanded,
            onDismissRequest = { keyCurveDropDownExpanded = false }
        ) {
            val ecCurveOption = if (mDocAuthState.mDocAuthentication == MdocAuthStateOption.ECDSA) {
                AndroidAuthKeyCurveOption.Ed25519
            } else {
                AndroidAuthKeyCurveOption.X25519
            }
            TextDropDownRow(
                label = curveLabelFor(curveOption = AndroidAuthKeyCurveOption.P_256),
                onSelected = {
                    onAndroidAuthKeyCurveChanged(AndroidAuthKeyCurveOption.P_256)
                    keyCurveDropDownExpanded = false
                }
            )
            TextDropDownRow(
                label = curveLabelFor(curveOption = ecCurveOption),
                onSelected = {
                    onAndroidAuthKeyCurveChanged(ecCurveOption)
                    keyCurveDropDownExpanded = false
                }
            )
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

@Composable
private fun mdocAuthOptionLabelFor(
    state: MdocAuthStateOption
): String {
    return when (state) {
        MdocAuthStateOption.ECDSA ->
            stringResource(id = R.string.mdoc_auth_ecdsa)

        MdocAuthStateOption.MAC ->
            stringResource(id = R.string.mdoc_auth_mac)
    }
}

@Composable
private fun curveLabelFor(
    curveOption: AndroidAuthKeyCurveOption
): String {
    return when (curveOption) {
        AndroidAuthKeyCurveOption.P_256 ->
            stringResource(id = R.string.curve_p_256)

        AndroidAuthKeyCurveOption.Ed25519 ->
            stringResource(id = R.string.curve_ed25519)

        AndroidAuthKeyCurveOption.X25519 ->
            stringResource(id = R.string.curve_x25519)
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
@PreviewLightDark
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
            onLskfAuthChanged = {},
            onBiometricAuthChanged = {},
            onStrongBoxChanged = {},
            onMdocAuthOptionChange = {},
            onAndroidAuthKeyCurveChanged = {},
            onPassphraseChanged = {},
            onNumberOfMsoChanged = {},
            onMaxUseOfMsoChanged = {},
            onValidityInDaysChanged = {},
            onMinValidityInDaysChanged = {},
            onNext = {}
        )
    }
}

@Composable
@PreviewLightDark
private fun PreviewAddSelfSignedDocumentScreenAndroidKeystoreAuthOn() {
    HolderAppTheme {
        AddSelfSignedDocumentScreenContent(
            modifier = Modifier.fillMaxSize(),
            screenState = AddSelfSignedScreenState(
                userAuthentication = true,
                allowLSKFUnlocking = AddSelfSignedScreenState.AuthTypeState(
                    isEnabled = true,
                    canBeModified = true
                ),
                allowBiometricUnlocking = AddSelfSignedScreenState.AuthTypeState(
                    isEnabled = true,
                    canBeModified = false
                ),
            ),
            onDocumentTypeChanged = {},
            onCardArtSelected = {},
            onDocumentNameChanged = {},
            onKeystoreImplementationChanged = {},
            onUserAuthenticationChanged = {},
            onAuthTimeoutChanged = {},
            onLskfAuthChanged = {},
            onBiometricAuthChanged = {},
            onStrongBoxChanged = {},
            onMdocAuthOptionChange = {},
            onAndroidAuthKeyCurveChanged = {},
            onPassphraseChanged = {},
            onNumberOfMsoChanged = {},
            onMaxUseOfMsoChanged = {},
            onValidityInDaysChanged = {},
            onMinValidityInDaysChanged = {},
            onNext = {}
        )
    }
}

@Composable
@PreviewLightDark
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
            onLskfAuthChanged = {},
            onBiometricAuthChanged = {},
            onStrongBoxChanged = {},
            onMdocAuthOptionChange = {},
            onAndroidAuthKeyCurveChanged = {},
            onPassphraseChanged = {},
            onNumberOfMsoChanged = {},
            onMaxUseOfMsoChanged = {},
            onValidityInDaysChanged = {},
            onMinValidityInDaysChanged = {},
            onNext = {}
        )
    }
}