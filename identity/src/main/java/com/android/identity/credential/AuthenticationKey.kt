package com.android.identity.credential

import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.builder.MapBuilder
import co.nstant.`in`.cbor.model.ByteString
import co.nstant.`in`.cbor.model.DataItem
import com.android.identity.internal.Util
import com.android.identity.securearea.SecureArea
import com.android.identity.util.ApplicationData
import com.android.identity.util.Logger
import com.android.identity.util.SimpleApplicationData
import com.android.identity.util.Timestamp
import java.security.cert.X509Certificate

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
    val attestation: List<X509Certificate>
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
        val builder = CborBuilder()
        val mapBuilder: MapBuilder<CborBuilder> = builder.addMap()
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
            mapBuilder.put("replacementAlias", replacementAlias)
        }
        return builder.build()[0]
    }

    /**
     * The pending auth key that will replace this key once certified or `null` if no
     * key is designated to replace this key.
     */
    val replacement: PendingAuthenticationKey?
        get() {
            if (replacementAlias == null) {
                return null
            }
            for (pendingAuthKey in credential.pendingAuthenticationKeys) {
                if (pendingAuthKey.alias == replacementAlias) {
                    return pendingAuthKey
                }
            }
            Logger.w(TAG, "Pending key with alias $replacementAlias which " +
                    "is intended to replace this key does not exist")
            return null
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
        ): AuthenticationKey {
            val ret = AuthenticationKey()
            ret.alias = Util.cborMapExtractString(dataItem, "alias")
            if (Util.cborMapHasKey(dataItem, "domain")) {
                ret.domain = Util.cborMapExtractString(dataItem, "domain")
            } else {
                ret.domain = ""
            }
            val secureAreaIdentifier =
                Util.cborMapExtractString(dataItem, "secureAreaIdentifier")
            ret.secureArea =
                credential.secureAreaRepository.getImplementation(secureAreaIdentifier)!!
            requireNotNull(ret.secureArea) { "Unknown Secure Area $secureAreaIdentifier" }
            ret.usageCount = Util.cborMapExtractNumber(dataItem, "usageCount").toInt()
            ret.issuerProvidedData = Util.cborMapExtractByteString(dataItem, "data")
            ret.validFrom =
                Timestamp.ofEpochMilli(Util.cborMapExtractNumber(dataItem, "validFrom"))
            ret.validUntil =
                Timestamp.ofEpochMilli(Util.cborMapExtractNumber(dataItem, "validUntil"))
            if (Util.cborMapHasKey(dataItem, "replacementAlias")) {
                ret.replacementAlias = Util.cborMapExtractString(dataItem, "replacementAlias")
            }
            val applicationDataDataItem: DataItem =
                Util.cborMapExtract(dataItem, "applicationData")
            check(applicationDataDataItem is ByteString) { "applicationData not found or not byte[]" }
            ret.credential = credential
            ret.privateApplicationData = SimpleApplicationData.decodeFromCbor(
                applicationDataDataItem.bytes
            ) { ret.credential.saveCredential() }
            if (Util.cborMapHasKey(dataItem, "authenticationKeyCounter")) {
                ret.authenticationKeyCounter =
                    Util.cborMapExtractNumber(dataItem, "authenticationKeyCounter")
            }
            return ret
        }
    }
}