package com.android.identity.wallet.support

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.fragment.app.Fragment
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.credential.Credential
import com.android.identity.securearea.SecureArea
import com.android.identity.securearea.SoftwareSecureArea
import com.android.identity.wallet.util.ProvisioningUtil

interface SecureAreaSupport {

    /**
     * This function should create a composable that will render the portion of the UI
     * for the specific [SecureArea] setup inside the [AddSelfSignedDocumentScreen].
     *
     * The composable should hold and manage its state internally, and expose it through
     * the [onUiStateUpdated] lambda. The state must be an implementation of [SecureAreaSupportState]
     */
    @Composable
    fun SecureAreaAuthUi(onUiStateUpdated: (newState: SecureAreaSupportState) -> Unit)

    /**
     * This function should create [SecureArea.KeyUnlockData] based on the incoming [Credential].
     * It's implementation should decide on the mechanism that will do the unlocking (i.e. present
     * a biometric prompts or other sort of UI), and the way the [SecureArea.KeyUnlockData] is created.
     *
     * The function is an extension on a [Fragment] due to the nature of Android and the navigation,
     * so in case of rendering a UI specific for unlocking (like a Biometric Prompt, or a Dialog),
     * there is a provided way to navigate using the [findNavController] function.
     */
    fun Fragment.unlockKey(
        credential: Credential,
        onKeyUnlocked: (unlockData: SecureArea.KeyUnlockData?) -> Unit,
        onUnlockFailure: (wasCancelled: Boolean) -> Unit
    )

    /**
     * Should return the current [SecureAreaSupportState] which is used by the composition
     * when rendering the composable UI.
     */
    fun getSecureAreaSupportState(): SecureAreaSupportState

    companion object {
        fun getInstance(
            context: Context,
            currentSecureArea: CurrentSecureArea
        ): SecureAreaSupport {
            return when (currentSecureArea.secureArea) {
                is AndroidKeystoreSecureArea -> {
                    val capabilities = AndroidKeystoreSecureArea.Capabilities(context)
                    AndroidKeystoreSecureAreaSupport(capabilities)
                }

                is SoftwareSecureArea -> SoftwareKeystoreSecureAreaSupport()

                else -> SecureAreaSupportNull()
            }
        }

        fun getInstance(
            context: Context,
            credential: Credential
        ): SecureAreaSupport {
            return when (credential.credentialSecureArea) {
                is AndroidKeystoreSecureArea -> {
                    val capabilities = AndroidKeystoreSecureArea.Capabilities(context)
                    AndroidKeystoreSecureAreaSupport(capabilities)
                }

                is SoftwareSecureArea -> SoftwareKeystoreSecureAreaSupport()

                else -> SecureAreaSupportNull()
            }
        }
    }
}

/**
 * Utility function to convert a [SecureArea] implementation into a corresponding state
 * used by the Jetpack Compose composition when rendering the UI.
 */
fun SecureArea.toSecureAreaState(): CurrentSecureArea {
    return CurrentSecureArea(this)
}