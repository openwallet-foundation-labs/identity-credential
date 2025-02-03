package com.android.identity.securearea

/**
 * An interface that the UI layer can implement to display passphrase prompt dialogs.
 *
 * See the [com.android.identity.appsupport.ui.passphrase.PassphrasePromptProvider] composable
 * for an example.
 */
interface PassphrasePromptView {
    /**
     * Called when the UI layer should ask the user for a passphrase.
     *
     * See [PassphrasePromptModel.requestPassphrase] for more details on how [passphraseEvaluator].
     *
     * @param title the title for the passphrase prompt.
     * @param subtitle the subtitle for the passphrase prompt.
     * @param passphraseConstraints the [PassphraseConstraints] for the passphrase.
     * @param passphraseEvaluator an optional function to evaluate the passphrase and give the user feedback.
     * @return the passphrase entered by the user or `null` if the user dismissed the prompt.
     */
    suspend fun requestPassphrase(
        title: String,
        subtitle: String,
        passphraseConstraints: PassphraseConstraints,
        passphraseEvaluator: (suspend (enteredPassphrase: String) -> String?)?
    ): String?
}