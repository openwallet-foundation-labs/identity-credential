package com.android.identity_credential.wallet.ui.prompt.passphrase

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.FragmentActivity
import com.android.identity.securearea.PassphraseConstraints
import com.android.identity_credential.wallet.ui.theme.IdentityCredentialTheme
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Show the Passphrase prompt
 *
 * Async function that renders the Passphrase Prompt through the composition of
 * a [PassphraseEntryField] from inside a Dialog Fragment. Returns the typed [String]
 * passphrase after the user taps on the "Done" key on the keyboard.
 *
 * @param activity the [FragmentActivity] to show the Dialog Fragment via Activity's FragmentManager.
 * @param constraints the constraints for the passphrase.
 * @param checkWeakPassphrase if true, checks and disallows for weak passphrase/PINs and also
 *                            shows a hint if this is the case.
 */
suspend fun showPassphrasePrompt(
    activity: FragmentActivity,
    constraints: PassphraseConstraints,
    checkWeakPassphrase: Boolean,
): String =
    suspendCancellableCoroutine { continuation ->
        val passphrasePrompt = PassphrasePrompt(
            constraints = constraints,
            checkWeakPassphrase = checkWeakPassphrase,
            onPassphraseEntered = { passphrase ->
                continuation.resume(passphrase)
            },
        )
        // show the prompt fragment
        passphrasePrompt.show(activity.supportFragmentManager, "passphrase_prompt")
    }

/**
 * PassphrasePrompt Dialog Fragment class that renders the Passphrase Prompt via Composition for the
 * purposes of running [PassphrasePrompt] synchronously to return the prompt's result as
 * a [Boolean] and not worry about callback hell.
 *
 * @param constraints the constraints for the passphrase.
 * @param checkWeakPassphrase if true, checks and disallows for weak passphrase/PINs and also
 *                            shows a hint if this is the case.
 * @param onPassphraseEntered callback issued from this dialog fragment when the user enters a passphrase
 * @extends [BottomSheetDialogFragment] that can create the Fragment's contents via Composition.
 */
class PassphrasePrompt(
    private val constraints: PassphraseConstraints,
    private val checkWeakPassphrase: Boolean,
    private val onPassphraseEntered: (String) -> Unit,
) : BottomSheetDialogFragment() {

    /**
     * Render the Composable [PassphraseEntryField] and notify when passphrase has been submitted
     */
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
                            // notify of the typed passphrase when user taps 'Done' on the keyboard
                            if (donePressed) {
                                onPassphraseEntered.invoke(passphrase)
                            }
                        }
                    )
                }
            }
        }
}