package com.android.identity.appsupport.ui.presentment

import com.android.identity.credential.Credential
import com.android.identity.document.Document
import com.android.identity.documenttype.DocumentTypeRepository
import com.android.identity.request.Request
import com.android.identity.trustmanagement.TrustPoint

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
     * Selects one or more credentials eligible for presentment for a request.
     *
     * One example where [preSelectedDocument] is non-`null` is when using the W3C Digital Credentials
     * API on Android where the operating system displays a document picker prior to invoking the
     * application.
     *
     * The credentials returned must be for distinct documents.
     *
     * @param request the request.
     * @param preSelectedDocument if not `null`, a [Document] preselected by the user.
     * @return zero, one, or more [Credential] instances eligible for presentment.
     */
    suspend fun selectCredentialForPresentment(
        request: Request,
        preSelectedDocument: Document?
    ): List<Credential>

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
     * @param credential the credential being presented.
     * @param request the request.
     * @return `true` to always use signatures even if key agreement is possible.
     */
    fun shouldPreferSignatureToKeyAgreement(
        credential: Credential,
        request: Request,
    ): Boolean
}