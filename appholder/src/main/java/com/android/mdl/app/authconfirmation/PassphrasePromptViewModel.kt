package com.android.mdl.app.authconfirmation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class PassphrasePromptViewModel : ViewModel() {

    private val _authorizationState =
        MutableStateFlow<PassphraseAuthResult>(PassphraseAuthResult.Idle)
    val authorizationState: StateFlow<PassphraseAuthResult> = _authorizationState

    fun authorize(userPassphrase: String) {
        _authorizationState.update { PassphraseAuthResult.Success(userPassphrase) }
    }

    fun reset() {
        _authorizationState.update { PassphraseAuthResult.Idle }
    }
}