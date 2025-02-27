package org.multipaz.compose.prompt

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import com.android.identity.prompt.PassphraseRequest
import com.android.identity.prompt.SinglePromptModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.multipaz.compose.passphrase.PassphrasePromptBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassphrasePromptDialog(model: SinglePromptModel<PassphraseRequest, String?>) {
    val dialogState = model.dialogState.collectAsState(SinglePromptModel.NoDialogState())
    val coroutineScope = rememberCoroutineScope()
    val showKeyboard = MutableStateFlow<Boolean>(false)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value ->
            showKeyboard.value = true
            true
        }
    )
    val dialogStateValue = dialogState.value
    if (dialogStateValue is SinglePromptModel.DialogShownState) {
        val dialogParameters = dialogStateValue.parameters
        PassphrasePromptBottomSheet(
            sheetState = sheetState,
            title = dialogParameters.title,
            subtitle = dialogParameters.subtitle,
            passphraseConstraints = dialogParameters.passphraseConstraints,
            showKeyboard = showKeyboard.asStateFlow(),
            onPassphraseEntered = { enteredPassphrase ->
                val evaluator = dialogParameters.passphraseEvaluator
                if (evaluator != null) {
                    val matchResult = evaluator.invoke(enteredPassphrase)
                    if (matchResult == null) {
                        dialogStateValue.resultChannel.send(enteredPassphrase)
                        null
                    } else {
                        matchResult
                    }
                } else {
                    dialogStateValue.resultChannel.send(enteredPassphrase)
                    null
                }
            },
            onDismissed = {
                coroutineScope.launch {
                    dialogStateValue.resultChannel.send(null)
                }
            },
        )
    }
}