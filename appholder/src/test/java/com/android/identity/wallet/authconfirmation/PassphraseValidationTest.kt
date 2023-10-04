package com.android.identity.wallet.authconfirmation

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PassphraseValidationTest {

    @Test
    fun defaultValue() {
        val viewModel = PassphrasePromptViewModel()

        assertThat(viewModel.authorizationState.value)
            .isEqualTo(PassphraseAuthResult.Idle)
    }

    @Test
    fun authorizationSuccess() {
        val passphrase = ":irrelevant:"
        val viewModel = PassphrasePromptViewModel()

        viewModel.authorize(userPassphrase = passphrase)

        assertThat(viewModel.authorizationState.value)
            .isEqualTo(PassphraseAuthResult.Success(passphrase))
    }

    @Test
    fun resetting() {
        val viewModel = PassphrasePromptViewModel().apply {
            val passphrase = ":irrelevant:"
            authorize(passphrase)
        }

        viewModel.reset()

        assertThat(viewModel.authorizationState.value)
            .isEqualTo(PassphraseAuthResult.Idle)
    }
}