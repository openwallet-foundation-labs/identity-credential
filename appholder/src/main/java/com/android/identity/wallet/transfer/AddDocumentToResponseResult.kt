package com.android.identity.wallet.transfer

import com.android.identity.credential.AuthenticationKey

sealed class AddDocumentToResponseResult {

    data class DocumentAdded(
        val signingKeyUsageLimitPassed: Boolean
    ) : AddDocumentToResponseResult()

    data class DocumentLocked(
        val authKey: AuthenticationKey
    ) : AddDocumentToResponseResult()
}