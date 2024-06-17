/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.identity.document

import com.android.identity.credential.CredentialFactory
import com.android.identity.securearea.SecureArea
import com.android.identity.securearea.SecureAreaRepository
import com.android.identity.storage.StorageEngine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking

/**
 * Class for storing real-world identity documents.
 *
 * This class is designed for storing real-world identity documents such as
 * Mobile Driving Licenses (mDL) as specified in ISO/IEC 18013-5:2021. It is however
 * not tied to that specific document format and is designed to hold any kind of
 * document, regardless of format, presentation-, or issuance-protocol used.
 *
 * This code relies on a Secure Area for keys and this dependency is abstracted
 * by the [SecureArea] interface and allows the use of different [SecureArea]
 * implementations for *Authentication Keys*) associated with documents stored
 * in the Document Store.
 *
 * It is guaranteed that once a document is created with [createDocument],
 * each subsequent call to [lookupDocument] will return the same
 * [Document] instance.
 *
 * For more details about documents stored in a [DocumentStore] see the
 * [Document] class.
 *
 * @param storageEngine the [StorageEngine] to use for storing/retrieving documents.
 * @param secureAreaRepository the repository of configured [SecureArea] that can
 * be used.
 * @param credentialFactory the [CredentialFactory] to use for retrieving serialized credentials
 * associated with documents.
 */
class DocumentStore(
    private val storageEngine: StorageEngine,
    private val secureAreaRepository: SecureAreaRepository,
    private val credentialFactory: CredentialFactory
) {
    // Use a cache so the same instance is returned by multiple lookupDocument() calls.
    private val documentCache = mutableMapOf<String, Document>()

    /**
     * Creates a new document.
     *
     * If a document with the given identifier already exists, it will be deleted prior to
     * creating the document.
     *
     * The returned document isn't yet added to the store and exists only in memory
     * (e.g. not persisted to the [StorageEngine] the document store has been configured with)
     * until [addDocument] has been called. Events will not be emitted (via [eventFlow]) until
     * this happens
     *
     * @param name an identifier for the document.
     * @return A newly created document.
     */
    fun createDocument(name: String): Document {
        lookupDocument(name)?.let { document ->
            documentCache.remove(name)
            emitOnDocumentDeleted(document)
            document.deleteDocument()
        }
        val transientDocument = Document.create(
            storageEngine,
            secureAreaRepository,
            name,
            this,
            credentialFactory
        )
        return transientDocument
    }

    /**
     * Adds a document created with [createDocument] to the document store.
     *
     * This makes the document visible to collectors collecing from [eventFlow].
     *
     * @param document the document.
     */
    fun addDocument(document: Document) {
        document.addToStore()
        documentCache[document.name] = document
        emitOnDocumentAdded(document)
    }

    /**
     * Looks up a document previously added to the store with [addDocument].
     *
     * @param name the identifier of the document.
     * @return the document or `null` if not found.
     */
    fun lookupDocument(name: String): Document? {
        val result =
            documentCache[name]
                ?: Document.lookup(storageEngine, secureAreaRepository, name, this, credentialFactory)
                ?: return null
        documentCache[name] = result
        return result
    }

    /**
     * Lists all documents in the store.
     *
     * @return list of all the document names in the store.
     */
    fun listDocuments(): List<String> = mutableListOf<String>().apply {
        storageEngine.enumerate()
            .filter { name -> name.startsWith(Document.DOCUMENT_PREFIX) }
            .map { name -> name.substring(Document.DOCUMENT_PREFIX.length) }
            .forEach { name -> add(name) }
    }

    /**
     * Deletes a document.
     *
     * If the document doesn't exist this does nothing.
     *
     * @param name the identifier of the document.
     */
    fun deleteDocument(name: String) {
        lookupDocument(name)?.let { document ->
            documentCache.remove(name)
            emitOnDocumentDeleted(document)
            document.deleteDocument()
        }
    }

    /**
     * Types of events used in the [eventFlow] property.
     */
    enum class EventType {
        /**
         * A document was added to the store.
         */
        DOCUMENT_ADDED,

        /**
         * A document was deleted from the store.
         */
        DOCUMENT_DELETED,

        /**
         * A document in the store was updated.
         */
        DOCUMENT_UPDATED
    }

    private val _eventFlow = MutableSharedFlow<Pair<EventType, Document>>()

    /**
     * A [SharedFlow] which can be used to listen for when credentials are added and removed
     * from the store as well as when credentials in the store have been updated.
     */
    val eventFlow
        get() = _eventFlow.asSharedFlow()


    private fun emitOnDocumentAdded(document: Document) {
        runBlocking {
            _eventFlow.emit(Pair(EventType.DOCUMENT_ADDED, document))
        }
    }

    private fun emitOnDocumentDeleted(document: Document) {
        runBlocking {
            _eventFlow.emit(Pair(EventType.DOCUMENT_DELETED, document))
        }
    }

    // Called by code in Document class
    internal fun emitOnDocumentChanged(document: Document) {
        if (documentCache[document.name] == null) {
            // This is to prevent emitting onChanged when creating a document.
            return
        }
        runBlocking {
            _eventFlow.emit(Pair(EventType.DOCUMENT_UPDATED, document))
        }
    }

    companion object {
        const val TAG = "DocumentStore"
    }
}