package com.android.identity.testapp

import com.android.identity.testapp.provisioning.model.ProvisioningModel
import org.multipaz.document.Document
import org.multipaz.provisioning.evidence.Openid4VciCredentialOffer

/**
 * A listener for credential issuance events.
 *
 * This listener can be implemented by UI components to get updates on the
 * progress of credential issuance.
 */
interface IssuanceListener {
    /**
     * Called when the state of the issuance process changes.
     *
     * @param state The new state.
     */
    fun onStateChanged(state: ProvisioningModel.State)

    /**
     * Called when the credential has been successfully issued.
     *
     * @param document The document containing the new credential.
     */
    fun onIssuanceCompleted(document: Document)

    /**
     * Called when an error occurs during issuance.
     *
     * @param error The error that occurred.
     */
    fun onIssuanceError(error: Throwable)

    /**
     * Called when the user is asked for consent and makes a choice.
     *
     * @param granted True if the user accepted/continued, false otherwise.
     */
    fun onUserConsent(granted: Boolean)

    /**
     * Called when the user makes a choice from a list of options.
     *
     * @param choice The ID of the choice the user made.
     */
    fun onChoiceMade(choice: String)
}

/**
 * A singleton object to manage the issuance listener and dispatch events.
 *
 * This allows different parts of the application to be notified of issuance
 * events without being tightly coupled.
 */
object CredentialManager {
    private var issuanceListener: IssuanceListener? = null
    private var provisioningModel: ProvisioningModel? = null
    private var navigateToProvisioning: (() -> Unit)? = null

    /**
     * Initializes the CredentialManager with necessary dependencies.
     * This should be called once when the application starts.
     *
     * @param provisioningModel The singleton instance of the ProvisioningModel.
     * @param navigateToProvisioning A lambda function to trigger navigation to the provisioning screen.
     */
    fun initialize(provisioningModel: ProvisioningModel, navigateToProvisioning: () -> Unit) {
        this.provisioningModel = provisioningModel
        this.navigateToProvisioning = navigateToProvisioning
    }

    /**
     * Starts the entire credential issuance flow.
     *
     * @param offer The credential offer that initiates the flow.
     */
    fun startIssuanceFlow(offer: Openid4VciCredentialOffer) {
        val model = provisioningModel
            ?: throw IllegalStateException("CredentialManager not initialized. Call initialize() first.")
        val navigate = navigateToProvisioning
            ?: throw IllegalStateException("CredentialManager not initialized. Call initialize() first.")

        model.startProvisioning(offer)
        navigate()
    }

    /**
     * Sets the issuance listener.
     *
     * @param listener The listener to set.
     */
    fun setIssuanceListener(listener: IssuanceListener) {
        this.issuanceListener = listener
    }

    /**
     * Removes the issuance listener.
     */
    fun removeIssuanceListener() {
        this.issuanceListener = null
    }

    /**
     * Called by the provisioning logic to notify of a state change.
     */
    internal fun onStateChanged(state: ProvisioningModel.State) {
        issuanceListener?.onStateChanged(state)
    }

    /**
     * Called by the provisioning logic when issuance is complete.
     */
    internal fun onIssuanceCompleted(document: Document) {
        issuanceListener?.onIssuanceCompleted(document)
    }

    /**
     * Called by the provisioning logic when an error occurs.
     */
    internal fun onIssuanceError(error: Throwable) {
        issuanceListener?.onIssuanceError(error)
    }

    /**
     * Called when the user makes a consent decision.
     */
    internal fun onUserConsent(granted: Boolean) {
        issuanceListener?.onUserConsent(granted)
    }

    /**
     * Called when the user makes a choice.
     */
    internal fun onChoiceMade(choice: String) {
        issuanceListener?.onChoiceMade(choice)
    }
}