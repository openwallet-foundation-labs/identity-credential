package org.multipaz.issuance

import org.multipaz.flow.annotation.FlowInterface
import org.multipaz.flow.annotation.FlowMethod
import org.multipaz.flow.client.FlowNotifiable

/**
 * An interface representing an Issuing Authority.
 *
 * An [IssuingAuthority] instance represents a particular document type from a particular
 * issuer. If a single issuer has documents of different types, an [IssuingAuthority] instance
 * is needed for each type.
 *
 * Documents are identifier by an identifier - `documentId` - and each document may have
 * multiple credentials associated with it.
 */
@FlowInterface
interface IssuingAuthority : FlowNotifiable<IssuingAuthorityNotification> {
    /**
     * Calls the issuer to start creating a document.
     *
     * The result of this flow is `documentId` which is an unique identifier for the document
     * and used in all subsequent communications with the issuer. If this completes successfully,
     * [getState] can be used to check the state of the document.
     *
     * @return a [RegistrationFlow] instance.
     */
    @FlowMethod
    suspend fun register(): RegistrationFlow

    /**
     * Performs a network call to get configuration for the Issuing Authority.
     */
    @FlowMethod
    suspend fun getConfiguration(): IssuingAuthorityConfiguration

    /**
     * Performs a network call to the Issuing Authority to get information about a document.
     *
     * If the document was recently deleted the condition will be set to
     * [DocumentCondition.NO_SUCH_DOCUMENT].
     */
    @FlowMethod
    suspend fun getState(documentId: String): DocumentState

    /**
     * Calls the issuer to start proofing the applicant.
     *
     * This will fail unless the state returned by [getState] is in the condition
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
    @FlowMethod
    suspend fun proof(documentId: String): ProofingFlow

    /**
     * Calls the issuer to get configuration for the configured document.
     *
     * Once this has been read, the condition changes to [DocumentCondition.READY]
     *
     * @param documentId the document to get information about.
     * @throws IllegalStateException if not in condition [DocumentCondition.CONFIGURATION_AVAILABLE].
     * @throws UnknownDocumentException if the given documentId isn't valid.
     */
    @FlowMethod
    suspend fun getDocumentConfiguration(documentId: String): DocumentConfiguration

    /**
     * Request issuance of credentials for a document.
     *
     * This flow is for requesting Credentials which is an asynchronous operation since it
     * could take several hours for the issuer to mint these (this may happen at times when
     * compute resources are less expensive).
     *
     * The application can track the number of pending and available objects in the
     * [DocumentState] object and collect the ones that are ready, via
     * the [getCredentials] method.
     *
     * @param documentId the document to request presentation objects for.
     * @throws IllegalStateException if not in state [DocumentCondition.READY].
     * @throws UnknownDocumentException if the given documentId isn't valid.
     */
    @FlowMethod
    suspend fun requestCredentials(documentId: String): RequestCredentialsFlow

    /**
     * Calls the IA to get available credentials.
     *
     * This should be called when [DocumentState.numAvailableCredentials] in
     * the state is greater than zero. On success, these objects will be removed
     * from the IA server and [DocumentState.numAvailableCredentials] will
     * be set to 0 in the state.
     *
     * @throws UnknownDocumentException if the given documentId isn't valid.
     */
    @FlowMethod
    suspend fun getCredentials(documentId: String): List<CredentialData>

    /**
     * Request update of the document data
     *
     * This is a developer-mode feature for the application to request a document update
     * update. This is optional for an issuing authority to implement, it may be a no-op.
     *
     * When this is called the issuer should generate a new [DocumentConfiguration], put the
     * document into the [DocumentCondition.CONFIGURATION_AVAILABLE] condition
     * and post a notification to the application if the passed [notifyApplicationOfUpdate]
     * is set to true.
     *
     * Upon receiving this notification the application will show an UI notification
     * to the user, delete existing credentials, download the new [DocumentConfiguration],
     * and request new credentials.
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
    @FlowMethod
    suspend fun developerModeRequestUpdate(
        documentId: String,
        requestRemoteDeletion: Boolean,
        notifyApplicationOfUpdate: Boolean
    )
}
