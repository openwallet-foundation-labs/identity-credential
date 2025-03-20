package org.multipaz.android.direct_access

import android.os.Build
import androidx.annotation.RequiresApi
import org.multipaz.document.DocumentMetadata

/**
 * An interface that must be implemented by [DocumentMetadata] implementation of the documents
 * that can host [DirectAccessCredential]s.
 */
@RequiresApi(Build.VERSION_CODES.P)
interface DirectAccessDocumentMetadata: DocumentMetadata {
    var directAccessDocumentSlot: Int

    override suspend fun documentDeleted() {
        if (directAccessDocumentSlot >= 0) {
            DirectAccess.clearDocumentSlot(directAccessDocumentSlot)
        }
    }
}