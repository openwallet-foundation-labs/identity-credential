package com.android.identity.wallet.selfsigned

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.android.identity.wallet.document.DocumentColor
import com.android.identity.wallet.document.DocumentType
import com.android.identity.wallet.support.CurrentSecureArea
import com.android.identity.wallet.support.SecureAreaSupportState
import com.android.identity.wallet.util.getState
import com.android.identity.wallet.util.updateState
import kotlinx.coroutines.flow.StateFlow
import java.lang.Integer.max

class AddSelfSignedViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    val screenState: StateFlow<AddSelfSignedScreenState> = savedStateHandle.getState(
        AddSelfSignedScreenState()
    )

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

    fun updateKeystoreImplementation(newValue: CurrentSecureArea) {
        savedStateHandle.updateState<AddSelfSignedScreenState> {
            it.copy(currentSecureArea = newValue)
        }
    }

    fun updateSecureAreaSupportState(newValue: SecureAreaSupportState) {
        savedStateHandle.updateState<AddSelfSignedScreenState> {
            it.copy(secureAreaSupportState = newValue)
        }
    }

    fun updateValidityInDays(newValue: Int) {
        val state = savedStateHandle.getState(AddSelfSignedScreenState())
        if (newValue < state.value.minValidityInDays) return
        savedStateHandle.updateState<AddSelfSignedScreenState> {
            it.copy(validityInDays = newValue)
        }
    }

    fun updateMinValidityInDays(newValue: Int) {
        if (newValue <= 0) return
        savedStateHandle.updateState<AddSelfSignedScreenState> {
            val validityDays = max(newValue, it.validityInDays)
            it.copy(minValidityInDays = newValue, validityInDays = validityDays)
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
