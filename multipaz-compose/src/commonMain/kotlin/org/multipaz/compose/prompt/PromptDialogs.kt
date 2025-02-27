package org.multipaz.compose.prompt

import androidx.compose.runtime.Composable
import com.android.identity.prompt.PromptModel

@Composable
expect fun PromptDialogs(promptModel: PromptModel)
