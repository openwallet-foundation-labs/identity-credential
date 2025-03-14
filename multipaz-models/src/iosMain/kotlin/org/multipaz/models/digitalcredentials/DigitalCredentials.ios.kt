package org.multipaz.models.digitalcredentials

import org.multipaz.document.DocumentStore
import org.multipaz.documenttype.DocumentTypeRepository

internal actual val defaultAvailable = false

internal actual suspend fun defaultStartExportingCredentials(
    documentStore: DocumentStore,
    documentTypeRepository: DocumentTypeRepository,
) {
    throw NotImplementedError("DigitalCredentials is not available on iOS")
}

internal actual suspend fun defaultStopExportingCredentials(
    documentStore: DocumentStore,
) {
    throw NotImplementedError("DigitalCredentials is not available on iOS")
}
