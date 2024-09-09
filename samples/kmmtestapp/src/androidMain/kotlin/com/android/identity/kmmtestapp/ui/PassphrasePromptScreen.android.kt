package com.android.identity.kmmtestapp.ui

import androidx.fragment.app.FragmentActivity
import com.android.identity.appsupport.ui.PassphraseEntryField
import com.android.identity.securearea.PassphraseConstraints
import com.android.identity.kmmtestapp.MainActivity
import com.android.identity.kmmtestapp.ui.prompt.passphrase.PassphrasePromptDialogFragment
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
 * @param title the top-most text of the Prompt.
 * @param content contains more information about the Prompt.
 * @return the typed passphrase or null if user canceled the prompt.
 */
actual suspend fun showPassphrasePrompt(
    constraints: PassphraseConstraints,
    title: String,
    content: String,
): String? =
    suspendCancellableCoroutine { continuation ->
        val passphrasePrompt = PassphrasePromptDialogFragment(
            constraints = constraints,
            title = title,
            content = content,
            onPassphraseResult = { passphrase ->
                continuation.resume(passphrase)
            },
        )
        // show the Dialog Fragment Prompt
        passphrasePrompt.show(MainActivity.instance.supportFragmentManager, "passphrase_prompt")
    }
