package com.android.identity.testapp

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import com.android.identity.document.DocumentAdded
import com.android.identity.document.DocumentDeleted
import com.android.identity.document.DocumentStore
import com.android.identity.document.DocumentUpdated
import com.android.identity.util.Logger
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
    companion object {
        private const val TAG = "DocumentModel"
    }

    val documentInfos = mutableStateMapOf<String, DocumentInfo>()

    private val lock = Mutex()

    suspend fun initialize() {
        val docIds = documentStore.listDocuments()
        for (documentId in docIds) {
            val document = documentStore.lookupDocument(documentId)
            if (document != null) {
                documentInfos[documentId] = DocumentInfo(
                    document = document,
                    cardArt = decodeImage(document.metadata.cardArt!!.toByteArray()),
                    credentials = document.getCredentials()
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
                                    cardArt = decodeImage(document.metadata.cardArt!!.toByteArray()),
                                    credentials = document.getCredentials()
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
                                    cardArt = decodeImage(document.metadata.cardArt!!.toByteArray()),
                                    credentials = document.getCredentials()
                                )
                            }
                        }
                    }
                }
            }
            .launchIn(scope)
    }

}
