package com.android.identity.wallet.selfsigned

import androidx.lifecycle.SavedStateHandle
import com.android.identity.wallet.document.DocumentColor
import com.android.identity.wallet.document.DocumentType
import com.android.identity.wallet.document.SecureAreaImplementationState
import com.android.identity.wallet.selfsigned.AddSelfSignedScreenState.AndroidAuthKeyCurveState
import com.android.identity.wallet.selfsigned.AddSelfSignedScreenState.AuthTypeState
import com.android.identity.wallet.selfsigned.AddSelfSignedScreenState.MdocAuthOptionState
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SelfSignedScreenStateTest {

    private val savedStateHandle = SavedStateHandle()

    @Test
    fun defaultScreenState() {
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        assertThat(viewModel.screenState.value)
            .isEqualTo(AddSelfSignedScreenState())
    }

    @Test
    fun updateDocumentType() {
        val personalId = DocumentType.EUPID
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateDocumentType(personalId)

        assertThat(viewModel.screenState.value).isEqualTo(
            AddSelfSignedScreenState(documentType = personalId, documentName = "EU Personal ID")
        )
    }

    @Test
    fun updateDocumentName() {
        val newName = ":irrelevant:"
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateDocumentName(newName)

        assertThat(viewModel.screenState.value)
            .isEqualTo(AddSelfSignedScreenState(documentName = newName))
    }

    @Test
    fun updateDocumentTypeAfterNameUpdate() {
        val registration = DocumentType.MVR
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateDocumentName(":irrelevant:")
        viewModel.updateDocumentType(registration)

        assertThat(viewModel.screenState.value).isEqualTo(
            AddSelfSignedScreenState(
                documentType = registration,
                documentName = "Vehicle Registration"
            )
        )
    }

    @Test
    fun updateCardArt() {
        val blue = DocumentColor.Blue
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateCardArt(blue)

        assertThat(viewModel.screenState.value)
            .isEqualTo(AddSelfSignedScreenState(cardArt = blue))
    }

    @Test
    fun updateKeystoreImplementation() {
        val bouncyCastle = SecureAreaImplementationState.BouncyCastle
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateKeystoreImplementation(bouncyCastle)

        assertThat(viewModel.screenState.value).isEqualTo(
            AddSelfSignedScreenState(secureAreaImplementationState = bouncyCastle)
        )
    }

    @Test
    fun updateUserAuthentication() {
        val authenticationOn = true
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateUserAuthentication(authenticationOn)

        assertThat(viewModel.screenState.value)
            .isEqualTo(AddSelfSignedScreenState(userAuthentication = authenticationOn))
    }

    @Test
    fun updateUserAuthenticationTimeoutSeconds() {
        val newValue = 12
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateUserAuthenticationTimeoutSeconds(newValue)

        assertThat(viewModel.screenState.value)
            .isEqualTo(AddSelfSignedScreenState(userAuthenticationTimeoutSeconds = newValue))
    }

    @Test
    fun updateUserAuthenticationTimeoutSecondsInvalidValue() {
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateUserAuthenticationTimeoutSeconds(1)
        viewModel.updateUserAuthenticationTimeoutSeconds(0)
        viewModel.updateUserAuthenticationTimeoutSeconds(-1)

        assertThat(viewModel.screenState.value)
            .isEqualTo(AddSelfSignedScreenState(userAuthenticationTimeoutSeconds = 0))
    }

    @Test
    fun updateAllowedLskfUnlocking() {
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateLskfUnlocking(false)

        assertThat(viewModel.screenState.value.allowLSKFUnlocking)
            .isEqualTo(AuthTypeState(isEnabled = false, canBeModified = false))
    }

    @Test
    fun updateAllowedLskfUnlockingWhenBiometricIsOff() {
        val viewModel = AddSelfSignedViewModel(savedStateHandle)
        viewModel.updateBiometricUnlocking(false)

        viewModel.updateLskfUnlocking(false)

        assertThat(viewModel.screenState.value.allowLSKFUnlocking)
            .isEqualTo(AuthTypeState(isEnabled = true, canBeModified = false))
    }

    @Test
    fun updateAllowedBiometricUnlocking() {
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateBiometricUnlocking(false)

        assertThat(viewModel.screenState.value.allowBiometricUnlocking)
            .isEqualTo(AuthTypeState(isEnabled = false, canBeModified = false))
    }

    @Test
    fun updateAllowedBiometricUnlockingWhenLskfIsOff() {
        val viewModel = AddSelfSignedViewModel(savedStateHandle)
        viewModel.updateLskfUnlocking(false)

        viewModel.updateBiometricUnlocking(false)

        assertThat(viewModel.screenState.value.allowBiometricUnlocking)
            .isEqualTo(AuthTypeState(isEnabled = true, canBeModified = false))
    }

    @Test
    fun updateStrongBox() {
        val enabled = true
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateStrongBox(enabled)

        assertThat(viewModel.screenState.value.useStrongBox)
            .isEqualTo(AuthTypeState(isEnabled = enabled, canBeModified = false))
    }

    @Test
    fun updateMdocAuthOption() {
        val authOption = AddSelfSignedScreenState.MdocAuthStateOption.MAC
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateMdocAuthOption(authOption)

        assertThat(viewModel.screenState.value.androidMdocAuthState)
            .isEqualTo(MdocAuthOptionState(isEnabled = true, mDocAuthentication = authOption))
    }

    @Test
    fun updateAndroidAuthKeyCurve() {
        val x25519 = AddSelfSignedScreenState.AndroidAuthKeyCurveOption.X25519
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateAndroidAuthKeyCurve(x25519)

        assertThat(viewModel.screenState.value.androidAuthKeyCurveState)
            .isEqualTo(AndroidAuthKeyCurveState(isEnabled = true, authCurve = x25519))
    }

    @Test
    fun updateValidityInDays() {
        val newValue = 15
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateValidityInDays(newValue)

        assertThat(viewModel.screenState.value.validityInDays)
            .isEqualTo(newValue)
    }

    @Test
    fun updateValidityInDaysBelowMinValidityDays() {
        val defaultMinValidity = 10
        val belowMinValidity = 9
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateValidityInDays(defaultMinValidity)
        viewModel.updateValidityInDays(belowMinValidity)

        assertThat(viewModel.screenState.value.validityInDays)
            .isEqualTo(defaultMinValidity)
    }

    @Test
    fun updateMinValidityInDays() {
        val newMinValidity = 15
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateMinValidityInDays(newMinValidity)

        assertThat(viewModel.screenState.value.minValidityInDays)
            .isEqualTo(newMinValidity)
    }

    @Test
    fun updateMinValidityInDaysAboveValidityInDays() {
        val defaultValidityInDays = 30
        val minValidityInDays = defaultValidityInDays + 5
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateMinValidityInDays(minValidityInDays)

        assertThat(viewModel.screenState.value.validityInDays)
            .isEqualTo(minValidityInDays)
    }

    @Test
    fun updateBouncyCastlePassphrase() {
        val newPassphrase = ":irrelevant:"
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updatePassphrase(newPassphrase)

        assertThat(viewModel.screenState.value)
            .isEqualTo(AddSelfSignedScreenState(passphrase = newPassphrase))
    }

    @Test
    fun updateNumberOfMso() {
        val msoCount = 2
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateNumberOfMso(msoCount)

        assertThat(viewModel.screenState.value)
            .isEqualTo(AddSelfSignedScreenState(numberOfMso = msoCount))
    }

    @Test
    fun updateNumberOfMsoInvalidValue() {
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateNumberOfMso(1)
        viewModel.updateNumberOfMso(0)
        viewModel.updateNumberOfMso(-1)

        assertThat(viewModel.screenState.value)
            .isEqualTo(AddSelfSignedScreenState(numberOfMso = 1))
    }

    @Test
    fun updateMaxUseOfMso() {
        val maxMsoUsages = 3
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateMaxUseOfMso(maxMsoUsages)

        assertThat(viewModel.screenState.value)
            .isEqualTo(AddSelfSignedScreenState(maxUseOfMso = maxMsoUsages))
    }

    @Test
    fun updateMaxUseOfMsoInvalidValue() {
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateMaxUseOfMso(1)
        viewModel.updateMaxUseOfMso(0)
        viewModel.updateMaxUseOfMso(-1)

        assertThat(viewModel.screenState.value)
            .isEqualTo(AddSelfSignedScreenState(maxUseOfMso = 1))
    }
}