package com.android.identity.wallet.transfer

import com.android.identity.credential.Credential

sealed class AddDocumentToResponseResult {

    data class DocumentAdded(
        val signingKeyUsageLimitPassed: Boolean
    ) : AddDocumentToResponseResult()

    data class DocumentLocked(
        val authKey: Credential.AuthenticationKey
    ) : AddDocumentToResponseResult()
}