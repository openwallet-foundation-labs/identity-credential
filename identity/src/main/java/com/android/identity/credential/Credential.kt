/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.identity.credential

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborMap
import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.SecureArea
import com.android.identity.securearea.SecureAreaRepository
import com.android.identity.storage.StorageEngine
import com.android.identity.util.ApplicationData
import com.android.identity.util.Logger
import com.android.identity.util.SimpleApplicationData
import com.android.identity.util.Timestamp

/**
 * This class represents a credential created in [CredentialStore].
 *
 * Credentials in this store are identified by a name which must be unique
 * per credential.
 *
 * Arbitrary data can be stored in credentials using the [ApplicationData] returned
 * by [.getApplicationData] which supports key/value pairs with typed values
 * including raw blobs, strings, booleans, numbers, and [NameSpacedData].
 * This data is persisted for the life-time of the credential.
 *
 * One typical use of [ApplicationData] is for using it to store the alias
 * of a [SecureArea] key used for communicating with the Issuing Authority
 * issuing data for the credential and and proving - via the attestation on the key - that
 * the device is in a known good state (e.g. verified boot is enabled, the OS is at a
 * sufficiently recent patch level, it's communicating with the expected Android
 * application, etc).
 *
 * Each credential may have a number of *Authentication Keys*
 * associated with it. These keys are intended to be used in ways specified by the
 * underlying credential format but the general idea is that they are created on
 * the device and then sent to the issuer for certification. The issuer then returns
 * some format-specific data related to the key. For Mobile Driving License and MDOCs according
 * to [ISO/IEC 18013-5:2021](https://www.iso.org/standard/69084.html)
 * the authentication key plays the role of *DeviceKey* and the issuer-signed
 * data includes the *Mobile Security Object* which includes the authentication
 * key and is signed by the issuer. This is used for anti-cloning and to return data signed
 * by the device. The way it works in this API is that the application can use
 * [.createPendingAuthenticationKey]
 * to get a [PendingAuthenticationKey]. With this in hand, the application can use
 * [PendingAuthenticationKey.attestation] and send the attestation
 * to the issuer for certification. The issuer will then craft credential-format
 * specific data (for ISO/IEC 18013-5:2021 it will be a signed MSO which references
 * the public part of the newly created authentication key) and send it back
 * to the app. The application can then call
 * [PendingAuthenticationKey.certify] to
 * upgrade the [PendingAuthenticationKey] to a [AuthenticationKey].
 *
 * At credential presentation time the application first receives the request
 * from a remote reader using a specific credential presentation protocol, such
 * as ISO/IEC 18013-5:2021. The details of the credential-specific request includes
 * enough information (for example, the *DocType* if using ISO/IEC 18013-5:2021)
 * for the application to locate a suitable [Credential] from a [CredentialStore].
 * See [CredentialRequest] for more information about how to generate the response for
 * the remote reader given a [Credential] instance.
 *
 * There is nothing mDL/MDOC specific about this type, it can be used for any kind
 * of credential regardless of format, presentation, or issuance protocol used.
 *
 * @param name the name of the credential which can be used with [CredentialStore].
 */
class Credential private constructor(
    val name: String,
    private val storageEngine: StorageEngine,
    internal val secureAreaRepository: SecureAreaRepository,
    private val store: CredentialStore
) {
    private var addedToStore = false

    internal fun addToStore() {
        addedToStore = true
        saveCredential()
    }

    /**
     * Application specific data.
     *
     * Use this object to store additional data an application may want to associate
     * with the authentication key. Setters and associated getters are
     * enumerated in the [ApplicationData] interface.
     */
    private var _applicationData = SimpleApplicationData { saveCredential() }
    val applicationData: ApplicationData
        get() = _applicationData

    /**
     * Pending authentication keys.
     */
    private var _pendingAuthenticationKeys = mutableListOf<PendingAuthenticationKey>()
    val pendingAuthenticationKeys: List<PendingAuthenticationKey>
        // Return shallow copy b/c backing field may get modified if certify() or delete() is called.
        get() = _pendingAuthenticationKeys.toList()

    /**
     * Certified authentication keys.
     */
    private var _authenticationKeys = mutableListOf<AuthenticationKey>()
    val authenticationKeys: List<AuthenticationKey>
        // Return shallow copy b/c backing field may get modified if certify() or delete() is called.
        get() = _authenticationKeys.toList()

    /**
     * Authentication key counter.
     *
     * This is a number which starts at 0 and is increased by one for every call
     * to [.createPendingAuthenticationKey].
     */
    var authenticationKeyCounter: Long = 0
        private set

    internal fun saveCredential() {
        if (!addedToStore) {
            return
        }
        val t0 = Timestamp.now()
        val mapBuilder = CborMap.builder().apply {
            put("applicationData", _applicationData.encodeAsCbor())
            val pendingAuthenticationKeysArrayBuilder = putArray("pendingAuthenticationKeys")
            for (pendingAuthenticationKey in _pendingAuthenticationKeys) {
                pendingAuthenticationKeysArrayBuilder.add(pendingAuthenticationKey.toCbor())
            }
            val authenticationKeysArrayBuilder = putArray("authenticationKeys")
            for (authenticationKey in _authenticationKeys) {
                authenticationKeysArrayBuilder.add(authenticationKey.toCbor())
            }
            put("authenticationKeyCounter", authenticationKeyCounter)
        }
        storageEngine.put(CREDENTIAL_PREFIX + name, Cbor.encode(mapBuilder.end().build()))
        val t1 = Timestamp.now()

        // Saving a credential is a costly affair (often more than 100ms) so log when we're doing
        // this so application developers are aware. This is to deter applications from storing
        // ephemeral data in the ApplicationData instances of the credential and our associated
        // authentication keys.
        val durationMillis = t1.toEpochMilli() - t0.toEpochMilli()
        Logger.i(TAG, "Saved credential '$name' to disk in $durationMillis msec")
        store.emitOnCredentialChanged(this)
    }

    private fun loadCredential(): Boolean {
        val data = storageEngine[CREDENTIAL_PREFIX + name] ?: return false
        val map = Cbor.decode(data)

        _applicationData = SimpleApplicationData
            .decodeFromCbor(map["applicationData"].asBstr) {
                saveCredential()
            }

        _pendingAuthenticationKeys = ArrayList()
        for (item in map["pendingAuthenticationKeys"].asArray) {
            _pendingAuthenticationKeys.add(PendingAuthenticationKey.fromCbor(item, this))
        }
        _authenticationKeys = ArrayList()
        for (item in map["authenticationKeys"].asArray) {
            _authenticationKeys.add(AuthenticationKey.fromCbor(item, this))
        }
        authenticationKeyCounter = map["authenticationKeyCounter"].asNumber
        addedToStore = true
        return true
    }

    fun deleteCredential() {
        _pendingAuthenticationKeys.clear()
        _authenticationKeys.clear()
        storageEngine.delete(CREDENTIAL_PREFIX + name)
    }

    /**
     * Finds a suitable authentication key to use.
     *
     * @param domain The domain to pick the authentication key from.
     * @param now Pass current time to ensure that the selected slot's validity period or
     * `null` to not consider validity times.
     * @return An authentication key which can be used for signing or `null` if none was found.
     */
    fun findAuthenticationKey(
        domain: String,
        now: Timestamp?
    ): AuthenticationKey? {
        var candidate: AuthenticationKey? = null
        _authenticationKeys.filter {
            it.domain == domain && (
                    now != null
                            && (now.toEpochMilli() >= it.validFrom.toEpochMilli())
                            && (now.toEpochMilli() <= it.validUntil.toEpochMilli())
                    )
        }.forEach { authenticationKey ->
            // If we already have a candidate, prefer this one if its usage count is lower
            candidate?.let { candidateAuthKey ->
                if (authenticationKey.usageCount < candidateAuthKey.usageCount) {
                    candidate = authenticationKey
                }
            } ?: run {
                candidate = authenticationKey
            }

        }
        return candidate
    }

    /**
     * Creates a new authentication key.
     *
     *
     * This returns a [PendingAuthenticationKey] which should be sent to the
     * credential issuer for certification. Use
     * [PendingAuthenticationKey.certify] when certification
     * has been obtained.
     *
     *
     * For a higher-level way of managing authentication keys, see
     * [CredentialUtil.managedAuthenticationKeyHelper].
     *
     * @param domain a string used to group authentications keys together.
     * @param secureArea the secure area to use for the authentication key.
     * @param createKeySettings settings for the authentication key.
     * @param asReplacementFor if not `null`, replace the given authentication key
     * with this one, once it has been certified.
     * @return a [PendingAuthenticationKey].
     * @throws IllegalArgumentException if `asReplacementFor` is not null and the given
     * key already has a pending key intending to replace it.
     */
    fun createPendingAuthenticationKey(
        domain: String,
        secureArea: SecureArea,
        createKeySettings: CreateKeySettings,
        asReplacementFor: AuthenticationKey?
    ): PendingAuthenticationKey {
        check(asReplacementFor?.replacement == null) {
            "The given key already has an existing pending key intending to replace it"
        }
        val alias =
            AUTHENTICATION_KEY_ALIAS_PREFIX + name + "_authKey_" + authenticationKeyCounter++
        val pendingAuthenticationKey = PendingAuthenticationKey.create(
            alias,
            domain,
            secureArea,
            createKeySettings,
            asReplacementFor,
            this
        )
        _pendingAuthenticationKeys.add(pendingAuthenticationKey)
        asReplacementFor?.setReplacementAlias(pendingAuthenticationKey.alias)
        saveCredential()
        return pendingAuthenticationKey
    }

    fun removePendingAuthenticationKey(pendingAuthenticationKey: PendingAuthenticationKey) {
        check(_pendingAuthenticationKeys.remove(pendingAuthenticationKey)) { "Error removing pending authentication key" }
        if (pendingAuthenticationKey.replacementForAlias != null) {
            for (authKey in _authenticationKeys) {
                if (authKey.alias == pendingAuthenticationKey.replacementForAlias) {
                    authKey.replacementAlias = null
                    break
                }
            }
        }
        saveCredential()
    }

    fun removeAuthenticationKey(authenticationKey: AuthenticationKey) {
        check(_authenticationKeys.remove(authenticationKey)) { "Error removing authentication key" }
        if (authenticationKey.replacementAlias != null) {
            for (pendingAuthKey in _pendingAuthenticationKeys) {
                if (pendingAuthKey.alias == authenticationKey.replacementAlias) {
                    pendingAuthKey.replacementForAlias = null
                    break
                }
            }
        }
        saveCredential()
    }

    fun certifyPendingAuthenticationKey(
        pendingAuthenticationKey: PendingAuthenticationKey,
        issuerProvidedAuthenticationData: ByteArray,
        validFrom: Timestamp,
        validUntil: Timestamp
    ): AuthenticationKey {
        check(_pendingAuthenticationKeys.remove(pendingAuthenticationKey)) { "Error removing pending authentication key" }
        val authenticationKey = AuthenticationKey.create(
            pendingAuthenticationKey,
            issuerProvidedAuthenticationData,
            validFrom,
            validUntil,
            this
        )
        _authenticationKeys.add(authenticationKey)
        val authKeyToDelete = pendingAuthenticationKey.replacementFor
        authKeyToDelete?.delete()
        saveCredential()
        return authenticationKey
    }

    companion object {
        private const val TAG = "Credential"
        const val CREDENTIAL_PREFIX = "IC_Credential_"
        const val AUTHENTICATION_KEY_ALIAS_PREFIX = "IC_AuthenticationKey_"

        // Called by CredentialStore.createCredential().
        fun create(
            storageEngine: StorageEngine,
            secureAreaRepository: SecureAreaRepository,
            name: String,
            store: CredentialStore
        ): Credential =
            Credential(name, storageEngine, secureAreaRepository, store).apply { saveCredential() }

        // Called by CredentialStore.lookupCredential().
        fun lookup(
            storageEngine: StorageEngine,
            secureAreaRepository: SecureAreaRepository,
            name: String,
            store: CredentialStore
        ): Credential? = Credential(name, storageEngine, secureAreaRepository, store).run {
            if (loadCredential()) {
                this// return this Credential object
            } else { // when credential.loadCredential() == false
                null // return null
            }
        }
    }
}