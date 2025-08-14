package org.multipaz.testapp

import androidx.compose.runtime.mutableStateMapOf
import org.multipaz.credential.SecureAreaBoundCredential
import org.multipaz.document.Document
import org.multipaz.document.DocumentAdded
import org.multipaz.document.DocumentDeleted
import org.multipaz.document.DocumentStore
import org.multipaz.document.DocumentUpdated
import org.multipaz.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.multipaz.compose.decodeImage

class DocumentModel(
    val scope: CoroutineScope,
    val documentStore: DocumentStore,
) {
    val documentInfos = mutableStateMapOf<String, DocumentInfo>()

    private val lock = Mutex()

    suspend fun initialize() {
        val docIds = documentStore.listDocuments()
        for (documentId in docIds) {
            val document = documentStore.lookupDocument(documentId)
            if (document != null) {
                documentInfos[documentId] = DocumentInfo(
                    document = document,
                    cardArt = decodeImage(document.metadata.cardArt?.toByteArray() ?: byteArrayOf()),
                    credentialInfos = document.buildCredentialInfos()
                )
            }
        }

        documentStore.eventFlow
            .onEach { event ->
                lock.withLock {
                    Logger.i(TAG, "DocumentStore event ${event::class.simpleName} ${event.documentId}")
                    when (event) {
                        is DocumentAdded -> {
                            val document = documentStore.lookupDocument(event.documentId)
                            if (document != null) {
                                documentInfos[event.documentId] = DocumentInfo(
                                    document = document,
                                    cardArt = decodeImage(document.metadata.cardArt?.toByteArray() ?: byteArrayOf()),
                                    credentialInfos = document.buildCredentialInfos()
                                )
                            }
                        }

                        is DocumentDeleted -> {
                            documentInfos.remove(event.documentId)
                        }

                        is DocumentUpdated -> {
                            val document = documentStore.lookupDocument(event.documentId)
                            if (document != null) {
                                documentInfos[event.documentId] = DocumentInfo(
                                    document = document,
                                    cardArt = decodeImage(document.metadata.cardArt?.toByteArray() ?: byteArrayOf()),
                                    credentialInfos = document.buildCredentialInfos()
                                )
                            }
                        }
                    }
                }
            }
            .launchIn(scope)
    }

    companion object {
        private const val TAG = "DocumentModel"

        private suspend fun Document.buildCredentialInfos(): List<CredentialInfo> {
            return getCredentials().map { credential ->
                val keyInfo = if (credential is SecureAreaBoundCredential) {
                    credential.secureArea.getKeyInfo(credential.alias)
                } else {
                    null
                }
                val keyInvalidated = if (credential is SecureAreaBoundCredential) {
                    credential.secureArea.getKeyInvalidated(credential.alias)
                } else {
                    false
                }
                CredentialInfo(
                    credential = credential,
                    keyInfo = keyInfo,
                    keyInvalidated = keyInvalidated
                )
            }
        }
    }
}
