package com.android.identity.wallet.authconfirmation

import kotlin.test.Test
import kotlin.test.assertEquals

class PassphraseValidationTest {

    @Test
    fun defaultValue() {
        val viewModel = PassphrasePromptViewModel()

        assertEquals(viewModel.authorizationState.value, PassphraseAuthResult.Idle)
    }

    @Test
    fun authorizationSuccess() {
        val passphrase = ":irrelevant:"
        val viewModel = PassphrasePromptViewModel()

        viewModel.authorize(userPassphrase = passphrase)

        assertEquals(viewModel.authorizationState.value, PassphraseAuthResult.Success(passphrase))
    }

    @Test
    fun resetting() {
        val viewModel = PassphrasePromptViewModel().apply {
            val passphrase = ":irrelevant:"
            authorize(passphrase)
        }

        viewModel.reset()

        assertEquals(viewModel.authorizationState.value, PassphraseAuthResult.Idle)
    }
}