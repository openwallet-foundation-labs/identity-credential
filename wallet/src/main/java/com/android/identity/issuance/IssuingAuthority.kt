package com.android.identity.issuance

import kotlinx.coroutines.flow.SharedFlow

/**
 * An interface representing an Issuing Authority.
 *
 * An [IssuingAuthority] instance represents a particular document type from a particular
 * issuer. If a single issuer has documents of different types, an [IssuingAuthority] instance
 * is needed for each type.
 */
interface IssuingAuthority {
    /**
     * Static information about the Issuing Authority.
     */
    val configuration: IssuingAuthorityConfiguration

    /**
     * Performs a network call to the Issuing Authority to get information about a document.
     *
     * @throws UnknownDocumentException if the given documentId isn't valid.
     */
    suspend fun documentGetState(documentId: String): DocumentState

    /**
     * A [SharedFlow] which can be used to listen for when a credential has changed state
     * on the issuer side. The first element in the pair is an [IssuingAuthority], the second
     * element is the `credentialId`.
     */
    val eventFlow: SharedFlow<Pair<IssuingAuthority, String>>

    /**
     * Calls the IA to start creating a document.
     *
     * The result of this flow is documentId which is an unique identifier for the document
     * and used in all subsequent communications with the issuer.
     *
     * If this completes successfully, [documentGetState] can be used to check the state
     * of the document.
     *
     * @return a [RegisterDocumentFlow] instance.
     */
    fun registerDocument(): RegisterDocumentFlow

    /**
     * Calls the IA to start proofing the user.
     *
     * This will fail unless the state returned by [documentGetState] is in the condition
     * [DocumentCondition.PROOFING_REQUIRED].
     *
     * If this completes successfully, the condition will transition to
     * [DocumentCondition.PROOFING_PROCESSING]
     *
     * @param documentId the document to perform proofing for.
     * @return a [ProofingFlow] instance.
     * @throws IllegalStateException if not in state [DocumentCondition.PROOFING_REQUIRED].
     * @throws UnknownDocumentException if the given documentId isn't valid.
     */
    fun documentProof(documentId: String): ProofingFlow

    /**
     * Calls the IA to get configuration for the configured document.
     *
     * Once this has been read, the condition changes to [DocumentCondition.READY]
     *
     * @param documentId the document to get information about.
     * @throws IllegalStateException if not in condition [DocumentCondition.CONFIGURATION_AVAILABLE].
     * @throws UnknownDocumentException if the given documentId isn't valid.
     */
    suspend fun documentGetConfiguration(documentId: String): DocumentConfiguration

    /**
     * Request issuance of Document Presentation Objects.
     *
     * This flow is for requesting Documents Presentation Objects which is an asynchronous
     * operation since it may take several hours for the issuer to mint these (this may happen
     * at times when compute resources are less expensive).
     *
     * The application can track the number of pending and available objects in the
     * [DocumentState] object and collect the ones that are ready, via
     * the [documentGetPresentationObjects] method.
     *
     * @param documentId the document to request presentation objects for.
     * @throws IllegalStateException if not in state [DocumentCondition.READY].
     * @throws UnknownDocumentException if the given documentId isn't valid.
     */
    fun documentRequestPresentationObjects(documentId: String): RequestPresentationObjectsFlow

    /**
     * Calls the IA to get available Document Presentation Objects.
     *
     * This should be called when [numAvailableDocumentPresentationObjects] in
     * the state is greater than zero. On success, these objects will be removed
     * from the IA server and [numAvailableDocumentPresentationObjects] will
     * be set to 0 in the state.
     *
     * @throws UnknownDocumentException if the given documentId isn't valid.
     */
    suspend fun documentGetPresentationObjects(documentId: String):
            List<DocumentPresentationObject>

    /**
     * Request update of the document data
     *
     * This is a developer-mode feature for the application to request a document update
     * update. This is optional for an issuing authority to implement, it may be a no-op.
     *
     * When this is called the issuer should generate a new [DocumentConfiguration], put the
     * document into the [DocumentCondition.CONFIGURATION_AVAILABLE] condition
     * and post a notification to the application if the passed [notifyApplicationOfUpdate]
     * is set to true. The
     *
     * Upon receiving this notification the application will show an UI notification
     * to the user, delete existing CPOs / AuthKeys, download the new [DocumentConfiguration],
     * and request new CPOs.
     *
     * The sole reason for this feature is to make it easy to test the data update
     * flow both from an application and an issuer point of view. The [notifyApplicationOfUpdate]
     * parameter can be set to false used to simulate a lossy notification distribution network.
     *
     * @param documentId the document to request an update for.
     * @param requestRemoteDeletion request that the document is deleted.
     * @param notifyApplicationOfUpdate true if the issuer should send a notification to
     * the application about this, false to not send a notification
     * @throws IllegalStateException if not in state [DocumentCondition.READY].
     * @throws UnknownDocumentException if the given documentId isn't valid.
     */
    suspend fun documentDeveloperModeRequestUpdate(
        documentId: String,
        requestRemoteDeletion: Boolean,
        notifyApplicationOfUpdate: Boolean
    )

}
