package com.android.identity.wallet.transfer

import com.android.identity.document.Credential

sealed class AddDocumentToResponseResult {

    data class DocumentAdded(
        val signingKeyUsageLimitPassed: Boolean
    ) : AddDocumentToResponseResult()

    data class DocumentLocked(
        val authKey: Credential
    ) : AddDocumentToResponseResult()
}