@file:OptIn(ExperimentalMaterial3Api::class)

package com.android.identity_credential.wallet.ui.prompt.passphrase

import android.app.Dialog
import android.content.DialogInterface
import android.content.res.Resources
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.android.identity.appsupport.ui.PassphraseEntryField
import com.android.identity.securearea.PassphraseConstraints
import com.android.identity_credential.wallet.R
import com.android.identity_credential.wallet.presentation.UserCanceledPromptException
import com.android.identity_credential.wallet.ui.theme.IdentityCredentialTheme
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
suspend fun showPassphrasePrompt(
    activity: FragmentActivity,
    constraints: PassphraseConstraints,
    title: String,
    content: String,
): String? =
    suspendCancellableCoroutine { continuation ->

        val passphrasePrompt = PassphrasePrompt(
            constraints = constraints,
            title = title,
            content = content,
            onPassphraseResult = { passphrase ->
                continuation.resume(passphrase)
            },
        )
        // show the Dialog Fragment Prompt
        passphrasePrompt.show(activity.supportFragmentManager, "passphrase_prompt")
    }

/**
 * PassphrasePrompt Dialog Fragment class that renders the Passphrase Prompt via Composition for the
 * purposes of running [PassphrasePrompt] synchronously to return the prompt's result as
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
class PassphrasePrompt(
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // follow light/dark theme
        setStyle(STYLE_NORMAL, R.style.PassphrasePromptBottomSheetTheme)
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
                var currPassphrase = ""

                // if fixed length, the IME action button shouldn't do anything because the user
                // needs to provide exactly the required number of characters; show an arrow icon
                // rather than a checkmark icon (since with fixed length, the user should not
                // have the option to "complete" the entry, rather it is completed inherently
                // when the required number of characters has been entered)
                val imeAction = if (constraints.isFixedLength()) ImeAction.Go else ImeAction.Done

                /**
                 * Local function called to notify of passphrase submission.
                 */
                fun onSuccess(passphrase: String) {
                    repliedWithPassphrase = true
                    onPassphraseResult.invoke(passphrase)
                    dismiss()
                }

                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.onBackground),
                    verticalArrangement = Arrangement.Top
                ) {
                    Column(
                        modifier = Modifier
                            .padding(bottom = 32.dp)
                    ) {
                        PassphrasePromptActions(
                            onCancel = {
                                onPassphraseResult.invoke(null)
                                dismiss()
                            }
                        )

                        PassphrasePromptHeader(title = title, content = content)
                        PassphraseEntryField(
                            constraints = constraints,
                            checkWeakPassphrase = false,
                            imeAction = imeAction,
                            onChanged = { passphrase, _, donePressed ->
                                currPassphrase = passphrase
                                if (!constraints.isFixedLength()) {
                                    // notify of the typed passphrase when user taps 'Done' on the keyboard
                                    if (donePressed) {
                                        onSuccess(currPassphrase)
                                    }
                                } else { // when the user enters the maximum numbers of characters, send
                                    if (passphrase.length == constraints.maxLength) {
                                        onSuccess(currPassphrase)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

    @Composable
    private fun PassphrasePromptHeader(title: String, content: String) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = title,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (!isSystemInDarkTheme()) Color.Black else MaterialTheme.colorScheme.primary

            )

            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                text = content,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = if (!isSystemInDarkTheme()) Color.Black else MaterialTheme.colorScheme.secondary
            )
        }
    }

    /**
     * Shows the action buttons of the Passphrase prompt.
     * Material3 Compose Buttons: https://developer.android.com/develop/ui/compose/components/button
     */
    @Composable
    private fun PassphrasePromptActions(
        onCancel: () -> Unit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Spacer(
                modifier = Modifier
                    .width(8.dp)
                    .weight(0.4f)
            )
            // Cancel button
            TextButton(
                modifier = Modifier.weight(0.1f),
                onClick = { onCancel.invoke() },
            ) {
                Text(text = stringResource(id = R.string.passphrase_prompt_cancel))
            }
        }
    }

    /**
     * Return whether a keyboard is detected to be shown up or not from the context of the composable
     * itself [LocalView]
     */
    private fun View.isKeyboardVisible(): Boolean {
        val rect = Rect()
        getWindowVisibleDisplayFrame(rect)
        val screenHeight = rootView.height
        val keypadHeight = screenHeight - rect.bottom
        return keypadHeight > screenHeight * 0.15 // Threshold for keyboard visibility
    }
}