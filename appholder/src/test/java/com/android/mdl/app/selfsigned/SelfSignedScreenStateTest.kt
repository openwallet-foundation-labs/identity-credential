package com.android.mdl.app.selfsigned

import androidx.lifecycle.SavedStateHandle
import com.android.mdl.app.document.DocumentColor
import com.android.mdl.app.document.DocumentType
import com.android.mdl.app.document.SecureAreaImplementationState
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