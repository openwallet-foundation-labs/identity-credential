package com.android.identity.issuance

/**
 * An interface representing an Issuing Authority.
 *
 * An [IssuingAuthority] instance represents a particular credential type from a particular
 * issuer. If a single issuer has credentials of different types, an [IssuingAuthority] instance
 * is needed for each type.
 */
interface IssuingAuthority {
    /**
     * Static information about the Issuing Authority.
     */
    val configuration: IssuingAuthorityConfiguration

    /**
     * Performs a network call to the Issuing Authority to get information about a credential.
     *
     * @throws UnknownCredentialException if the given credentialId isn't valid.
     */
    suspend fun credentialGetState(credentialId: String): CredentialState

    /**
     * Sets an observer to be notified when a credential has an updated state.
     *
     * Updates might be implemented using a lossy mechanism (e.g. push notifications)
     * so applications must not rely on getting a callback whenever the state changes.
     *
     * The observer can be removed using [stopObserving].
     *
     * @param observer the observer.
     */
    fun startObserving(observer: Observer)

    /**
     * Removes the observer previously set with [startObserving].
     *
     * @param observer the observer.
     */
    fun stopObserving(observer: Observer)

    /**
     * An interface which can be used to be informed when a credential has changed from
     * an issuer's point of view.
     */
    interface Observer {
        /**
         * This is called when a credential's state has changed.
         *
         * The application should call [IssuingAuthority.credentialGetState] to collect
         * the new state.
         *
         * @param issuingAuthority the issuing authority.
         * @param credentialId the credential which state has changed.
         */
        fun onCredentialStateChanged(issuingAuthority: IssuingAuthority, credentialId: String)
    }

    /**
     * Calls the IA to start creating a credential.
     *
     * The result of this flow is credentialId which is an unique identifier for the credential
     * and used in all subsequent communications with the issuer.
     *
     * If this completes successfully, [credentialGetState] can be used to check the state
     * of the credential.
     *
     * @return a [RegisterCredentialFlow] instance.
     */
    fun registerCredential(): RegisterCredentialFlow

    /**
     * Calls the IA to start proofing the user.
     *
     * This will fail unless the state returned by [credentialGetState] is in the condition
     * [CredentialCondition.PROOFING_REQUIRED].
     *
     * If this completes successfully, the condition will transition to
     * [CredentialCondition.PROOFING_PROCESSING]
     *
     * @param credentialId the credential to perform proofing for.
     * @return a [ProofingFlow] instance.
     * @throws IllegalStateException if not in state [CredentialCondition.PROOFING_REQUIRED].
     * @throws UnknownCredentialException if the given credentialId isn't valid.
     */
    fun credentialProof(credentialId: String): ProofingFlow

    /**
     * Calls the IA to get configuration for the configured credential.
     *
     * Once this has been read, the condition changes to [CredentialCondition.READY]
     *
     * @param credentialId the credential to get information about.
     * @throws IllegalStateException if not in condition [CredentialCondition.CONFIGURATION_AVAILABLE].
     * @throws UnknownCredentialException if the given credentialId isn't valid.
     */
    suspend fun credentialGetConfiguration(credentialId: String): CredentialConfiguration

    /**
     * Request issuance of Credential Presentation Objects.
     *
     * This flow is for requesting Credentials Presentation Objects which is an asynchronous
     * operation since it may take several hours for the issuer to mint these (this may happen
     * at times when compute resources are less expensive).
     *
     * The application can track the number of pending and available objects in the
     * [CredentialState] object and collect the ones that are ready, via
     * the [credentialGetPresentationObjects] method.
     *
     * @param credentialId the credential to request presentation objects for.
     * @throws IllegalStateException if not in state [CredentialCondition.READY].
     * @throws UnknownCredentialException if the given credentialId isn't valid.
     */
    fun credentialRequestPresentationObjects(credentialId: String): RequestPresentationObjectsFlow

    /**
     * Calls the IA to get available Credential Presentation Objects.
     *
     * This should be called when [numAvailableCredentialPresentationObjects] in
     * the state is greater than zero. On success, these objects will be removed
     * from the IA server and [numAvailableCredentialPresentationObjects] will
     * be set to 0 in the state.
     *
     * @throws UnknownCredentialException if the given credentialId isn't valid.
     */
    suspend fun credentialGetPresentationObjects(credentialId: String):
            List<CredentialPresentationObject>

    /**
     * Request update of the document data
     *
     * This is a developer-mode feature for the application to request a document update
     * update. This is optional for an issuing authority to implement, it may be a no-op.
     *
     * When this is called the issuer should generate a new [CredentialConfiguration], put the
     * credential into the [CredentialCondition.CONFIGURATION_AVAILABLE] condition
     * and post a notification to the application if the passed [notifyApplicationOfUpdate]
     * is set to true. The
     *
     * Upon receiving this notification the application will show an UI notification
     * to the user, delete existing CPOs / AuthKeys, download the new [CredentialConfiguration],
     * and request new CPOs.
     *
     * The sole reason for this feature is to make it easy to test the data update
     * flow both from an application and an issuer point of view. The [notifyApplicationOfUpdate]
     * parameter can be set to false used to simulate a lossy notification distribution network.
     *
     * @param credentialId the credential to request an update for.
     * @param requestRemoteDeletion request that the document is deleted.
     * @param notifyApplicationOfUpdate true if the issuer should send a notification to
     * the application about this, false to not send a notification
     * @throws IllegalStateException if not in state [CredentialCondition.READY].
     * @throws UnknownCredentialException if the given credentialId isn't valid.
     */
    suspend fun credentialDeveloperModeRequestUpdate(
        credentialId: String,
        requestRemoteDeletion: Boolean,
        notifyApplicationOfUpdate: Boolean
    )

}
