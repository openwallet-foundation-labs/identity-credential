package org.multipaz.compose.prompt

import androidx.compose.runtime.Composable
import org.multipaz.prompt.PromptModel

@Composable
expect fun PromptDialogs(promptModel: PromptModel)
