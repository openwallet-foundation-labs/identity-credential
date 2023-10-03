package com.android.identity.wallet.authconfirmation

import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.securearea.BouncyCastleSecureArea
import com.android.identity.securearea.SecureArea.ALGORITHM_ES256
import com.android.identity.wallet.R
import com.android.identity.wallet.authprompt.UserAuthPromptBuilder
import com.android.identity.wallet.theme.HolderAppTheme
import com.android.identity.wallet.transfer.AddDocumentToResponseResult
import com.android.identity.wallet.util.DocumentData
import com.android.identity.wallet.util.log
import com.android.identity.wallet.viewmodel.TransferDocumentViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class AuthConfirmationFragment : BottomSheetDialogFragment() {

    private val viewModel: TransferDocumentViewModel by activityViewModels()
    private val passphraseViewModel: PassphrasePromptViewModel by activityViewModels()
    private val arguments by navArgs<AuthConfirmationFragmentArgs>()
    private var isSendingInProgress = mutableStateOf(false)
    private var androidKeyUnlockData: AndroidKeystoreSecureArea.KeyUnlockData? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val elementsToSign = viewModel.requestedElements()
        val sheetData = mapToConfirmationSheetData(elementsToSign)
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                HolderAppTheme {
                    ConfirmationSheet(
                        modifier = Modifier.fillMaxWidth(),
                        title = getSubtitle(),
                        isTrustedReader = arguments.readerIsTrusted,
                        isSendingInProgress = isSendingInProgress.value,
                        sheetData = sheetData,
                        onElementToggled = { element -> viewModel.toggleSignedElement(element) },
                        onConfirm = { sendResponse() },
                        onCancel = {
                            dismiss()
                            cancelAuthorization()
                        }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                passphraseViewModel.authorizationState.collect { value ->
                    if (value is PassphraseAuthResult.Success) {
                        onPassphraseProvided(value.userPassphrase)
                        passphraseViewModel.reset()
                    }
                }
            }
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        cancelAuthorization()
    }

    private fun cancelAuthorization() {
        viewModel.onAuthenticationCancelled()
    }

    private fun mapToConfirmationSheetData(
        elementsToSign: List<RequestedDocumentData>
    ): List<ConfirmationSheetData> {
        return elementsToSign.map { documentData ->
            viewModel.addDocumentForSigning(documentData)
            val elements = documentData.requestedElements.map { element ->
                viewModel.toggleSignedElement(element)
                val displayName = stringValueFor(element.namespace, element.value)
                ConfirmationSheetData.DocumentElement(displayName, element)
            }
            ConfirmationSheetData(documentData.userReadableName, elements)
        }
    }

    private fun stringValueFor(namespace: String, element: String): String {
        val requested = if (element == "portrait") portraitFor(namespace) else element
        val identifier = resources.getIdentifier(requested, "string", requireContext().packageName)
        return if (identifier != 0) getString(identifier) else element
    }

    private fun portraitFor(namespace: String): String {
        return if (namespace == DocumentData.EU_PID_NAMESPACE) "facial_portrait" else "portrait"
    }

    private fun sendResponse() {
        isSendingInProgress.value = true
        val result = viewModel.sendResponseForSelection()
        onSendResponseResult(result)
    }

    private fun requestUserAuth(
        allowLskfUnlock: Boolean,
        allowBiometricUnlock: Boolean,
        forceLskf: Boolean = !allowBiometricUnlock
    ) {
        val userAuthRequest = UserAuthPromptBuilder.requestUserAuth(this)
            .withTitle(getString(R.string.bio_auth_title))
            .withSuccessCallback { authenticationSucceeded() }
            .withCancelledCallback {
                if (allowLskfUnlock) {
                    retryForcingPinUse(allowLskfUnlock, allowBiometricUnlock)
                } else {
                    cancelAuthorization()
                }
            }
            .withFailureCallback { authenticationFailed() }
            .setForceLskf(forceLskf)
        if (allowLskfUnlock) {
            userAuthRequest.withNegativeButton(getString(R.string.bio_auth_use_pin))
        } else {
            userAuthRequest.withNegativeButton("Cancel")
        }
        val cryptoObject = androidKeyUnlockData?.getCryptoObjectForSigning(ALGORITHM_ES256)
        userAuthRequest.build().authenticate(cryptoObject)
    }

    private fun getSubtitle(): String {
        val readerCommonName = arguments.readerCommonName
        val readerIsTrusted = arguments.readerIsTrusted
        return if (readerCommonName != "") {
            if (readerIsTrusted) {
                getString(R.string.bio_auth_verifier_trusted_with_name, readerCommonName)
            } else {
                getString(R.string.bio_auth_verifier_untrusted_with_name, readerCommonName)
            }
        } else {
            getString(R.string.bio_auth_verifier_anonymous)
        }
    }

    private fun onPassphraseProvided(passphrase: String) {
        val unlockData = BouncyCastleSecureArea.KeyUnlockData(passphrase)
        val result = viewModel.sendResponseForSelection(unlockData)
        onSendResponseResult(result)
    }

    private fun authenticationSucceeded() {
        try {
            val result = viewModel.sendResponseForSelection(keyUnlockData = androidKeyUnlockData)
            onSendResponseResult(result)
        } catch (e: Exception) {
            val message = "Send response error: ${e.message}"
            log(message, e)
            toast(message)
        }
    }

    private fun onSendResponseResult(result: AddDocumentToResponseResult) {
        when (result) {
            is AddDocumentToResponseResult.UserAuthRequired -> {
                androidKeyUnlockData = AndroidKeystoreSecureArea.KeyUnlockData(result.keyAlias)
                requestUserAuth(
                    result.allowLSKFUnlocking,
                    result.allowBiometricUnlocking
                )
            }

            is AddDocumentToResponseResult.PassphraseRequired -> {
                requestPassphrase(result.attemptedWithIncorrectPassword)
            }

            is AddDocumentToResponseResult.DocumentAdded -> {
                if (result.signingKeyUsageLimitPassed) {
                    toast("Using previously used Auth Key")
                }
                findNavController().navigateUp()
            }
        }
    }

    private fun retryForcingPinUse(allowLsfk: Boolean, allowBiometric: Boolean) {
        val runnable = { requestUserAuth(allowLsfk, allowBiometric, true) }
        // Without this delay, the prompt won't reshow
        Handler(Looper.getMainLooper()).postDelayed(runnable, 100)
    }

    private fun authenticationFailed() {
        viewModel.closeConnection()
    }

    private fun requestPassphrase(attemptedWithIncorrectPassword: Boolean) {
        val destination = AuthConfirmationFragmentDirections.openPassphrasePrompt(
            showIncorrectPassword = attemptedWithIncorrectPassword
        )
        val runnable = { findNavController().navigate(destination) }
        // The system needs a little time to get back to this screen
        Handler(Looper.getMainLooper()).postDelayed(runnable, 500)
    }

    private fun toast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(requireContext(), message, duration).show()
    }
}