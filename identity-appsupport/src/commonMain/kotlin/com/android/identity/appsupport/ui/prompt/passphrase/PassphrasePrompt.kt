
package com.android.identity.appsupport.ui.prompt.passphrase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.identity.securearea.PassphraseConstraints
import identitycredential.identity_appsupport.generated.resources.Res
import identitycredential.identity_appsupport.generated.resources.passphrase_prompt_cancel
import org.jetbrains.compose.resources.stringResource

/**
 * Compose View of Passphrase Prompt - it's up to each platform on how to show it.
 */
@Composable
fun PassphrasePrompt(
    constraints: PassphraseConstraints,
    title: String,
    content: String,
    onSuccess: (String) -> Unit,
    onCancel: () -> Unit

) {
    var currPassphrase = ""
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.Top
        ) {
            // redirect all PIN constraints to PassphrasePinScreen
            if (constraints.requireNumerical) {
                PassphrasePinScreen(
                    title = title,
                    content = content,
                    constraints = constraints,
                    onSubmitPin = { pin -> onSuccess(pin) },
                    onCancel = { onCancel() }
                )
            } else { // non-digit passphrase
                Column(
                    modifier = Modifier
                        .padding(bottom = 32.dp)
                ) {
                    // cancel button on top right
                    PassphrasePromptActions(
                        onCancel = { onCancel() }
                    )
                    PassphrasePromptHeader(title = title, content = content)
                    PassphrasePromptInputField(
                        constraints = constraints,
                        onChanged = { passphrase, donePressed ->
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
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            text = content,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
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
            Text(text = stringResource(Res.string.passphrase_prompt_cancel))
        }
    }
}
