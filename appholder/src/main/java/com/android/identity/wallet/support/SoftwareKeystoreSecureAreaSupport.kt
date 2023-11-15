package com.android.identity.wallet.support

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.android.identity.credential.Credential
import com.android.identity.securearea.SecureArea
import com.android.identity.securearea.SoftwareSecureArea
import com.android.identity.wallet.authconfirmation.AuthConfirmationFragmentDirections
import com.android.identity.wallet.authconfirmation.PassphraseAuthResult
import com.android.identity.wallet.authconfirmation.PassphrasePromptViewModel
import com.android.identity.wallet.composables.AuthenticationKeyCurveSoftware
import com.android.identity.wallet.composables.MdocAuthentication
import com.android.identity.wallet.composables.SoftwareSetupContainer
import com.android.identity.wallet.support.softwarekeystore.SoftwareAuthKeyCurveOption
import kotlinx.coroutines.launch

class SoftwareKeystoreSecureAreaSupport : SecureAreaSupport {

    private val screenState = SoftwareKeystoreSecureAreaSupportState()

    override fun Fragment.unlockKey(
        credential: Credential,
        onKeyUnlocked: (unlockData: SecureArea.KeyUnlockData?) -> Unit,
        onUnlockFailure: (wasCancelled: Boolean) -> Unit
    ) {
        val viewModel: PassphrasePromptViewModel by activityViewModels()
        var didAttemptToUnlock = false

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                viewModel.authorizationState.collect { value ->
                    if (value is PassphraseAuthResult.Success) {
                        val keyUnlockData = SoftwareSecureArea.KeyUnlockData(value.userPassphrase)
                        didAttemptToUnlock = true
                        onKeyUnlocked(keyUnlockData)
                        viewModel.reset()
                    }
                }
            }
        }
        val destination = AuthConfirmationFragmentDirections.openPassphrasePrompt(
            showIncorrectPassword = didAttemptToUnlock
        )
        val runnable = { findNavController().navigate(destination) }
        // The system needs a little time to get back to this screen
        Handler(Looper.getMainLooper()).postDelayed(runnable, 500)
    }

    @Composable
    override fun SecureAreaAuthUi(
        onUiStateUpdated: (newState: SecureAreaSupportState) -> Unit
    ) {
        var compositionState by remember { mutableStateOf(screenState) }
        LaunchedEffect(key1 = compositionState) {
            onUiStateUpdated(compositionState)
        }
        SoftwareSetupContainer(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            passphrase = compositionState.passphrase,
            onPassphraseChanged = {
                compositionState = compositionState.copy(passphrase = it)
            }
        )
        MdocAuthentication(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            state = compositionState.mDocAuthOption,
            onMdocAuthOptionChange = {
                val newValue = compositionState.mDocAuthOption.copy(mDocAuthentication = it)
                compositionState = compositionState.copy(
                    mDocAuthOption = newValue,
                    softwareAuthKeyCurveState = compositionState.softwareAuthKeyCurveState.copy(
                        authCurve = SoftwareAuthKeyCurveOption.P256
                    )
                )
            }
        )
        AuthenticationKeyCurveSoftware(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            state = compositionState.softwareAuthKeyCurveState,
            mDocAuthState = compositionState.mDocAuthOption,
            onSoftwareAuthKeyCurveChanged = {
                val newValue = compositionState.authKeyCurve.copy(authCurve = it)
                compositionState = compositionState.copy(softwareAuthKeyCurveState = newValue)
            }
        )
    }

    override fun getSecureAreaSupportState(): SecureAreaSupportState {
        return screenState
    }
}