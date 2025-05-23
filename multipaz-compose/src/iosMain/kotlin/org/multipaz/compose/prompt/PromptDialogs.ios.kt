package org.multipaz.compose.prompt

import androidx.compose.runtime.Composable
import org.multipaz.prompt.PromptModel

@Composable
actual fun PromptDialogs(promptModel: PromptModel) {
    PassphrasePromptDialog(promptModel.passphrasePromptModel)
}