package org.multipaz.compose.prompt

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.android.identity.context.getActivity
import com.android.identity.prompt.BiometricPromptState
import com.android.identity.prompt.SinglePromptModel

/**
 * Displays biometric prompt dialog in Composable UI environment.
 */
@Composable
internal fun BiometricPromptDialog(model: SinglePromptModel<BiometricPromptState, Boolean>) {
    val activity = LocalContext.current.getActivity() as FragmentActivity
    val dialogState = model.dialogState.collectAsState(SinglePromptModel.NoDialogState())
    val dialogStateValue = dialogState.value
    LaunchedEffect(dialogStateValue) {
        when (dialogStateValue) {
            is SinglePromptModel.DialogShownState -> {
                val dialogParameters = dialogStateValue.parameters
                try {
                    val result = org.multipaz.compose.biometrics.showBiometricPrompt(
                        activity = activity,
                        cryptoObject = dialogParameters.cryptoObject,
                        title = dialogParameters.title,
                        subtitle = dialogParameters.subtitle,
                        userAuthenticationTypes = dialogParameters.userAuthenticationTypes,
                        requireConfirmation = dialogParameters.requireConfirmation
                    )
                    dialogStateValue.resultChannel.send(result)
                } catch (err: Throwable) {
                    dialogStateValue.resultChannel.send(false)
                }
            }
            else -> {}
        }
    }
}