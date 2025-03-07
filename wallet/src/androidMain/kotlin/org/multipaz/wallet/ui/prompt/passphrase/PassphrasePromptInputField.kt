package org.multipaz_credential.wallet.ui.prompt.passphrase

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.multipaz.securearea.PassphraseConstraints
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


/**
 * Passphrase Prompt Input Field is used for submitting a passphrase or PIN in a Passphrase Prompt.
 * This differs from PassphraseEntryField since it is not being used for creating a passphrase or
 * PIN but rather only submitting one for verification.
 *
 * PassphrasePinScreen composes a keypad for entering a PIN and thus this Input Field should not
 * request keyboard focus and dismiss any attempts to bring one when user taps on the Input Field.
 *
 * @param constraints the constraints for the passphrase that define length and whether it's
 *      alphanumeric or numerical only.
 * @param setPin set the text of the Input Field to be a numerical value. If provided a non-null
 *      value (such as "" at the start of the Pin Screen) then disable keyboard focus and all tap
 *      attempts on the Input Field to bring up the keyboard.
 * @param onChanged called when the user is tapping on a native keyboard to submit an alphanumerical
 *      passphrase only, including tapping on the "Done" IME action. Will never get called when
 *      [setPin] has been specified.
 */
@Composable
fun PassphrasePromptInputField(
    constraints: PassphraseConstraints,
    setPin: String? = null,
    onChanged: (passphrase: String, donePressed: Boolean) -> Unit,
) {
    // whether or not to remove focus from Input Field - only when showing PIN screen.
    val disableKeyboard = setPin != null

    // if fixed length, the IME action button shouldn't do anything because the user
    // needs to provide exactly the required number of characters; show an arrow icon
    // rather than a checkmark icon (since with fixed length, the user should not
    // have the option to "complete" the entry, rather it is completed inherently
    // when the required number of characters has been entered)
    val imeAction =
        if (constraints.isFixedLength()) ImeAction.Go else ImeAction.Done

    val focusRequester = remember { FocusRequester() }
    var inputText by remember { mutableStateOf("") }
    if (disableKeyboard){
        inputText = setPin!!
    }

    var obfuscateAll by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Put boxes around the entered chars for fixed length and six or fewer characters
    var decorationBox: @Composable (@Composable () -> Unit) -> Unit =
        @Composable { innerTextField -> innerTextField() }
    if (constraints.isFixedLength() && constraints.minLength <= 6) {
        decorationBox = {
            Row(
                horizontalArrangement = Arrangement.Center,
            ) {
                // Obfuscate all but the last characters ... note `visualTransformation` isn't used.
                repeat(constraints.maxLength) { digitIndex ->
                    val digit =
                        if (digitIndex == inputText.length - 1) {
                            if (obfuscateAll) {
                                "\u2022"  // U+2022 Bullet
                            } else {
                                inputText[digitIndex].toString()
                            }
                        } else if (digitIndex < inputText.length) {
                            "\u2022"  // U+2022 Bullet
                        } else {
                            ""
                        }
                    Text(
                        text = digit,
                        modifier = Modifier
                            .width(40.dp)
                            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                            .padding(2.dp)
                            .then(
                                // if keyboard is flagged as disabled
                                if (disableKeyboard) {
                                    // intercept the tap on the text field
                                    Modifier.clickable { /* Do nothing  */ }
                                } else {
                                    Modifier
                                }
                            ),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            color = MaterialTheme.colorScheme.primary,
                            // make the "dot" \u2022 be a bit bolder/bigger
                            fontWeight = if (digit == "\u2022") {
                                FontWeight.Black
                            } else {
                                MaterialTheme.typography.headlineLarge.fontWeight
                            }
                        ),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        BasicTextField(
            value = inputText,
            modifier = Modifier
                .padding(8.dp)
                .focusRequester(focusRequester),
            onValueChange = {
                if (it.length > constraints.maxLength) {
                    return@BasicTextField
                }
                if (constraints.requireNumerical) {
                    if (!(it.all { it.isDigit() })) {
                        return@BasicTextField
                    }
                }

                inputText = it

                obfuscateAll = false
                scope.launch {
                    delay(750)
                    obfuscateAll = true
                    val value = inputText
                    inputText = ""
                    inputText = value
                }

                onChanged(inputText, false)
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.primary),
            decorationBox = decorationBox,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (constraints.requireNumerical) KeyboardType.NumberPassword else KeyboardType.Password,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    onChanged(inputText, true)
                }
            ),
            visualTransformation = { text ->
                val mask = '\u2022'
                val result = if (text.isNotEmpty()) {
                    if (obfuscateAll) {
                        mask.toString().repeat(text.text.length)
                    } else {
                        mask.toString().repeat(text.text.length - 1) + text.last()
                    }
                } else {
                    ""
                }
                TransformedText(
                    AnnotatedString(result),
                    OffsetMapping.Identity
                )
            }
        )
    }

    if (!constraints.isFixedLength()) {
        HorizontalDivider(
            color = Color.Blue,
            thickness = 2.dp,
            modifier = Modifier.padding(start = 32.dp, end = 32.dp)
        )
    }

    LaunchedEffect(Unit) {
        // Bring up keyboard when entering screen if keyboard has not been disabled
        if (!disableKeyboard) {
            focusRequester.requestFocus()
        }
    }
}