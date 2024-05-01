package com.android.identity.wallet.authconfirmation

sealed class PassphraseAuthResult {
    object Idle : PassphraseAuthResult()

    data class Success(val userPassphrase: String) : PassphraseAuthResult()
}
