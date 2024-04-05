package com.android.identity.wallet.support

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.fragment.app.Fragment
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.document.Document
import com.android.identity.mdoc.credential.MdocCredential
import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.KeyUnlockData
import com.android.identity.securearea.SecureArea
import com.android.identity.securearea.software.SoftwareSecureArea
import com.android.identity.util.Timestamp

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
     * This function should create [SecureArea.KeyUnlockData] based on the incoming
     * [Document.AuthenticationKey]. Its implementation should decide on the mechanism
     * that will do the unlocking (i.e. present a biometric prompts or other sort of UI),
     * and the way the [SecureArea.KeyUnlockData] is created.
     *
     * The function is an extension on a [Fragment] due to the nature of Android and the navigation,
     * so in case of rendering a UI specific for unlocking (like a Biometric Prompt, or a Dialog),
     * there is a provided way to navigate using the [findNavController] function.
     */
    fun Fragment.unlockKey(
        credential: MdocCredential,
        onKeyUnlocked: (unlockData: KeyUnlockData?) -> Unit,
        onUnlockFailure: (wasCancelled: Boolean) -> Unit
    )

    /**
     * Should return the current [SecureAreaSupportState] which is used by the composition
     * when rendering the composable UI.
     */
    fun getSecureAreaSupportState(): SecureAreaSupportState

    /**
     * Returns a configuration for creating authentication keys which can be persisted to disk
     * and passed to [createAuthKeySettingsFromConfiguration] every time new authentication keys
     * need to be created.
     */
    fun createAuthKeySettingsConfiguration(secureAreaSupportState: SecureAreaSupportState): ByteArray

    /**
     * Creates a [SecureArea.CreateKeySettings] instead based on the settings previously created
     * with [createAuthKeySettingsConfiguration] and the given challenge and validity period.
     */
    fun createAuthKeySettingsFromConfiguration(
        encodedConfiguration: ByteArray,
        challenge: ByteArray,
        validFrom: Timestamp,
        validUntil: Timestamp
    ): CreateKeySettings

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
            secureArea: SecureArea,
        ): SecureAreaSupport {
            return when (secureArea) {
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