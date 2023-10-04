package com.android.mdl.app.selfsigned

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.android.mdl.app.theme.HolderAppTheme

class AddSelfSignedFragment : Fragment() {

    private val viewModel: AddSelfSignedViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HolderAppTheme {
                    AddSelfSignedDocumentScreen(
                        viewModel = viewModel,
                        onNext = { onNext() }
                    )
                }
            }
        }
    }

    private fun onNext() {
        val state = viewModel.screenState.value
        val provisionInfo = ProvisionInfo(
            docType = state.documentType.value,
            docName = state.documentName,
            docColor = state.cardArt.value,
            secureAreaImplementationStateType = state.secureAreaImplementationState,
            userAuthentication = state.userAuthentication,
            userAuthenticationTimeoutSeconds = state.userAuthenticationTimeoutSeconds,
            allowLskfUnlocking = state.allowLSKFUnlocking.isEnabled,
            allowBiometricUnlocking = state.allowBiometricUnlocking.isEnabled,
            useStrongBox = state.useStrongBox.isEnabled,
            mDocAuthenticationOption = state.androidMdocAuthState.mDocAuthentication,
            authKeyCurve = state.ecCurve,
            validityInDays = state.validityInDays,
            minValidityInDays = state.minValidityInDays,
            passphrase = state.passphrase.ifBlank { null },
            numberMso = state.numberOfMso,
            maxUseMso = state.maxUseOfMso
        )
        val destination = AddSelfSignedFragmentDirections
            .actionAddSelfSignedToSelfSignedDetails(provisionInfo)
        findNavController().navigate(destination)
    }
}