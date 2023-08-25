package com.android.mdl.app.transfer

sealed class AddDocumentToResponseResult {

    data class DocumentAdded(
        val signingKeyUsageLimitPassed: Boolean
    ) : AddDocumentToResponseResult()

    data class UserAuthRequired(
        val keyAlias: String,
        val allowLSKFUnlocking: Boolean,
        val allowBiometricUnlocking: Boolean
    ) : AddDocumentToResponseResult()

    object PassphraseRequired : AddDocumentToResponseResult()
}