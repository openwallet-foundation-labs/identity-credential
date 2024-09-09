@file:OptIn(ExperimentalMaterial3Api::class)

package com.android.identity.kmmtestapp.ui.prompt.passphrase

import android.app.Dialog
import android.content.DialogInterface
import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.FragmentActivity
import com.android.identity.appsupport.ui.PassphraseEntryField
import com.android.identity.appsupport.ui.prompt.passphrase.PassphrasePrompt
import com.android.identity.securearea.PassphraseConstraints
import com.android.identity.testapp.ui.IdentityCredentialTheme
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Show the Passphrase prompt
 *
 * Async function that renders the Passphrase Prompt through the composition of
 * a [PassphraseEntryField] from inside a Dialog Fragment. Returns the typed [String]
 * passphrase after the user taps on the "Done" key on the keyboard.
 *
 * @param activity the [FragmentActivity] to show the Dialog Fragment via Activity's FragmentManager.
 * @param constraints the constraints for the passphrase.
 * @param title the top-most text of the Prompt.
 * @param content contains more information about the Prompt.
 * @return the typed passphrase or null if user canceled the prompt.
 */
//actual suspend fun showPassphrasePrompt(
//    constraints: PassphraseConstraints,
//    title: String,
//    content: String,
//): String? =
//    suspendCancellableCoroutine { continuation ->
//        val passphrasePrompt = PassphrasePrompt(
//            constraints = constraints,
//            title = title,
//            content = content,
//            onPassphraseResult = { passphrase ->
//                continuation.resume(passphrase)
//            },
//        )
//        // show the Dialog Fragment Prompt
//        passphrasePrompt.show(MainActivity.instance.supportFragmentManager, "passphrase_prompt")
//    }


/**
 * PassphrasePrompt Dialog Fragment class that renders the Passphrase Prompt via Composition for the
 * purposes of running [PassphrasePromptDialogFragment] synchronously to return the prompt's result as
 * a [Boolean] and not worry about callback hell.
 *
 * @param title the top-most text of the Prompt.
 * @param content contains more information about the Prompt.
 * @param constraints the constraints for the passphrase.
 * @param onDismissDialog the callback invoked with a Boolean indicating whether onPassphraseResult() has
 *      already been called which means the dismissal of the dialog is from the completion of the
 *      Prompt rather than be dismissed because the user tapped outside of this dialog fragment.
 * @param onPassphraseResult callback issued from this dialog fragment when the user enters a passphrase
 * @extends [BottomSheetDialogFragment] that can create the Fragment's contents via Composition.
 */
class PassphrasePromptDialogFragment(
    private val title: String,
    private val content: String,
    private val constraints: PassphraseConstraints,
    private val onPassphraseResult: (String?) -> Unit,
) : BottomSheetDialogFragment() {

    // flag used by the override onDismiss() to know if the dismiss was premature or not.
    var repliedWithPassphrase = false

    /**
     * Fully Expanded Bottom Sheet Dialog
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            setOnShowListener { dialog ->
                val bottomSheet =
                    (dialog as BottomSheetDialog).findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                BottomSheetBehavior.from(bottomSheet!!).apply {
                    state = BottomSheetBehavior.STATE_EXPANDED
                    skipCollapsed = true // Prevent collapsing
                    (dialog as? BottomSheetDialog)?.behavior?.peekHeight =
                        Resources.getSystem().displayMetrics.heightPixels
                    dialog.window?.setDimAmount(0f) // Remove background dimming
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        // if dismiss is called before sending the passphrase result (such as user tapping on the
        // Cancel) then send the null result.
        if (!repliedWithPassphrase) {
            onPassphraseResult.invoke(null)
        }
    }

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

                    /**
                     * Local function called to notify of passphrase submission.
                     */
                    /**
                     * Local function called to notify of passphrase submission.
                     */
                    val onSuccess: (String) -> Unit = { passphrase ->
                        repliedWithPassphrase = true
                        onPassphraseResult.invoke(passphrase)
                        dismiss()
                    }

                    /**
                     * Local function called when user taps on the Cancel button
                     */
                    /**
                     * Local function called when user taps on the Cancel button
                     */
                    val onCancel: () -> Unit = {
                        onPassphraseResult.invoke(null)
                        dismiss()
                    }
                    PassphrasePrompt(
                        constraints = constraints,
                        title = title,
                        content = content,
                        onSuccess = onSuccess,
                        onCancel = onCancel,
                    )
                }
            }
        }
}