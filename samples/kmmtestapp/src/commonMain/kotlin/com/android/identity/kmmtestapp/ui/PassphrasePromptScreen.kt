package com.android.identity.kmmtestapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.identity.appsupport.ui.prompt.passphrase.PassphrasePrompt
import com.android.identity.kmmtestapp.Platform
import com.android.identity.kmmtestapp.platform
import com.android.identity.securearea.PassphraseConstraints
import kotlinx.coroutines.launch

expect suspend fun showPassphrasePrompt(
    constraints: PassphraseConstraints,
    title: String,
    content: String
): String?


@Composable
fun PassphrasePromptScreen(
    showToast: (message: String) -> Unit
) {
    /**
     * Defines / holds the data for a menu item on the screen.
     */
    data class ScreenMenu(
        val label: String,
        val constraints: PassphraseConstraints
    ) {
        override fun equals(other: Any?): Boolean {
            if (other !is ScreenMenu) return false
            if (other.label == this.label) return true
            return false
        }

        override fun hashCode(): Int {
            return label.hashCode()
        }
    }

    // if set to this show main menu of Passphrase Prompt Screen, choose any placeholder constraint
    val mainMenu = ScreenMenu(label = "INIT", constraints = PassphraseConstraints.PIN_FOUR_DIGITS)

    val menuItems = listOf(
        ScreenMenu(
            label = "4-Digit PIN",
            constraints = PassphraseConstraints.PIN_FOUR_DIGITS
        ),
        ScreenMenu(
            label = "6-Digit PIN",
            constraints = PassphraseConstraints.PIN_SIX_DIGITS
        ),
        ScreenMenu(
            label = "6-Digit PIN or longer",
            constraints = PassphraseConstraints.PIN_SIX_DIGITS_OR_LONGER
        ),
        ScreenMenu(
            label = "Passphrase 6 Characters",
            constraints = PassphraseConstraints.PASSPHRASE_SIX_CHARS
        ),
        ScreenMenu(
            label = "Passphrase 6 Characters or longer",
            constraints = PassphraseConstraints.PASSPHRASE_SIX_CHARS_OR_LONGER
        ),
        ScreenMenu(
            label = "No Constraints",
            constraints = PassphraseConstraints.NONE
        )
    )

    val coroutineScope = rememberCoroutineScope()
    val showEntry = remember { mutableStateOf(mainMenu) }
    if (showEntry.value == mainMenu) {
        LazyColumn(
            modifier = Modifier.padding(8.dp)
        ) {
            items(menuItems) { menuItem ->
                TextButton(
                    onClick = {
                        showEntry.value = menuItem
                    },
                    content = { Text(menuItem.label) }
                )

            }
        }
    } else {
        if (platform == Platform.IOS) {
            Dialog(
                onDismissRequest = {
                    showEntry.value = mainMenu
                },
                properties = DialogProperties(
                    dismissOnClickOutside = false,
                    dismissOnBackPress = true,
                    usePlatformDefaultWidth = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    PassphrasePrompt(
                        constraints = showEntry.value.constraints,
                        title = showEntry.value.label,
                        content = "KMM port of Passphrase Prompt on iOS",
                        onSuccess = { passphrase ->
                            showToast("Provided Passphrase: $passphrase")
                            showEntry.value = mainMenu
                        },
                        onCancel = {
                            showEntry.value = mainMenu
                        }
                    )
                }
            }
        } else { // platform == android
            SideEffect {
                coroutineScope.launch {
                    val enteredPassphrase = showPassphrasePrompt(
                        constraints = showEntry.value.constraints,
                        title = showEntry.value.label,
                        content = "KMM port of Passphrase Prompt on Android",
                    )
                    showToast("Provided PIN/Passphrase: $enteredPassphrase")
                    showEntry.value = mainMenu
                }
            }
        }
    }
}