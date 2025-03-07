package org.multipaz.compose.passphrase

import androidx.compose.foundation.border
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
import androidx.compose.runtime.collectAsState
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// U+2022 Bullet, see https://en.wikipedia.org/wiki/Bullet_(typography)
//
private const val BULLET = "\u2022"

@Composable
internal fun PassphrasePromptInputField(
    constraints: PassphraseConstraints,
    showKeyboard: StateFlow<Boolean>,
    onChanged: suspend (passphrase: String, donePressed: Boolean) -> Boolean,
) {
    val coroutineScope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }

    val imeAction = if (constraints.isFixedLength()) {
        ImeAction.None
    } else {
        if (inputText.length >= constraints.minLength) {
            ImeAction.Done
        } else {
            ImeAction.None
        }
    }

    val focusRequester = remember { FocusRequester() }

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
                                BULLET
                            } else {
                                inputText[digitIndex].toString()
                            }
                        } else if (digitIndex < inputText.length) {
                            BULLET
                        } else {
                            ""
                        }
                    Text(
                        text = digit,
                        modifier = Modifier
                            .width(40.dp)
                            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                            .padding(2.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            color = MaterialTheme.colorScheme.primary,
                            // make the BULLET be a bit bolder/bigger
                            fontWeight = if (digit == BULLET) {
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
            onValueChange = { newText ->
                if (newText.length > constraints.maxLength) {
                    return@BasicTextField
                }
                if (constraints.requireNumerical) {
                    if (!(newText.all { it.isDigit() })) {
                        return@BasicTextField
                    }
                }
                inputText = newText

                obfuscateAll = false
                scope.launch {
                    delay(750)
                    obfuscateAll = true
                    val value = inputText
                    inputText = ""
                    inputText = value
                }

                coroutineScope.launch {
                    if (onChanged(inputText, false)) {
                        inputText = ""
                    }
                }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.primary),
            decorationBox = decorationBox,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (constraints.requireNumerical) {
                    KeyboardType.NumberPassword
                } else {
                    KeyboardType.Password
                },
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (inputText.length >= constraints.minLength) {
                        coroutineScope.launch {
                            if (onChanged(inputText, true)) {
                                inputText = ""
                            }
                        }
                    }
                }
            ),
            visualTransformation = { text ->
                val result = if (text.isNotEmpty()) {
                    if (obfuscateAll) {
                        BULLET.repeat(text.text.length)
                    } else {
                        BULLET.repeat(text.text.length - 1) + text.last()
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

    if (showKeyboard.collectAsState().value == true) {
        focusRequester.requestFocus()
    }
}