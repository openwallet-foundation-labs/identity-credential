package org.multipaz.models.presentment

import org.multipaz.credential.Credential
import org.multipaz.document.Document
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.request.Request
import org.multipaz.trustmanagement.TrustPoint

/**
 * An interface used for the application to provide data and policy for credential presentment.
 */
interface PresentmentSource {

    /**
     * The [DocumentTypeRepository] to look up metadata for incoming requests.
     */
    val documentTypeRepository: DocumentTypeRepository

    /**
     * Finds a [TrustPoint] for a requester.
     *
     * @param request The request.
     * @return a [TrustPoint] or `null` if none could be found.
     */
    fun findTrustPoint(
        request: Request
    ): TrustPoint?

    /**
     * Gets the documents that match a given request.
     *
     * The documents returned must be distinct.
     *
     * @param request the request.
     * @return zero, one, or more [Document] instances eligible for presentment.
     */
    suspend fun getDocumentsMatchingRequest(
        request: Request,
    ): List<Document>

    /**
     * Returns a credential that can be presented.
     *
     * @param request the request.
     * @param document the document to get a credential from or `null`.
     * @return a [CredentialForPresentment] object with a credential that can be used for presentment.
     */
    suspend fun getCredentialForPresentment(
        request: Request,
        document: Document?
    ): CredentialForPresentment

    /**
     * Function to determine if the consent prompt should be shown.
     *
     * @param credential the credential being presented.
     * @param request the request.
     * @return `true` if the consent prompt should be shown, `false` otherwise.
     */
    fun shouldShowConsentPrompt(
        credential: Credential,
        request: Request,
    ): Boolean

    /**
     * Function to determine if a Signature should be used for the response if both
     * Key Agreement and Signatures are possible options.
     *
     * Key Agreement provides better privacy to the credential holder because it does not
     * require producing a potentially non-repudiable signature over reader-provided data.
     * The holder can always deny the MAC value to a third party because the reader
     * could have produced it by itself.
     *
     * In some cases the reader might prefer a Signature to get proof that the credential
     * holder really participated in the transaction. This function provides a mechanism
     * for the holder to honor such a request. The request from the reader to express
     * this preference would need to be provided out-of-band by the reader.
     *
     * @param document the document being presented.
     * @param request the request.
     * @return `true` to always use signatures even if key agreement is possible.
     */
    fun shouldPreferSignatureToKeyAgreement(
        document: Document,
        request: Request,
    ): Boolean
}