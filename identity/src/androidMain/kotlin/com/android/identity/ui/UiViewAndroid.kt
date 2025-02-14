package com.android.identity.ui

import androidx.biometric.BiometricPrompt.CryptoObject
import com.android.identity.securearea.UserAuthenticationType
import kotlinx.coroutines.flow.StateFlow

/**
 * An interface that the UI layer can implement to provide UI services on Android for the low-level libraries.
 *
 * See the [org.multipaz.compose.ui.UIProvider] composable for an example provider.
 */
interface UiViewAndroid {
    /**
     * Prompts user for authentication.
     *
     * To dismiss the prompt programmatically, cancel the job the coroutine was launched in.
     *
     * @param cryptoObject optional [CryptoObject] to be associated with the authentication.
     * @param title the title for the authentication prompt.
     * @param subtitle the subtitle for the authentication prompt.
     * @param userAuthenticationTypes the set of allowed user authentication types, must contain at least one element.
     * @param requireConfirmation set to `true` to require explicit user confirmation after presenting passive biometric.
     * @return `true` if authentication succeed, `false` if the user dismissed the prompt.
     */
    suspend fun showBiometricPrompt(
        cryptoObject: CryptoObject?,
        title: String,
        subtitle: String,
        userAuthenticationTypes: Set<UserAuthenticationType>,
        requireConfirmation: Boolean,
    ): Boolean

    /**
     * Shows a dialog requesting the user to scan a NFC tag.
     *
     * Returns when the user dismisses the prompt.
     *
     * To dismiss the prompt programmatically, cancel the job the coroutine was launched in.
     *
     * @param message a message to show in the dialog.
     * @param icon the icon to show in the dialog.
     */
    suspend fun showScanNfcTagDialog(
        message: StateFlow<String>,
        icon: StateFlow<ScanNfcTagDialogIcon>,
    )
}
