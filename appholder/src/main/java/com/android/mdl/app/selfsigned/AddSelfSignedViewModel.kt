package com.android.mdl.app.selfsigned

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.android.mdl.app.document.DocumentColor
import com.android.mdl.app.document.DocumentType
import com.android.mdl.app.document.SecureAreaImplementationState
import com.android.mdl.app.util.getState
import com.android.mdl.app.util.updateState
import kotlinx.coroutines.flow.StateFlow

class AddSelfSignedViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    val screenState: StateFlow<AddSelfSignedScreenState> =
        savedStateHandle.getState(AddSelfSignedScreenState())

    fun updateDocumentType(newValue: DocumentType) {
        savedStateHandle.updateState<AddSelfSignedScreenState> {
            it.copy(documentType = newValue, documentName = documentNameFor(newValue))
        }
    }

    fun updateCardArt(newValue: DocumentColor) {
        savedStateHandle.updateState<AddSelfSignedScreenState> {
            it.copy(cardArt = newValue)
        }
    }

    fun updateDocumentName(newValue: String) {
        savedStateHandle.updateState<AddSelfSignedScreenState> {
            it.copy(documentName = newValue)
        }
    }

    fun updateKeystoreImplementation(newValue: SecureAreaImplementationState) {
        savedStateHandle.updateState<AddSelfSignedScreenState> {
            it.copy(secureAreaImplementationState = newValue)
        }
    }

    fun updateUserAuthentication(newValue: Boolean) {
        savedStateHandle.updateState<AddSelfSignedScreenState> {
            it.copy(userAuthentication = newValue)
        }
    }

    fun updateUserAuthenticationTimeoutSeconds(seconds: Int) {
        savedStateHandle.updateState<AddSelfSignedScreenState> {
            it.copy(userAuthenticationTimeoutSeconds = seconds)
        }
    }

    fun updatePassphrase(newValue: String) {
        savedStateHandle.updateState<AddSelfSignedScreenState> {
            it.copy(passphrase = newValue)
        }
    }

    fun updateNumberOfMso(newValue: Int) {
        if (newValue <= 0) return
        savedStateHandle.updateState<AddSelfSignedScreenState> {
            it.copy(numberOfMso = newValue)
        }
    }

    fun updateMaxUseOfMso(newValue: Int) {
        if (newValue <= 0) return
        savedStateHandle.updateState<AddSelfSignedScreenState> {
            it.copy(maxUseOfMso = newValue)
        }
    }

    private fun documentNameFor(documentType: DocumentType): String {
        return when (documentType) {
            is DocumentType.MDL -> "Driving License"
            is DocumentType.MVR -> "Vehicle Registration"
            is DocumentType.MICOV -> "Vaccination Document"
            is DocumentType.EUPID -> "EU Personal ID"
        }
    }
}
