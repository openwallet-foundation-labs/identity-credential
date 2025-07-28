package org.multipaz.models.digitalcredentials

import org.multipaz.document.DocumentStore
import org.multipaz.documenttype.DocumentTypeRepository

/**
 * An interface for interacting with the W3C Digital Credentials API provider
 * on the platform, if available.
 */
interface DigitalCredentials {

    /**
     * Whether this API is available on the platform.
     */
    val available: Boolean

    /**
     * The set of W3C Digital Credentials protocols supported.
     */
    val supportedProtocols: Set<String>

    /**
     * The set of W3C Digital Credentials protocols currently selected.
     *
     * The default value for this is [supportedProtocols] but this may be changed using
     * [setSelectedProtocols] if supported by the platform.
     */
    val selectedProtocols: Set<String>

    /**
     * Sets the supported W3C Digital Credentials protocols, in order of preference.
     *
     * @param protocols the set of selected W3C protocols, must be a subset of [supportedProtocols].
     * @throws IllegalStateException if the platform doesn't allow configuring which protocols
     *   to export credentials on.
     */
    suspend fun setSelectedProtocols(protocols: Set<String>)

    /**
     * Registers all documents in the given [DocumentStore] with the platform.
     *
     * This also watches the store and updates the registration as documents and credentials
     * are added and removed.
     *
     * @param documentStore the [DocumentStore] to export credentials from.
     * @param documentTypeRepository a [DocumentTypeRepository].
     */
    suspend fun startExportingCredentials(
        documentStore: DocumentStore,
        documentTypeRepository: DocumentTypeRepository
    )

    /**
     * Stops exporting documents.
     *
     * All documents from the given store are unregistered with the platform.
     *
     * @param documentStore the [DocumentStore] passed to [startExportingCredentials]
     */
    suspend fun stopExportingCredentials(
        documentStore: DocumentStore
    )

    /**
     * The default implementation of the [DigitalCredentials] API on the platform.
     */
    object Default: DigitalCredentials {
        override val available: Boolean
            get() = defaultAvailable

        override val supportedProtocols: Set<String>
            get() = defaultSupportedProtocols

        override val selectedProtocols: Set<String>
            get() = defaultSelectedProtocols

        override suspend fun setSelectedProtocols(
            protocols: Set<String>
        ) = defaultSetSelectedProtocols(protocols)

        override suspend fun startExportingCredentials(
            documentStore: DocumentStore,
            documentTypeRepository: DocumentTypeRepository
        ) = defaultStartExportingCredentials(documentStore, documentTypeRepository)

        override suspend fun stopExportingCredentials(
            documentStore: DocumentStore
        ) = defaultStopExportingCredentials(documentStore)
    }
}

internal expect val defaultAvailable: Boolean

internal expect val defaultSupportedProtocols: Set<String>

internal expect val defaultSelectedProtocols: Set<String>

internal expect suspend fun defaultSetSelectedProtocols(
    protocols: Set<String>
)

internal expect suspend fun defaultStartExportingCredentials(
    documentStore: DocumentStore,
    documentTypeRepository: DocumentTypeRepository
)

internal expect suspend fun defaultStopExportingCredentials(
    documentStore: DocumentStore,
)