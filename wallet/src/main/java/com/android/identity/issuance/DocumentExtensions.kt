package com.android.identity.issuance

import com.android.identity.android.securearea.AndroidKeystoreCreateKeySettings
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.android.securearea.UserAuthenticationType
import com.android.identity.document.Document
import com.android.identity.document.DocumentUtil
import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.SecureAreaRepository
import com.android.identity.util.Logger
import com.android.identity.util.Timestamp
import com.android.identity_credential.wallet.R
import com.android.identity_credential.wallet.WalletApplication
import java.lang.IllegalArgumentException
import kotlin.time.Duration.Companion.hours
import kotlin.time.DurationUnit
import kotlin.time.toDuration

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
            this.state = issuer.documentGetState(documentIdentifier)
            return true
        } catch (e: UnknownDocumentException) {
            applicationData.setBoolean("isDeleted", true)
            return false
        }
    }

    /**
     * This performs all the essential bits of housekeeping for the credential, including
     * requesting CPOs, checking for PII updates, and so on.
     *
     * Throttling is implemented internally meaning that this will only reach out to the
     * issuer every N hours or after a network change meaning this is safe to call at
     * from the application at any time, e.g. application start-up, or when a network
     * change has been detected. (TODO: spell out policy and make it customizable.)
     *
     * @param issuingAuthorityRepository a repository of issuing authorities.
     * @param secureAreaRepository a repository of Secure Area implementations available.
     * @param forceUpdate if true, throttling will be bypassed.
     * @return the number of authentication keys refreshed or -1 if the credential was deleted
     * @throws IllegalArgumentException if the issuer isn't known.
     */
    suspend fun Document.housekeeping(
        walletApplication: WalletApplication,
        issuingAuthorityRepository: IssuingAuthorityRepository,
        secureAreaRepository: SecureAreaRepository,
        forceUpdate: Boolean,
    ): Int {
        if (forceUpdate) {
            Logger.i(TAG, "housekeeping: Forcing update")
        } else {
            val limit = 4.hours
            val sinceLastUpdate = (state.timestamp - System.currentTimeMillis()).toDuration(DurationUnit.MILLISECONDS)
            if (sinceLastUpdate.inWholeHours < 4) {
                Logger.i(
                    TAG,
                    "housekeeping: Last update was ${sinceLastUpdate.inWholeSeconds} " +
                            "seconds ago, skipping (limit: ${limit.inWholeSeconds})"
                )
                return 0
            }
            Logger.i(
                TAG,
                "housekeeping: Last update was ${sinceLastUpdate.inWholeSeconds} " +
                        "seconds ago, proceeding (limit: ${limit.inWholeSeconds} seconds)"
            )
        }

        val issuer = issuingAuthorityRepository.lookupIssuingAuthority(issuingAuthorityIdentifier)
            ?: throw IllegalArgumentException("No issuer with id $issuingAuthorityIdentifier")

        // TODO: this should all come from issuer configuration
        val numAuthKeys = 3
        val minValidTimeMillis = 30 * 24 * 3600L
        val secureArea = secureAreaRepository.getImplementation("AndroidKeystoreSecureArea")!!
        val authKeyDomain = WalletApplication.CREDENTIAL_DOMAIN

        // OK, let's see if configuration is available
        if (!refreshState(issuingAuthorityRepository)) {
            walletApplication.postNotificationForDocument(
                this,
                walletApplication.applicationContext.getString(
                    R.string.notifications_document_deleted_by_issuer
                )
            )
            return -1
        }
        if (state.condition == DocumentCondition.CONFIGURATION_AVAILABLE) {
            certifiedCredentials.forEach { it.delete() }
            pendingCredentials.forEach { it.delete() }

            documentConfiguration = issuer.documentGetConfiguration(documentIdentifier)

            // Notify the user that the document is either ready or an update has been downloaded.
            if (numDocumentConfigurationsDownloaded == 0L) {
                walletApplication.postNotificationForDocument(
                    this,
                    walletApplication.applicationContext.getString(
                        R.string.notifications_document_application_approved_and_ready_to_use
                    )
                )
            } else {
                walletApplication.postNotificationForDocument(
                    this,
                    walletApplication.applicationContext.getString(
                        R.string.notifications_document_update_from_issuer
                    )
                )
            }
            numDocumentConfigurationsDownloaded += 1

            if (!refreshState(issuingAuthorityRepository)) {
                return -1
            }
        }

        // Request new CPOs if needed
        if (state.condition == DocumentCondition.READY) {
            val now = Timestamp.now()
            // First do a dry-run to see how many pending authentication keys will be created
            val numPendingAuthKeysToCreate = DocumentUtil.managedCredentialHelper(
                this,
                secureArea,
                null,
                authKeyDomain,
                now,
                numAuthKeys,
                1,
                minValidTimeMillis,
                true)
            if (numPendingAuthKeysToCreate > 0) {
                val requestCpoFlow = issuer.documentRequestPresentationObjects(documentIdentifier)
                val authKeyConfig = requestCpoFlow.getAuthenticationKeyConfiguration()
                val authKeySettings: CreateKeySettings = if (secureArea is AndroidKeystoreSecureArea) {
                    AndroidKeystoreCreateKeySettings.Builder(authKeyConfig.challenge)
                        .setUserAuthenticationRequired(
                            true, 0,
                            setOf(UserAuthenticationType.LSKF, UserAuthenticationType.BIOMETRIC))
                        .build()
                } else {
                    CreateKeySettings(authKeyConfig.challenge)
                }
                DocumentUtil.managedCredentialHelper(
                    this,
                    secureArea,
                    authKeySettings,
                    authKeyDomain,
                    now,
                    numAuthKeys,
                    1,
                    minValidTimeMillis,
                    false)
                val documentPresentationRequests = mutableListOf<DocumentPresentationRequest>()
                for (pendingAuthKey in pendingCredentials) {
                    documentPresentationRequests.add(
                        DocumentPresentationRequest(
                            DocumentPresentationFormat.MDOC_MSO,
                            pendingAuthKey.attestation
                        )
                    )
                }
                requestCpoFlow.sendAuthenticationKeys(documentPresentationRequests)
                if (!refreshState(issuingAuthorityRepository)) {
                    return -1
                }
            }
        }

        var numAuthKeysRefreshed = 0
        if (state.numAvailableCPO > 0) {
            for (cpo in issuer.documentGetPresentationObjects(documentIdentifier)) {
                val pendingAuthKey = pendingCredentials.find {
                    it.attestation.certificates.first().publicKey.equals(cpo.authenticationKey) }
                if (pendingAuthKey == null) {
                    Logger.w(TAG, "No pending AuthenticationKey for pubkey ${cpo.authenticationKey}")
                    continue
                }
                pendingAuthKey.certify(
                    cpo.presentationData,
                    Timestamp.ofEpochMilli(cpo.validFromMillis),
                    Timestamp.ofEpochMilli(cpo.validUntilMillis))
                numAuthKeysRefreshed += 1
            }
            if (!refreshState(issuingAuthorityRepository)) {
                return -1
            }
        }
        return numAuthKeysRefreshed
    }

}
