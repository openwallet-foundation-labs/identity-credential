package com.android.identity.securearea

import com.android.identity.util.Logger

/**
 * A model for requesting passphrases from the user.
 *
 * This bridges the low-level library with the UI layer of an application. See the
 * [com.android.identity.appsupport.ui.passphrase.PassphrasePromptProvider] composable
 * for an example.
 */
object PassphrasePromptModel {
    private const val TAG = "PassphrasePromptModel"

    // No locking needed since calls should only come from the UI thread.
    //
    private var currentView: PassphrasePromptView? = null

    fun registerView(view: PassphrasePromptView) {
        if (currentView != null) {
            Logger.e(TAG, "Trying to register view, but another view which will be replaced is active: $currentView")
        }
        currentView = view
    }

    fun unregisterView(view: PassphrasePromptView) {
        if (currentView != view) {
            Logger.e(TAG, "Trying to unregister but another view is active: $currentView")
            return
        }
        currentView = null
    }

    /**
     * Requests that the UI layer should ask the user for a passphrase.
     *
     * If [passphraseEvaluator] is not `null`, it is called every time the user inputs a passphrase with
     * the passphrase that was entered. It should return `null` to indicate the passphrase is correct
     * otherwise a short message which is displayed in prompt indicating the user entered the wrong passphrase
     * and optionally how many attempts are remaining.
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
    ): String? {
        currentView?.let {
            return it.requestPassphrase(title, subtitle, passphraseConstraints, passphraseEvaluator)
        }
        throw PassphrasePromptViewNotAvailableException(
            "No PassphrasePromptView registered. " +
                    "Is PassphrasePromptProvider() or similar included in the current composition?"
        )
    }
}