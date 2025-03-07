package org.multipaz_credential.wallet.ui.prompt.passphrase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.multipaz.securearea.PassphraseConstraints
import org.multipaz_credential.wallet.R


/**
 * The buttons of KeyPad composable.
 */
private val keyPadButtons = listOf(
    // empty chars under 1 so it's top-aligned like digits 2-9
    KeyPadButton(1, chars = " "),
    KeyPadButton(2, "ABC"),
    KeyPadButton(3, "DEF"),
    KeyPadButton(4, "GHI"),
    KeyPadButton(5, "JKL"),
    KeyPadButton(6, "MNO"),
    KeyPadButton(7, "PQRS"),
    KeyPadButton(8, "TUV"),
    KeyPadButton(9, "WXYZ"),
    KeyPadButton(), //spacer
    KeyPadButton(0)
)

/**
 * The data of a KeyPad button defines the [number]/digit to show on top and the [chars] to show
 * underneath the number/digit. Alternatively a KeyPad button can show if an image resource id
 * [imageResId] is provided - this is paired with [buttonId] so it can be identified when the button
 * is tapped along with the [description] of the image for accessibility.
 */
private data class KeyPadButton(
    // the numerical part of the keypad button (actual input number)
    val number: Int? = null,
    // the mnemonic part of the keypad button (letters under each number)
    val chars: String? = null,
    // show an image instead of a number and chars; provide buttonId below to find it in a list
    val imageResId: Int? = null,
    // used to identify the button when an imageResId is provided (and number and chars are null)
    val buttonId: String? = null,
    // for accessibility usually for image buttons
    val description: String? = null,
) {
    /**
     * An empty KeyPadButton renders a Spacer instead.
     */
    fun isEmpty() = number == null && chars == null && imageResId == null && description == null
}

/**
 * Composes a full screen with PassphraseFieldEntry and custom numerical key pad to use for PINs.
 *
 * With fixed length constraints the user cannot tap on a "Done" button because it is automatically
 * submitted when user enters the required number of digits - so only the Cancel button is
 * composed on the bottom right that changes to "Delete" the moment digits start to be tapped.
 * Tapping on Delete will delete the last tapped digit until there's no digits remaining/showing and
 * the button's text changes back to "Cancel" that cancels the prompt.
 *
 * For non-fixed constraints the user has the option to submit the entered PIN at any point unless
 * the maximum number of digits has been entered that automatically submits the PIN. The
 * Cancel/Delete button is composed on the bottom left and the "Done" button on the bottom right.
 */
@Composable
fun PassphrasePinScreen(
    title: String,
    content: String,
    constraints: PassphraseConstraints,
    onSubmitPin: (String) -> Unit,
    onCancel: () -> Unit
) {
    if (!constraints.requireNumerical) {
        throw IllegalStateException("Passphrase Pin Screen is for numerical passphrases only.")
    }

    val currentPin = remember { mutableStateOf("") }
    val showDelete = remember { mutableStateOf(false) }

    /**
     * Called for updating the text of the Cancel/Delete button to show "Delete" text or not when
     * user taps on a KeyPad button or deletes a digit.
     */
    fun updateShowDelete() {
        showDelete.value = currentPin.value.isNotEmpty()
    }

    /**
     * Tapping on "Delete" button sets the [currentPin] length to be 1 less character.
     */
    fun onBackspace() {
        currentPin.value = currentPin.value.substring(0, currentPin.value.lastIndex)
    }

    Column {
        Spacer(modifier = Modifier.height(50.dp))
        PassphrasePinScreenHeader(title = title, content = content)

        Spacer(modifier = Modifier.height(30.dp))
        // prevent the keyboard from showing up in PassphraseEntryField and set the text to show
        PassphrasePromptInputField(
            constraints = constraints,
            setPin = currentPin.value,
            onChanged = { _, _ -> /* will never get called */ },
        )

        Spacer(modifier = Modifier.height(50.dp))
        KeyPad(
            onButtonTapped = { button ->
                // omit image buttons
                if (button.number != null) {
                    currentPin.value += button.number.toString()
                }
                updateShowDelete()
                // Automatically submit the PIN if the [currentPin] length == constraint max length
                // because  we're setting the value to PassphraseEntryField rather than typing it
                // and the callback [onValueChange] won't be called for every "new digit".
                if (currentPin.value.length == constraints.maxLength) {
                    onSubmitPin(currentPin.value)
                }
            }
        )
        Spacer(modifier = Modifier.height(30.dp))
        // Cancel/Delete and/or Done buttons
        PassphrasePinScreenActions(
            isFixedLength = constraints.isFixedLength(),
            showDelete = showDelete.value,
            onDone = {
                onSubmitPin(currentPin.value)
            },
            onCancelDelete = {
                if (showDelete.value) {
                    onBackspace()
                    updateShowDelete()
                } else {
                    onCancel.invoke()
                }
            }
        )
    }
}

@Composable
private fun PassphrasePinScreenHeader(title: String, content: String) {
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
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            text = content,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}


@Composable
private fun KeyPad(
    onButtonTapped: (KeyPadButton) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for (row in 0..3) { // 4 rows
            Row {
                for (col in 0..2) { // 2 cols
                    var index = row * 3 + col
                    if (index <= keyPadButtons.lastIndex) {
                        val button = keyPadButtons[index]
                        KeyPadButtonView(
                            button = keyPadButtons[index],
                            onClick = {
                                onButtonTapped.invoke(button)
                            }
                        )
                    } else {
                        // instead of showing an empty button, put a spacer of the same size
                        Spacer(modifier = Modifier.width(100.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}


@Composable
private fun KeyPadButtonView(button: KeyPadButton, onClick: (() -> Unit)? = null) {
    if (button.isEmpty()) {
        Spacer(modifier = Modifier.width(100.dp))
    } else {
        Button(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape),
            onClick = { onClick?.invoke() },
        ) {
            if (button.number != null) {
                Column(
                    modifier = Modifier.semantics(mergeDescendants = true, properties = {}),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = button.number.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (button.chars != null) {
                        Text(
                            text = button.chars,
                            modifier = Modifier.padding(top = 5.dp),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
        }
    }
}

/**
 * By default=, show the Cancel button. If param [showDelete] is [true] then the text will switch to
 * delete (after user has tapped on a keypad button). The callback is [onCancelDelete] so it doesn't
 * matter which text is showing. If [isFixedLength] == [false] show the "Done" button on the
 * right side and Cancel/Delete on the left.
 */
@Composable
private fun PassphrasePinScreenActions(
    isFixedLength: Boolean,
    showDelete: Boolean,
    onDone: () -> Unit,
    onCancelDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {

        if (isFixedLength) {
            // add spacer in place of "done" button
            Spacer(
                modifier = Modifier
                    .width(100.dp)
                    .weight(0.4f)
            )
            // add spacer between "done button" and cancel
            Spacer(
                modifier = Modifier
                    .width(100.dp)
                    .weight(0.2f)
            )
        }

        // Cancel or backspace/delete button depending on whether user has tapped on a button
        TextButton(
            modifier = Modifier
                .weight(0.4f)
                .then(
                    if (isFixedLength) {
                        Modifier.padding(end = 16.dp)
                    } else {
                        Modifier.padding(start = 16.dp)
                    }
                ),
            onClick = { onCancelDelete.invoke() },
        ) {
            Text(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                text = stringResource(
                    id = if (showDelete)
                        R.string.passphrase_pin_screen_delete
                    else
                        R.string.passphrase_prompt_cancel
                )
            )
        }
        if (!isFixedLength) {
            // add a spacer between the Cancel button and Done button below
            Spacer(
                modifier = Modifier
                    .width(100.dp)
                    .weight(0.2f)
            )
            TextButton(
                modifier = Modifier
                    .weight(0.4f)
                    .padding(end = 16.dp),
                onClick = { onDone.invoke() },
            ) {
                Text(
                    color = MaterialTheme.colorScheme.onSurface,
                    text = stringResource(id = R.string.passphrase_pin_screen_done),
                    fontSize = 18.sp,
                )
            }
        }
    }
}