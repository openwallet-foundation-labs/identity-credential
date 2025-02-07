package com.android.identity.android.direct_access

import android.os.Build
import androidx.annotation.RequiresApi
import com.android.identity.document.DocumentMetadata

/**
 * An interface that must be implemented by [DocumentMetadata] implementation of the documents
 * that can host [DirectAccessCredential]s.
 */
@RequiresApi(Build.VERSION_CODES.P)
interface DirectAccessDocumentMetadata: DocumentMetadata {
    var directAccessDocumentSlot: Int

    override suspend fun documentDeleted() {
        DirectAccess.clearDocumentSlot(directAccessDocumentSlot)
    }
}