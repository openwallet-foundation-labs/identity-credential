package com.android.identity.credential

import com.android.identity.cbor.Bstr
import com.android.identity.cbor.CborMap
import com.android.identity.cbor.DataItem
import com.android.identity.crypto.CertificateChain
import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.SecureArea
import com.android.identity.util.ApplicationData
import com.android.identity.util.Logger
import com.android.identity.util.SimpleApplicationData
import com.android.identity.util.Timestamp

/**
 * An authentication key pending certification.
 *
 *
 * To create a pending authentication key, use
 * [Credential.createPendingAuthenticationKey].
 *
 *
 * Because issuer certification of authentication could take a long time, pending
 * authentication keys are persisted and [Credential.getPendingAuthenticationKeys]
 * can be used to get a list of instances. For example this can be used to re-ping
 * the issuing server for outstanding certification requests.
 *
 *
 * Once certification is complete, use [.certify]
 * to upgrade to a [AuthenticationKey].
 */
class PendingAuthenticationKey {
    /**
     * The secure area for the pending authentication key.
     *
     * This can be used together with the alias returned in [alias].
     */
    lateinit var secureArea: SecureArea
        private set

    /**
     * The alias for the pending authentication key.
     *
     * This can be used together with the [SecureArea] returned by [secureArea].
     */
    lateinit var alias: String
        private set

    /**
     * The domain of the pending authentication key.
     *
     * This returns the domain set when the pending authentication key was created.
     */
    lateinit var domain: String
        private set

    /**
     * Application specific data.
     *
     * Use this object to store additional data an application may want to associate
     * with the pending authentication key. Setters and associated getters are
     * enumerated in the [ApplicationData] interface.
     */
    internal lateinit var privateApplicationData: SimpleApplicationData
    val applicationData: ApplicationData
        get() = privateApplicationData

    /**
     * Gets the authentication key counter.
     *
     *
     * This is the value of the Credential's Authentication Key Counter
     * at the time this pending authentication key was created.
     *
     * @return the authentication key counter.
     */
    var authenticationKeyCounter: Long = 0
        private set

    /**
     * The X.509 certificate chain for the authentication key pending certification.
     *
     * The application should send this key to the issuer which should create issuer-provided
     * data (e.g. an MSO if using ISO/IEC 18013-5:2021) using the key as the `DeviceKey`.
     *
     * @return An X.509 certificate chain for the pending authentication key.
     */
    val attestation: CertificateChain
        get() = secureArea.getKeyInfo(alias).attestation

    /**
     * The auth key that will be replaced by this key once it's been certified.
     *
     * @return An [AuthenticationKey] or `null` if no key was designated
     * when this pending key was created.
     */
    val replacementFor: AuthenticationKey?
        get() = credential.authenticationKeys.firstOrNull { it.alias == replacementForAlias }
            .also {
                if (it == null && replacementForAlias != null) {
                    Logger.w(TAG, "Key with alias $replacementForAlias which " +
                            "is intended to be replaced does not exist"
                    )
                }
            }


    private lateinit var credential: Credential

    internal var replacementForAlias: String? = null

    /**
     * Deletes the pending authentication key.
     */
    fun delete() {
        secureArea.deleteKey(alias)
        credential.removePendingAuthenticationKey(this)
    }

    /**
     * Certifies the pending authentication key.
     *
     * This will convert this [PendingAuthenticationKey] into a
     * [AuthenticationKey] including preserving the application-data
     * set.
     *
     * The [PendingAuthenticationKey] object should no longer be used after calling this.
     *
     * @param issuerProvidedAuthenticationData the issuer-provided static authentication data.
     * @param validFrom the point in time before which the data is not valid.
     * @param validUntil the point in time after which the data is not valid.
     * @return a [AuthenticationKey].
     */
    fun certify(
        issuerProvidedAuthenticationData: ByteArray,
        validFrom: Timestamp,
        validUntil: Timestamp
    ) = credential.certifyPendingAuthenticationKey(
        this,
        issuerProvidedAuthenticationData,
        validFrom,
        validUntil
    )

    fun toCbor(): DataItem {
        val mapBuilder = CborMap.builder()
        mapBuilder.put("alias", alias)
            .put("domain", domain)
            .put("secureAreaIdentifier", secureArea.identifier)
            .put("applicationData", privateApplicationData.encodeAsCbor())
            .put("authenticationKeyCounter", authenticationKeyCounter)
        if (replacementForAlias != null) {
            mapBuilder.put("replacementForAlias", replacementForAlias!!)
        }
        return mapBuilder.end().build()
    }

    companion object {
        const val TAG = "PendingAuthenticationKey"

        fun create(
            alias: String,
            domain: String,
            secureArea: SecureArea,
            createKeySettings: CreateKeySettings,
            asReplacementFor: AuthenticationKey?,
            credential: Credential
        ) = PendingAuthenticationKey().run {
            this.alias = alias
            this.domain = domain
            this.secureArea = secureArea
            this.secureArea.createKey(alias, createKeySettings)
            replacementForAlias = asReplacementFor?.alias
            this.credential = credential
            privateApplicationData = SimpleApplicationData { credential.saveCredential() }
            authenticationKeyCounter = credential.authenticationKeyCounter
            this
        }

        fun fromCbor(
            dataItem: DataItem,
            credential: Credential
        ) = PendingAuthenticationKey().apply {
            val map = dataItem
            alias = map["alias"].asTstr
            domain = map["domain"].asTstr
            val secureAreaIdentifier = map["secureAreaIdentifier"].asTstr
            secureArea = credential.secureAreaRepository.getImplementation(secureAreaIdentifier)
                ?: throw IllegalStateException("Unknown Secure Area $secureAreaIdentifier")
            replacementForAlias = map.getOrNull("replacementForAlias")?.asTstr
            val applicationDataDataItem = map["applicationData"]
            check(applicationDataDataItem is Bstr) { "applicationData not found or not byte[]" }
            this.credential = credential
            privateApplicationData = SimpleApplicationData
                .decodeFromCbor(applicationDataDataItem.value) {
                    credential.saveCredential()
                }
            authenticationKeyCounter = map["authenticationKeyCounter"].asNumber
        }
    }
}