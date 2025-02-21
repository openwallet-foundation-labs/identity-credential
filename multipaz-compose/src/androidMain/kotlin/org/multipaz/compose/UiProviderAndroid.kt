package org.multipaz.compose

import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.android.identity.securearea.UserAuthenticationType
import com.android.identity.nfc.NfcTagReaderModalBottomSheet
import com.android.identity.ui.ScanNfcTagDialogIcon
import com.android.identity.ui.UiModelAndroid
import com.android.identity.ui.UiViewAndroid
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.multipaz.compose.R
import kotlin.coroutines.resume

private data class ScanNfcTagDialogData(
    val message: StateFlow<String>,
    val icon: StateFlow<ScanNfcTagDialogIcon>,
    val continuation: CancellableContinuation<Boolean>
)

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
actual fun UiProvider(lifecycleOwner: LifecycleOwner) {
    UiProviderCommon(lifecycleOwner)

    val showScanNfcTagDialog = remember { mutableStateOf<ScanNfcTagDialogData??>(null) }

    val provider = object : UiViewAndroid {
        override suspend fun showBiometricPrompt(
            cryptoObject: BiometricPrompt.CryptoObject?,
            title: String,
            subtitle: String,
            userAuthenticationTypes: Set<UserAuthenticationType>,
            requireConfirmation: Boolean
        ): Boolean {
            return org.multipaz.compose.biometrics.showBiometricPrompt(
                cryptoObject = cryptoObject,
                title = title,
                subtitle = subtitle,
                userAuthenticationTypes = userAuthenticationTypes,
                requireConfirmation = requireConfirmation
            )
        }

        override suspend fun showScanNfcTagDialog(
            message: StateFlow<String>,
            icon: StateFlow<ScanNfcTagDialogIcon>
        ) {
            suspendCancellableCoroutine { continuation ->
                showScanNfcTagDialog.value = ScanNfcTagDialogData(
                    message = message,
                    icon = icon,
                    continuation = continuation
                )
                continuation.invokeOnCancellation {
                    showScanNfcTagDialog.value = null
                }
            }
            showScanNfcTagDialog.value = null
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                UiModelAndroid.registerView(provider)
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                UiModelAndroid.unregisterView(provider)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showScanNfcTagDialog.value != null) {
        val message = showScanNfcTagDialog.value!!.message.collectAsState().value
        val iconId = when (showScanNfcTagDialog.value!!.icon.collectAsState().value) {
            ScanNfcTagDialogIcon.READY_TO_SCAN -> R.drawable.nfc_tag_reader_icon_scan
            ScanNfcTagDialogIcon.SUCCESS -> R.drawable.nfc_tag_reader_icon_success
            ScanNfcTagDialogIcon.ERROR -> R.drawable.nfc_tag_reader_icon_error
        }
        NfcTagReaderModalBottomSheet(
            dialogMessage = message,
            dialogIconPainter = painterResource(iconId),
            onDismissed = { showScanNfcTagDialog.value!!.continuation.resume(true)  }
        )
    }
}
