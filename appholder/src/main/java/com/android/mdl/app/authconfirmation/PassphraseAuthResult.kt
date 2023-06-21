package com.android.mdl.app.authconfirmation

sealed class PassphraseAuthResult {
    object Idle: PassphraseAuthResult()
    data class Success(val userPassphrase: String): PassphraseAuthResult()
}
