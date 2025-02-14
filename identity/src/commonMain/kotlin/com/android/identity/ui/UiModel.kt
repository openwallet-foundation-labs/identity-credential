package com.android.identity.ui

import com.android.identity.securearea.PassphraseConstraints
import com.android.identity.util.Logger

/**
 * A model for interacting with the UI layer.
 */
object UiModel: UiView {
    private const val TAG = "UiModel"

    // No locking needed since calls should only come from the UI thread.
    //
    private var currentView: UiView? = null

    /**
     * Register a [UiView] for servicing UI requests.
     *
     * @param view the [UiView] to register.
     */
    fun registerView(view: UiView) {
        if (currentView != null) {
            Logger.e(TAG, "Trying to register view, but another view which will be replaced is active: $currentView")
        }
        currentView = view
    }

    /**
     * Unregister a [UiView] previously registered with [registerView].
     *
     * @param view the [UiView] to unregister.
     */
    fun unregisterView(view: UiView) {
        if (currentView != view) {
            Logger.e(TAG, "Trying to unregister but another view is active: $currentView")
            return
        }
        currentView = null
    }

    override suspend fun requestPassphrase(
        title: String,
        subtitle: String,
        passphraseConstraints: PassphraseConstraints,
        passphraseEvaluator: (suspend (enteredPassphrase: String) -> String?)?
    ): String? {
        currentView?.let {
            return it.requestPassphrase(title, subtitle, passphraseConstraints, passphraseEvaluator)
        }
        throw UiViewNotAvailableException(
            "No UIView registered. Is UIProvider() or similar included in the current composition?"
        )
    }

}