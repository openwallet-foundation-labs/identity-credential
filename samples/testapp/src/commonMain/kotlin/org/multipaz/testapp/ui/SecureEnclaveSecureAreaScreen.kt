package org.multipaz.testapp.ui

import androidx.compose.runtime.Composable
import org.multipaz.prompt.PromptModel

@Composable
expect fun SecureEnclaveSecureAreaScreen(
    promptModel: PromptModel,
    showToast: (message: String) -> Unit
)
