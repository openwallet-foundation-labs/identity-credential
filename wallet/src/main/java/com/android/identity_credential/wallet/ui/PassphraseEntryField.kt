package com.android.identity_credential.wallet.ui

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
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.identity.securearea.PassphraseConstraints
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A composable for entering a passphrase or PIN.
 *
 * @param constraints the constraints for the passphrase.
 * @param checkWeakPassphrase if true, checks and disallows for weak passphrase/PINs and also
 *   shows a hint if this is the case.
 * @param onChanged called when the user is entering text or pressing the "Done" IME action
 */
@Composable
fun PassphraseEntryField(
    constraints: PassphraseConstraints,
    checkWeakPassphrase: Boolean,
    onChanged: (passphrase: String, meetsRequirements: Boolean, donePressed: Boolean) -> Unit,
) {
    var inputText by remember { mutableStateOf("") }
    var meetsRequirements by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf("") }
    var obfuscateAll by remember { mutableStateOf(false) }
    val focusRequester = FocusRequester()
    val scope = rememberCoroutineScope()

    // Put boxes around the entered chars for fixed length and six or fewer characters
    var decorationBox: @Composable (@Composable () -> Unit) -> Unit = @Composable { innerTextField -> innerTextField() }
    val isFixedLength = (constraints.minLength == constraints.maxLength)
    if (isFixedLength && constraints.minLength <= 6) {
        decorationBox = {
            Row(
                horizontalArrangement = Arrangement.Center,
            ) {
                // Obfuscate all but the last characters ... note `visualTransformation` isn't used.
                repeat(constraints.maxLength) { digitIndex ->
                    val digit =
                        if (digitIndex == inputText.length - 1) {
                            if (obfuscateAll) {
                                "\u2022" // U+2022 Bullet
                            } else {
                                inputText[digitIndex].toString()
                            }
                        } else if (digitIndex < inputText.length) {
                            "\u2022" // U+2022 Bullet
                        } else {
                            ""
                        }
                    Text(
                        text = digit,
                        modifier =
                            Modifier
                                .width(48.dp)
                                .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                                .padding(2.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.headlineLarge,
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
            modifier =
                Modifier
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
                calcHintAndMeetsRequirements(inputText, constraints).let {
                    hint = it.first
                    meetsRequirements = it.second
                }

                obfuscateAll = false
                scope.launch {
                    delay(750)
                    obfuscateAll = true
                    val value = inputText
                    inputText = ""
                    inputText = value
                }

                onChanged(inputText, meetsRequirements, false)
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineMedium,
            decorationBox = decorationBox,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = if (constraints.requireNumerical) KeyboardType.NumberPassword else KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions =
                KeyboardActions(
                    onDone = {
                        onChanged(inputText, meetsRequirements, true)
                    },
                ),
            visualTransformation = { text ->
                val mask = '\u2022'
                val result =
                    if (text.isNotEmpty()) {
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
                    OffsetMapping.Identity,
                )
            },
        )
    }

    if (!isFixedLength) {
        Divider(
            color = Color.Blue,
            thickness = 2.dp,
            modifier = Modifier.padding(start = 32.dp, end = 32.dp),
        )
    }

    if (checkWeakPassphrase) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }

    // Bring up keyboard when entering screen
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()

        calcHintAndMeetsRequirements(inputText, constraints).let {
            hint = it.first
            meetsRequirements = it.second
        }
    }
}

private fun calcHintAndMeetsRequirements(
    passphrase: String,
    constraints: PassphraseConstraints,
): Pair<String, Boolean> {
    // For a fixed-length passphrase, never give any hints until user has typed it in.
    val isFixedLength = (constraints.minLength == constraints.maxLength)
    if (isFixedLength) {
        if (passphrase.length < constraints.minLength) {
            return Pair("", false)
        }
    }

    if (passphrase.length < constraints.minLength) {
        return Pair(
            if (constraints.requireNumerical) {
                "PIN must be at least ${constraints.minLength} digits"
            } else {
                "Passphrase must be at least ${constraints.minLength} characters"
            },
            false,
        )
    }

    if (isWeakPassphrase(passphrase)) {
        return Pair(
            if (constraints.requireNumerical) {
                "PIN is weak, please choose another"
            } else {
                "Passphrase is weak, please choose another"
            },
            false,
        )
    }

    return Pair("", true)
}

private fun isWeakPassphrase(passphrase: String): Boolean {
    if (passphrase.isEmpty()) {
        return false
    }

    // Check all characters being the same
    if (passphrase.all { it.equals(passphrase.first()) }) {
        return true
    }

    // Check consecutive characters (e.g. 1234 or abcd)
    var nextChar = passphrase.first().inc()
    for (n in IntRange(1, passphrase.length - 1)) {
        if (passphrase[n] != nextChar) {
            return false
        }
        nextChar = nextChar.inc()
    }
    return true
}
