package com.android.identity.wallet.transfer

import com.android.identity.mdoc.credential.MdocCredential

sealed class AddDocumentToResponseResult {
    data class DocumentAdded(
        val signingKeyUsageLimitPassed: Boolean,
    ) : AddDocumentToResponseResult()

    data class DocumentLocked(
        val credential: MdocCredential,
    ) : AddDocumentToResponseResult()
}
