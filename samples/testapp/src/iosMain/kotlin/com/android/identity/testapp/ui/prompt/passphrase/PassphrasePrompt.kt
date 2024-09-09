@file:OptIn(ExperimentalMaterial3Api::class)

package com.android.identity.testapp.ui.prompt.passphrase

import androidx.compose.material3.ExperimentalMaterial3Api
import com.android.identity.appsupport.ui.PassphraseEntryField
import com.android.identity.securearea.PassphraseConstraints

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
actual suspend fun showPassphrasePrompt(
    constraints: PassphraseConstraints,
    title: String,
    content: String,
): String? = "WIP"
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
//        passphrasePrompt.show(activity.supportFragmentManager, "passphrase_prompt")
//    }
//
///**
// * PassphrasePrompt Dialog Fragment class that renders the Passphrase Prompt via Composition for the
// * purposes of running [PassphrasePrompt] synchronously to return the prompt's result as
// * a [Boolean] and not worry about callback hell.
// *
// * @param title the top-most text of the Prompt.
// * @param content contains more information about the Prompt.
// * @param constraints the constraints for the passphrase.
// * @param onDismissDialog the callback invoked with a Boolean indicating whether onPassphraseResult() has
// *      already been called which means the dismissal of the dialog is from the completion of the
// *      Prompt rather than be dismissed because the user tapped outside of this dialog fragment.
// * @param onPassphraseResult callback issued from this dialog fragment when the user enters a passphrase
// * @extends [BottomSheetDialogFragment] that can create the Fragment's contents via Composition.
// */
//class PassphrasePrompt(
//    private val title: String,
//    private val content: String,
//    private val constraints: PassphraseConstraints,
//    private val onPassphraseResult: (String?) -> Unit,
//) : BottomSheetDialogFragment() {
//
//    // flag used by the override onDismiss() to know if the dismiss was premature or not.
//    var repliedWithPassphrase = false
//
//    /**
//     * Fully Expanded Bottom Sheet Dialog
//     */
//    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
//        return super.onCreateDialog(savedInstanceState).apply {
//            setOnShowListener { dialog ->
//                val bottomSheet =
//                    (dialog as BottomSheetDialog).findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
//                BottomSheetBehavior.from(bottomSheet!!).apply {
//                    state = BottomSheetBehavior.STATE_EXPANDED
//                    skipCollapsed = true // Prevent collapsing
//                    (dialog as? BottomSheetDialog)?.behavior?.peekHeight =
//                        Resources.getSystem().displayMetrics.heightPixels
//                    dialog.window?.setDimAmount(0f) // Remove background dimming
//                }
//            }
//        }
//    }
//
//    override fun onDismiss(dialog: DialogInterface) {
//        super.onDismiss(dialog)
//        // if dismiss is called before sending the passphrase result (such as user tapping on the
//        // Cancel) then send the null result.
//        if (!repliedWithPassphrase) {
//            onPassphraseResult.invoke(null)
//        }
//    }
//
//    /**
//     * Render the Composable [PassphraseEntryField] and notify when passphrase has been submitted
//     */
//    override fun onCreateView(
//        inflater: LayoutInflater,
//        container: ViewGroup?,
//        savedInstanceState: Bundle?
//    ): View =
//        ComposeView(requireContext()).apply {
//            setContent {
//                IdentityCredentialTheme {
//                    var currPassphrase = ""
//
//                    /**
//                     * Local function called to notify of passphrase submission.
//                     */
//                    /**
//                     * Local function called to notify of passphrase submission.
//                     */
//                    fun onSuccess(passphrase: String) {
//                        repliedWithPassphrase = true
//                        onPassphraseResult.invoke(passphrase)
//                        dismiss()
//                    }
//
//                    /**
//                     * Local function called when user taps on the Cancel button
//                     */
//                    /**
//                     * Local function called when user taps on the Cancel button
//                     */
//                    fun onCancel() {
//                        onPassphraseResult.invoke(null)
//                        dismiss()
//                    }
//
//                    Surface(modifier = Modifier.fillMaxSize()) {
//                        Column(
//                            verticalArrangement = Arrangement.Top
//                        ) {
//                            // redirect all PIN constraints to PassphrasePinScreen
//                            if (constraints.requireNumerical) {
//                                PassphrasePinScreen(
//                                    title = title,
//                                    content = content,
//                                    constraints = constraints,
//                                    onSubmitPin = { pin -> onSuccess(pin) },
//                                    onCancel = { onCancel() }
//                                )
//                            } else { // non-digit passphrase
//                                Column(
//                                    modifier = Modifier
//                                        .padding(bottom = 32.dp)
//                                ) {
//                                    // cancel button on top right
//                                    PassphrasePromptActions(
//                                        onCancel = { onCancel() }
//                                    )
//                                    PassphrasePromptHeader(title = title, content = content)
//                                    PassphrasePromptInputField(
//                                        constraints = constraints,
//                                        onChanged = { passphrase, donePressed ->
//                                            currPassphrase = passphrase
//                                            if (!constraints.isFixedLength()) {
//                                                // notify of the typed passphrase when user taps 'Done' on the keyboard
//                                                if (donePressed) {
//                                                    onSuccess(currPassphrase)
//                                                }
//                                            } else { // when the user enters the maximum numbers of characters, send
//                                                if (passphrase.length == constraints.maxLength) {
//                                                    onSuccess(currPassphrase)
//                                                }
//                                            }
//                                        }
//                                    )
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }
//
//    @Composable
//    private fun PassphrasePromptHeader(title: String, content: String) {
//        Column(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(top = 32.dp, bottom = 16.dp),
//            verticalArrangement = Arrangement.Center
//        ) {
//            Text(
//                modifier = Modifier.fillMaxWidth(),
//                text = title,
//                textAlign = TextAlign.Center,
//                style = MaterialTheme.typography.headlineMedium,
//                fontWeight = FontWeight.Bold,
//                color = MaterialTheme.colorScheme.onSurface
//            )
//
//            Text(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(top = 8.dp),
//                text = content,
//                textAlign = TextAlign.Center,
//                style = MaterialTheme.typography.titleMedium,
//                fontWeight = FontWeight.Bold,
//                color = MaterialTheme.colorScheme.onSurface
//            )
//        }
//    }
//
//    /**
//     * Shows the action buttons of the Passphrase prompt.
//     * Material3 Compose Buttons: https://developer.android.com/develop/ui/compose/components/button
//     */
//    @Composable
//    private fun PassphrasePromptActions(
//        onCancel: () -> Unit,
//    ) {
//        Row(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(top = 16.dp),
//            horizontalArrangement = Arrangement.End,
//        ) {
//            Spacer(
//                modifier = Modifier
//                    .width(8.dp)
//                    .weight(0.4f)
//            )
//            // Cancel button
//            TextButton(
//                modifier = Modifier.weight(0.1f),
//                onClick = { onCancel.invoke() },
//            ) {
//                Text(text = stringResource(Res.string.passphrase_prompt_cancel))
//            }
//        }
//    }
//}