package com.android.mdl.app.authprompt

import androidx.biometric.BiometricPrompt

class BiometricUserAuthPrompt(
    private val prompt: BiometricPrompt,
    private val promptInfo: BiometricPrompt.PromptInfo
) {

    fun authenticate(cryptoObject: BiometricPrompt.CryptoObject?) {
        if (cryptoObject != null) {
            prompt.authenticate(promptInfo, cryptoObject)
        } else {
            prompt.authenticate(promptInfo)
        }
    }
}