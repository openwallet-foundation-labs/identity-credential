package org.multipaz.document

import kotlinx.io.bytestring.ByteString

/**
 * Interface that all objects returned in [Document.metadata] must implement.
 *
 * Most applications will likely just use [DocumentMetadata] but if there are needs to store
 * application-specific data for each document they may implement this interface by
 * an application specific class.
 */
interface AbstractDocumentMetadata {
    /** Whether the document is provisioned, i.e. issuer is ready to provide credentials. */
    val provisioned: Boolean

    /** User-facing name of this specific [Document] instance, e.g. "John's Passport". */
    val displayName: String?

    /** User-facing name of this document type, e.g. "Utopia Passport". */
    val typeDisplayName: String?

    /**
     * An image that represents this document to the user in the UI. Generally, the aspect
     * ratio of 1.586 is expected (based on ID-1 from the ISO/IEC 7810). PNG format is expected
     * and transparency is supported.
     * */
    val cardArt: ByteString?

    /**
     * An image that represents the issuer of the document in the UI, e.g. passport office logo.
     * PNG format is expected, transparency is supported and square aspect ratio is preferred.
     */
    val issuerLogo: ByteString?

    /**
     * Additional data the application wishes to store.
     */
    val other: ByteString?

    /**
     * Marks the document as being provisioned.
     *
     * This sets the [provisioned] property to `true`.
     */
    suspend fun markAsProvisioned()

    /**
     * Updates the metadata for the document.
     *
     * @param displayName User-facing name of this specific [Document] instance, e.g. "John's Passport", or `null`.
     * @param typeDisplayName User-facing name of this document type, e.g. "Utopia Passport", or `null`.
     * @param cardArt An image that represents this document to the user in the UI. Generally, the aspect
     *   ratio of 1.586 is expected (based on ID-1 from the ISO/IEC 7810). PNG format is expected
     *   and transparency is supported.
     * @param issuerLogo An image that represents the issuer of the document in the UI, e.g. passport office logo.
     *   PNG format is expected, transparency is supported and square aspect ratio is preferred.
     * @param other Additional data the application wishes to store.
     */
    suspend fun setMetadata(
        displayName: String?,
        typeDisplayName: String?,
        cardArt: ByteString?,
        issuerLogo: ByteString?,
        other: ByteString?
    )
}
