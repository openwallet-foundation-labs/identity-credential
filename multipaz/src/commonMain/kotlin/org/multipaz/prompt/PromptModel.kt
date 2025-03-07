package org.multipaz.prompt

import org.multipaz.securearea.PassphraseConstraints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Base model object for prompts.
 *
 * Prompt is a UI dialog (typically a modal bottom sheet dialog) that asynchronous code can
 * pop up merely by calling a specialized function like [requestPassphrase]. Such function
 * will show the dialog, and then suspend until user enters the input, performs the required
 * action or dismisses the dialog. User input is then returned to the caller
 * (or [PromptDismissedException] is thrown if the dialog is dismissed).
 *
 * [PromptModel] must exist in the current [coroutineContext] for prompt functions to work.
 *  - An instance of [PromptModel] can be added to the context using [withContext].
 *  - There is a predefined coroutine scope [promptModelScope] that is bound to the context that
 *    contains this model (it can be used similar to `ViewModel.viewModelScope` on Android).
 *  - In Composable environment [PromptModel] can be used with `rememberCoroutineScope` like
 *    this:
 *    ```
 *    val myScope = rememberCoroutineScope { promptModel }
 *    ```
 *
 * [PromptModel] provides individual model objects for each supported prompt type. Each platform has
 * its own set. [passphrasePromptModel] is the only prompt type that must exist on every platform.
 */
interface PromptModel : CoroutineContext.Element {
    object Key: CoroutineContext.Key<PromptModel>

    override val key: CoroutineContext.Key<PromptModel>
        get() = Key

    val passphrasePromptModel: SinglePromptModel<PassphraseRequest, String?>

    val promptModelScope: CoroutineScope

    companion object {
        fun get(coroutineContext: CoroutineContext): PromptModel {
            return coroutineContext[Key] ?: throw PromptModelNotAvailableException()
        }
    }
}

/**
 * Requests that the UI layer should ask the user for a passphrase.
 *
 * If [passphraseEvaluator] is not `null`, it is called every time the user inputs a passphrase with
 * the passphrase that was entered. It should return `null` to indicate the passphrase is correct
 * otherwise a short message which is displayed in prompt indicating the user entered the wrong passphrase
 * and optionally how many attempts are remaining.
 *
 * To dismiss the prompt programmatically, cancel the job the coroutine was launched in.
 *
 * @param title the title for the passphrase prompt.
 * @param subtitle the subtitle for the passphrase prompt.
 * @param passphraseConstraints the [PassphraseConstraints] for the passphrase.
 * @param passphraseEvaluator an optional function to evaluate the passphrase and give the user feedback.
 * @return the passphrase entered by the user.
 * @throws PromptDismissedException if user dismissed passphrase prompt dialog.
 * @throws PromptModelNotAvailableException if [coroutineContext] does not have [PromptModel].
 * @throws PromptUiNotAvailableException if the UI layer hasn't bound any UI for [PromptModel].
 */
suspend fun requestPassphrase(
    title: String,
    subtitle: String,
    passphraseConstraints: PassphraseConstraints,
    passphraseEvaluator: (suspend (enteredPassphrase: String) -> String?)?
): String? {
    val promptModel = PromptModel.get(coroutineContext)
    return promptModel.passphrasePromptModel.displayPrompt(PassphraseRequest(
        title,
        subtitle,
        passphraseConstraints,
        passphraseEvaluator
    ))
}

/**
 * Data for the UI to display and run passphrase dialog.
 * @param title the title for the passphrase prompt.
 * @param subtitle the subtitle for the passphrase prompt.
 * @param passphraseConstraints the [PassphraseConstraints] for the passphrase.
 * @param passphraseEvaluator an optional function to evaluate the passphrase and give the user feedback.
 */
class PassphraseRequest(
    val title: String,
    val subtitle: String,
    val passphraseConstraints: PassphraseConstraints,
    val passphraseEvaluator: (suspend (enteredPassphrase: String) -> String?)?
)
