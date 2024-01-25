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
     * @throws IllegalArgumentException if the Issuing Authority does not recognize the given
     *   passed-in credentialId
     */
    suspend fun credentialGetState(credentialId: String): CredentialState

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
     */
    fun credentialProof(credentialId: String): ProofingFlow

    /**
     * Calls the IA to get configuration for the configured credential.
     *
     * Once this has been read, the condition changes to [CredentialCondition.READY]
     *
     * @param credentialId the credential to get information about.
     * @throws IllegalStateException if not in condition [CredentialCondition.CONFIGURATION_AVAILABLE].
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
     */
    fun credentialRequestPresentationObjects(credentialId: String): RequestPresentationObjectsFlow


    /**
     * Calls the IA to get available Credential Presentation Objects.
     *
     * This should be called when [numAvailableCredentialPresentationObjects] in
     * the state is greater than zero. On success, these objects will be removed
     * from the IA server and [numAvailableCredentialPresentationObjects] will
     * be set to 0 in the state.
     */
    suspend fun credentialGetPresentationObjects(credentialId: String):
            List<CredentialPresentationObject>
}
