package com.android.identity.issuance


import com.android.identity.document.Document
import java.lang.IllegalArgumentException

/**
 * A set of extensions on the [Document] type for working with the [IssuingAuthority]
 * and related classes.
 */
object DocumentExtensions {
    const val TAG = "DocumentExtensions"

    /** The identifier for the [IssuingAuthority] the credential belongs to */
    var Document.issuingAuthorityIdentifier: String
        get() = applicationData.getString("issuingAuthorityIdentifier")
        set(value) { applicationData.setString("issuingAuthorityIdentifier", value) }

    /**
     * The identifier for the credential, as assigned by the issuer
     *
     * This is the _credentialId_ value used in [IssuingAuthority] when communicating
     * with the issuing authority.
     */
    var Document.documentIdentifier: String
        get() = applicationData.getString("credentialIdentifier")
        set(value) { applicationData.setString("credentialIdentifier", value) }

    /**
     * The number of [DocumentConfiguration] objects downloaded from the issuer.
     */
    var Document.numDocumentConfigurationsDownloaded: Long
        get() {
            if (!applicationData.keyExists("numDocumentConfigurationsDownloaded")) {
                return 0
            }
            return applicationData.getNumber("numDocumentConfigurationsDownloaded")
        }
        set(value) { applicationData.setNumber("numDocumentConfigurationsDownloaded", value) }

    /** The [DocumentConfiguration] received from the issuer */
    var Document.documentConfiguration: DocumentConfiguration
        get() = DocumentConfiguration.fromCbor(applicationData.getData("credentialConfiguration"))
        set(value) { applicationData.setData("credentialConfiguration", value.toCbor()) }

    /**
     * The most recent [DocumentState] received from the issuer.
     *
     * This doesn't ping the issuer so the information may be stale. Applications can consult
     * the [DocumentState.timestamp] field to figure out the age of the state and use
     * [Document.refreshState] to refresh it directly from the issuer server.
     */
    var Document.state: DocumentState
        get() = DocumentState.fromCbor(applicationData.getData("credentialState"))
        set(value) { applicationData.setData("credentialState", value.toCbor()) }

    /**
     * Set to true if the credential was deleted on the issuer-side.
     */
    val Document.isDeleted: Boolean
        get() {
            return try {
                applicationData.getBoolean("isDeleted")
            } catch (e: Throwable) {
                false
            }
        }

    /**
     * Gets the credential state from the Issuer and updates the [.state] property with the value.
     *
     * Unlike reading from the [.state] property, this performs network I/O to communicate
     * with the issuer.
     *
     * If the credential doesn't exist (for example it could have been deleted recently) the
     * [Document.isDeleted] property will be set to true and this method returns false.
     *
     * @param issuingAuthorityRepository a repository of issuing authorities.
     * @return true if the refresh succeeded, false if the credential is unknown.
     * @throws IllegalArgumentException if the issuer isn't know.
     */
    suspend fun Document.refreshState(issuingAuthorityRepository: IssuingAuthorityRepository):
            Boolean {
        val issuer = issuingAuthorityRepository.lookupIssuingAuthority(issuingAuthorityIdentifier)
            ?: throw IllegalArgumentException("No issuer with id $issuingAuthorityIdentifier")
        try {
            this.state = issuer.issuingAuthority.getState(documentIdentifier)
            return true
        } catch (e: UnknownDocumentException) {
            applicationData.setBoolean("isDeleted", true)
            return false
        }
    }
}
