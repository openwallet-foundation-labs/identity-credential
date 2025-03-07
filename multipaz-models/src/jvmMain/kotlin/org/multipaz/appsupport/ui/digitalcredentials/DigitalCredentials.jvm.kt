package org.multipaz.models.ui.digitalcredentials

import org.multipaz.credential.Credential
import org.multipaz.document.DocumentStore
import org.multipaz.documenttype.DocumentTypeRepository
import kotlinx.io.bytestring.ByteString

internal actual val defaultAvailable = false

internal actual suspend fun defaultStartExportingCredentials(
    documentStore: DocumentStore,
    documentTypeRepository: DocumentTypeRepository,
) {
    throw NotImplementedError("DigitalCredentials is not available on JVM")
}

internal actual suspend fun defaultStopExportingCredentials(
    documentStore: DocumentStore,
) {
    throw NotImplementedError("DigitalCredentials is not available on JVM")
}
