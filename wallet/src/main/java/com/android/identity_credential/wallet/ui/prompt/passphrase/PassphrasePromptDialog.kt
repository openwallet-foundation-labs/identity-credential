package com.android.identity_credential.wallet.ui.prompt.passphrase

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.FragmentManager
import com.android.identity.securearea.PassphraseConstraints
import com.android.identity_credential.wallet.ui.theme.IdentityCredentialTheme
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume


/**
 * Show the "Passphrase Prompt" by rendering a [PassphraseEntryField] and returning the typed string
 * as the result of the prompt.
 */
suspend fun showPassphrasePrompt(
    constraints: PassphraseConstraints,
    checkWeakPassphrase: Boolean,
    fragmentManager: FragmentManager
): String  =
    suspendCancellableCoroutine { continuation ->
        val passphrasePromptDialog = PassphrasePromptDialog(
            constraints = constraints,
            checkWeakPassphrase = checkWeakPassphrase,
            passphrasePromptListener = object :
                PassphrasePromptDialog.PassphrasePromptResponseListener {
                override fun onPassphraseChanged(passphrase: String) {
                    continuation.resume(passphrase)
                }
            }
        )
        passphrasePromptDialog.show(fragmentManager, "passphrase_prompt")
    }


/**
 * Wrapper (Dialog) class for rendering the Passphrase prompt via composition since composable
 * functions cannot be defined as "suspend".
 *
 * Extends BottomSheetDialogFragment and shows up as an overlay above the current UI.
 *
 * Expects a [PassphrasePromptResponseListener] instance to be provided to notify when the user
 * finishes submitting a passphrase.
 */
class PassphrasePromptDialog(
    private val constraints: PassphraseConstraints,
    private val checkWeakPassphrase: Boolean,
    private val passphrasePromptListener: PassphrasePromptResponseListener,
) : BottomSheetDialogFragment() {

    interface PassphrasePromptResponseListener {
        fun onPassphraseChanged(passphrase: String)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        ComposeView(requireContext()).apply {
            setContent {
                IdentityCredentialTheme {
                    PassphraseEntryField(
                        constraints = constraints,
                        checkWeakPassphrase = checkWeakPassphrase,
                        onChanged = { passphrase, _, donePressed ->
                            if (donePressed) {
                                passphrasePromptListener.onPassphraseChanged(passphrase)
                            }
                        }
                    )
                }
            }
        }
}