package org.multipaz.compose.prompt

import androidx.compose.runtime.Composable
import org.multipaz.prompt.AndroidPromptModel
import org.multipaz.prompt.PromptModel

@Composable
actual fun PromptDialogs(promptModel: PromptModel) {
    val model = promptModel as AndroidPromptModel
    ScanNfcTagPromptDialog(model.scanNfcPromptModel)
    BiometricPromptDialog(model.biometricPromptModel)
    PassphrasePromptDialog(model.passphrasePromptModel)
}