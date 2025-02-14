package com.android.identity.ui

import androidx.biometric.BiometricPrompt
import com.android.identity.securearea.UserAuthenticationType
import com.android.identity.util.Logger
import kotlinx.coroutines.flow.StateFlow

/**
 * A model for interacting with the UI layer, with Android specific functionality.
 */
object UiModelAndroid: UiViewAndroid {
    private const val TAG = "UiModelAndroid"

    // No locking needed since calls should only come from the UI thread.
    //
    private var currentView: UiViewAndroid? = null

    /**
     * Register a [UiViewAndroid] for servicing UI requests.
     *
     * @param view the [UiViewAndroid] to register.
     */
    fun registerView(view: UiViewAndroid) {
        if (currentView != null) {
            Logger.e(
                TAG,
                "Trying to register view, but another view which will be replaced is active: $currentView"
            )
        }
        currentView = view
    }

    /**
     * Unregister a [UiViewAndroid] previously registered with [registerView].
     *
     * @param view the [UiViewAndroid] to unregister.
     */
    fun unregisterView(view: UiViewAndroid) {
        if (currentView != view) {
            Logger.e(TAG, "Trying to unregister but another view is active: $currentView")
            return
        }
        currentView = null
    }

    override suspend fun showBiometricPrompt(
        cryptoObject: BiometricPrompt.CryptoObject?,
        title: String,
        subtitle: String,
        userAuthenticationTypes: Set<UserAuthenticationType>,
        requireConfirmation: Boolean
    ): Boolean {
        currentView?.let {
            return it.showBiometricPrompt(cryptoObject, title, subtitle, userAuthenticationTypes, requireConfirmation)
        }
        throw UiViewNotAvailableException(
            "No UIViewAndroid registered. Is UIProvider() or similar included in the current composition?"
        )
    }

    override suspend fun showScanNfcTagDialog(
        message: StateFlow<String>,
        icon: StateFlow<ScanNfcTagDialogIcon>
    ) {
        currentView?.let {
            it.showScanNfcTagDialog(message, icon)
        }
        throw UiViewNotAvailableException(
            "No UIViewAndroid registered. Is UIProvider() or similar included in the current composition?"
        )
    }
}