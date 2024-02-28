package com.android.identity.credential

import com.android.identity.cbor.Bstr
import com.android.identity.cbor.CborMap
import com.android.identity.cbor.DataItem
import com.android.identity.crypto.CertificateChain
import com.android.identity.securearea.SecureArea
import com.android.identity.util.ApplicationData
import com.android.identity.util.Logger
import com.android.identity.util.SimpleApplicationData
import com.android.identity.util.Timestamp

/**
 * A certified authentication key.
 *
 * To create an instance of this type, an application must first use
 * [Credential.createPendingAuthenticationKey]
 * to create a [PendingAuthenticationKey] and after issuer certification
 * has been received it can be upgraded to a [AuthenticationKey].
 */
class AuthenticationKey {
    /**
     * The alias for the authentication key.
     *
     * This can be used together with the [SecureArea] returned by [.getSecureArea]
     */
    lateinit var alias: String
        private set

    /**
     * The domain of the authentication key.
     *
     * This returns the domain set when the pending authentication key was created.
     */
    lateinit var domain: String
        private set

    /**
     * How many time the key in the slot has been used.
     */
    var usageCount = 0
        private set

    /**
     * The issuer-provided data associated with the key.
     */
    lateinit var issuerProvidedData: ByteArray
        private set

    /**
     * The point in time the issuer-provided data is valid from.
     */
    lateinit var validFrom: Timestamp
        private set

    /**
     * The point in time the issuer-provided data is valid until.
     */
    lateinit var validUntil: Timestamp
        private set

    private lateinit var privateApplicationData: SimpleApplicationData

    /**
     * Application specific data.
     *
     * Use this object to store additional data an application may want to associate
     * with the authentication key. Setters and associated getters are
     * enumerated in the [ApplicationData] interface.
     */
    val applicationData: ApplicationData
        get() = privateApplicationData

    /**
     * The authentication key counter.
     *
     * This is the value of the Credential's Authentication Key Counter
     * at the time the pending authentication key for this authentication key
     * was created.
     */
    var authenticationKeyCounter: Long = 0
        private set

    /**
     * The X.509 certificate chain for the authentication key
     */
    val attestation: CertificateChain
        get() = secureArea.getKeyInfo(alias).attestation

    /**
     * The secure area for the authentication key.
     *
     * This can be used together with the alias returned by [alias].
     *
     * @return The [SecureArea] used.
     */
    lateinit var secureArea: SecureArea

    private lateinit var credential: Credential

    internal var replacementAlias: String? = null

    /**
     * Deletes the authentication key.
     *
     *
     * After deletion, this object should no longer be used.
     */
    fun delete() {
        secureArea.deleteKey(alias)
        credential.removeAuthenticationKey(this)
    }

    /**
     * Increases usage count of the authentication key.
     */
    fun increaseUsageCount() {
        usageCount += 1
        credential.saveCredential()
    }

    fun toCbor(): DataItem {
        val mapBuilder = CborMap.builder()
        mapBuilder.put("alias", alias)
            .put("domain", domain)
            .put("secureAreaIdentifier", secureArea.identifier)
            .put("usageCount", usageCount.toLong())
            .put("data", issuerProvidedData)
            .put("validFrom", validFrom.toEpochMilli())
            .put("validUntil", validUntil.toEpochMilli())
            .put("applicationData", privateApplicationData.encodeAsCbor())
            .put("authenticationKeyCounter", authenticationKeyCounter)
        if (replacementAlias != null) {
            mapBuilder.put("replacementAlias", replacementAlias!!)
        }
        return mapBuilder.end().build()
    }

    /**
     * The pending auth key that will replace this key once certified or `null` if no
     * key is designated to replace this key.
     */
    val replacement: PendingAuthenticationKey?
        get() = credential.pendingAuthenticationKeys.firstOrNull { it.alias == replacementAlias }
            .also {
                if (it == null && replacementAlias != null) {
                    Logger.w(
                        TAG, "Pending key with alias $replacementAlias which " +
                                "is intended to replace this key does not exist"
                    )
                }
            }


    fun setReplacementAlias(alias: String) {
        replacementAlias = alias
        credential.saveCredential()
    }

    companion object {
        const val TAG = "AuthenticationKey"

        fun create(
            pendingAuthenticationKey: PendingAuthenticationKey,
            issuerProvidedAuthenticationData: ByteArray,
            validFrom: Timestamp,
            validUntil: Timestamp,
            credential: Credential
        ) = AuthenticationKey().apply {
            alias = pendingAuthenticationKey.alias
            domain = pendingAuthenticationKey.domain
            issuerProvidedData = issuerProvidedAuthenticationData
            this.validFrom = validFrom
            this.validUntil = validUntil
            this.credential = credential
            secureArea = pendingAuthenticationKey.secureArea
            privateApplicationData = pendingAuthenticationKey.privateApplicationData
            authenticationKeyCounter = pendingAuthenticationKey.authenticationKeyCounter
        }

        fun fromCbor(
            dataItem: DataItem,
            credential: Credential
        ) = AuthenticationKey().apply {
            val map = dataItem
            alias = map["alias"].asTstr
            domain = map["domain"].asTstr
            val secureAreaIdentifier = map["secureAreaIdentifier"].asTstr
            secureArea = credential.secureAreaRepository.getImplementation(secureAreaIdentifier)
                ?: throw IllegalStateException("Unknown Secure Area $secureAreaIdentifier")
            usageCount = map["usageCount"].asNumber.toInt()
            issuerProvidedData = map["data"].asBstr
            validFrom = Timestamp.ofEpochMilli(map["validFrom"].asNumber)
            validUntil = Timestamp.ofEpochMilli(map["validUntil"].asNumber)
            replacementAlias = map.getOrNull("replacementAlias")?.asTstr
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