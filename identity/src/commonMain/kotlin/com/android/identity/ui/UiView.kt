package com.android.identity.ui

import com.android.identity.securearea.PassphraseConstraints

/**
 * An interface that the UI layer can implement to provide UI services for the low-level libraries.
 *
 * See the [org.multipaz.compose.ui.UIProvider] composable for an example provider.
 */
interface UiView {
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
     * @return the passphrase entered by the user or `null` if the user dismissed the prompt.
     * @throws PassphrasePromptViewNotAvailableException if the UI layer hasn't registered any viewer.
     */
    suspend fun requestPassphrase(
        title: String,
        subtitle: String,
        passphraseConstraints: PassphraseConstraints,
        passphraseEvaluator: (suspend (enteredPassphrase: String) -> String?)?
    ): String?
}