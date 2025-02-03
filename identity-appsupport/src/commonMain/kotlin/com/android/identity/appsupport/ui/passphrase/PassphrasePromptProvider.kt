package com.android.identity.appsupport.ui.passphrase

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.android.identity.securearea.PassphraseConstraints
import com.android.identity.securearea.PassphrasePromptModel
import com.android.identity.securearea.PassphrasePromptView
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private data class PassphraseRequestData(
    val title: String,
    val subtitle: String,
    val passphraseConstraints: PassphraseConstraints,
    val passphraseEvaluator: (suspend (enteredPassphrase: String) -> String?)?,
    val continuation: CancellableContinuation<String?>,
)

/**
 * A composable for asking the user for a passphrase.
 *
 * This connects to the underlying libraries using [PassphrasePromptModel] when the composable is
 * part of the composition. It shows requests using a modal bottom sheet. Applications wishing
 * to use [SecureArea] instances using passphrases should include this composable on screens
 * that are visible when using keys that may need to be unlocked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassphrasePromptProvider() {

    val showPassphrasePrompt = remember { mutableStateOf<PassphraseRequestData?>(null) }

    val provider = object: PassphrasePromptView {
        override suspend fun requestPassphrase(
            title: String,
            subtitle: String,
            passphraseConstraints: PassphraseConstraints,
            passphraseEvaluator: (suspend (enteredPassphrase: String) -> String?)?
        ): String? {
            val passphrase = suspendCancellableCoroutine { continuation ->
                showPassphrasePrompt.value = PassphraseRequestData(
                    title = title,
                    subtitle = subtitle,
                    passphraseConstraints = passphraseConstraints,
                    passphraseEvaluator = passphraseEvaluator,
                    continuation = continuation
                )
            }
            showPassphrasePrompt.value = null
            return passphrase
        }
    }

    LaunchedEffect(provider) {
        PassphrasePromptModel.registerView(provider)
    }
    DisposableEffect(provider) {
        onDispose {
            PassphrasePromptModel.unregisterView(provider)
        }
    }

    // To avoid jank, we request the keyboard to be shown only when the sheet is fully expanded.
    //
    val showKeyboard = MutableStateFlow<Boolean>(false)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value ->
            showKeyboard.value = true
            true
        }
    )
    if (showPassphrasePrompt.value != null) {
        PassphrasePromptBottomSheet(
            sheetState = sheetState,
            title = showPassphrasePrompt.value!!.title,
            subtitle = showPassphrasePrompt.value!!.subtitle,
            passphraseConstraints = showPassphrasePrompt.value!!.passphraseConstraints,
            showKeyboard = showKeyboard.asStateFlow(),
            onPassphraseEntered = { enteredPassphrase ->
                val evaluator = showPassphrasePrompt.value!!.passphraseEvaluator
                if (evaluator != null) {
                    val matchResult = evaluator.invoke(enteredPassphrase)
                    if (matchResult == null) {
                        showPassphrasePrompt.value!!.continuation.resume(enteredPassphrase)
                        null
                    } else {
                        matchResult
                    }
                } else {
                    showPassphrasePrompt.value!!.continuation.resume(enteredPassphrase)
                    null
                }
            },
            onDismissed = {
                showPassphrasePrompt.value!!.continuation.resume(null)
            },
        )
    }
}