package org.multipaz.compose

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.android.identity.securearea.PassphraseConstraints
import com.android.identity.ui.UiModel
import com.android.identity.ui.UiView
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.multipaz.compose.passphrase.PassphrasePromptBottomSheet
import kotlin.coroutines.resume

private data class PassphraseRequestData(
    val title: String,
    val subtitle: String,
    val passphraseConstraints: PassphraseConstraints,
    val passphraseEvaluator: (suspend (enteredPassphrase: String) -> String?)?,
    val continuation: CancellableContinuation<String?>,
)

/**
 * A composable for providing UI services to the low-level libraries.
 *
 * This connects to the underlying libraries using [UiModel] and [UIModelAndroid] when the
 * composable is part of the composition.
 *
 */
@Composable
expect fun UiProvider(lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UiProviderCommon(lifecycleOwner: LifecycleOwner) {
    val showPassphrasePrompt = remember { mutableStateOf<PassphraseRequestData?>(null) }

    val provider = object: UiView {
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
                continuation.invokeOnCancellation {
                    showPassphrasePrompt.value = null
                }
            }
            showPassphrasePrompt.value = null
            return passphrase
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                UiModel.registerView(provider)
            } else if (event == Lifecycle.Event.ON_STOP) {
                UiModel.unregisterView(provider)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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