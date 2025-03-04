package org.multipaz.compose.prompt

import androidx.compose.runtime.Composable
import com.android.identity.prompt.AndroidPromptModel
import com.android.identity.prompt.PromptModel

@Composable
actual fun PromptDialogs(promptModel: PromptModel) {
    val model = promptModel as AndroidPromptModel
    ScanNfcTagPromptDialog(model.scanNfcPromptModel)
    BiometricPromptDialog(model.biometricPromptModel)
    PassphrasePromptDialog(model.passphrasePromptModel)
}