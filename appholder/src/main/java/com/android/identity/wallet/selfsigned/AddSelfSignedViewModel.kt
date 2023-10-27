package com.android.identity.wallet.selfsigned

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.wallet.document.DocumentColor
import com.android.identity.wallet.document.DocumentType
import com.android.identity.wallet.document.SecureAreaImplementationState
import com.android.identity.wallet.selfsigned.AddSelfSignedScreenState.AndroidAuthKeyCurveOption
import com.android.identity.wallet.selfsigned.AddSelfSignedScreenState.AndroidAuthKeyCurveState
import com.android.identity.wallet.selfsigned.AddSelfSignedScreenState.AuthTypeState
import com.android.identity.wallet.selfsigned.AddSelfSignedScreenState.BouncyCastleAuthKeyCurveOption
import com.android.identity.wallet.selfsigned.AddSelfSignedScreenState.MdocAuthOptionState
import com.android.identity.wallet.selfsigned.AddSelfSignedScreenState.MdocAuthStateOption
import com.android.identity.wallet.util.getState
import com.android.identity.wallet.util.updateState
import kotlinx.coroutines.flow.StateFlow
import java.lang.Integer.max

class AddSelfSignedViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private var capabilities: AndroidKeystoreSecureArea.Capabilities? = null

    val screenState: StateFlow<AddSelfSignedScreenState> = savedStateHandle.getState(
        AddSelfSignedScreenState()
    )

    fun loadConfiguration(context: Context) {
        capabilities = AndroidKeystoreSecureArea.Capabilities(context)
        savedStateHandle.updateState<AddSelfSignedScreenState> {
            it.copy(
                allowLSKFUnlocking = AuthTypeState(
                    true,
                    capabilities!!.multipleAuthenticationTypesSupported
                ),
                allowBiometricUnlocking = AuthTypeState(
                    true,
                    capabilities!!.multipleAuthenticationTypesSupported
                ),
                useStrongBox = AuthTypeState(false, capabilities!!.strongBoxSupported),
                androidMdocAuthState = MdocAuthOptionState(
                    isEnabled = if (it.useStrongBox.isEnabled) capabilities!!.strongBoxKeyAgreementSupported else capabilities!!.keyAgreementSupported
                ),
                androidAuthKeyCurveState = AndroidAuthKeyCurveState(
                    isEnabled = if (it.useStrongBox.isEnabled) capabilities!!.strongBoxCurve25519Supported else capabilities!!.curve25519Supported
                )
            )
        }
    }

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
        if (seconds < 0) return
        savedStateHandle.updateState<AddSelfSignedScreenState> {
            it.copy(userAuthenticationTimeoutSeconds = seconds)
        }
    }

    fun updateLskfUnlocking(newValue: Boolean) {
        savedStateHandle.updateState<AddSelfSignedScreenState> {
            val allowLskfUnlock = if (it.allowBiometricUnlocking.isEnabled) newValue else true
            it.copy(allowLSKFUnlocking = it.allowLSKFUnlocking.copy(isEnabled = allowLskfUnlock))
        }
    }

    fun updateBiometricUnlocking(newValue: Boolean) {
        savedStateHandle.updateState<AddSelfSignedScreenState> {
            val allowBiometricUnlock = if (it.allowLSKFUnlocking.isEnabled) newValue else true
            it.copy(allowBiometricUnlocking = it.allowBiometricUnlocking.copy(isEnabled = allowBiometricUnlock))
        }
    }

    fun updateStrongBox(newValue: Boolean) {
        savedStateHandle.updateState<AddSelfSignedScreenState> {
            it.copy(
                useStrongBox = it.useStrongBox.copy(isEnabled = newValue),
                androidMdocAuthState = MdocAuthOptionState(
                    isEnabled = if (capabilities != null) {
                        if (newValue)
                            capabilities!!.strongBoxKeyAgreementSupported
                        else
                            capabilities!!.keyAgreementSupported
                    } else {
                        false
                    }
                ),
                androidAuthKeyCurveState = AndroidAuthKeyCurveState(
                    isEnabled = if (capabilities != null) {
                        if (newValue)
                            capabilities!!.strongBoxCurve25519Supported
                        else
                            capabilities!!.curve25519Supported
                    } else {
                        false
                    }
                )
            )
        }
    }

    fun updateMdocAuthOption(newValue: MdocAuthStateOption) {
        savedStateHandle.updateState<AddSelfSignedScreenState> {
            it.copy(
                androidMdocAuthState = it.androidMdocAuthState.copy(mDocAuthentication = newValue),
                androidAuthKeyCurveState = it.androidAuthKeyCurveState.copy(authCurve = AndroidAuthKeyCurveOption.P_256),
                bouncyCastleAuthKeyCurveState = it.bouncyCastleAuthKeyCurveState.copy(authCurve = BouncyCastleAuthKeyCurveOption.P256)
            )
        }
    }

    fun updateAndroidAuthKeyCurve(newValue: AndroidAuthKeyCurveOption) {
        savedStateHandle.updateState<AddSelfSignedScreenState> {
            it.copy(androidAuthKeyCurveState = it.androidAuthKeyCurveState.copy(authCurve = newValue))
        }
    }

    fun updateBouncyCastleAuthKeyCurve(newValue: BouncyCastleAuthKeyCurveOption) {
        savedStateHandle.updateState<AddSelfSignedScreenState> {
            it.copy(bouncyCastleAuthKeyCurveState = it.bouncyCastleAuthKeyCurveState.copy(authCurve = newValue))
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
