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

import com.android.identity.securearea.SecureArea
import com.android.identity.securearea.SecureAreaRepository
import com.android.identity.storage.StorageEngine
import com.android.identity.util.Logger

/**
 * Class for storing real-world identity credentials.
 *
 * This class is designed for storing real-world identity credentials such as
 * Mobile Driving Licenses (mDL) as specified in ISO/IEC 18013-5:2021. It is however
 * not tied to that specific credential format and is designed to hold any kind of
 * credential, regardless of format, presentation-, or issuance-protocol used.
 *
 * This code relies on a Secure Area for keys and this dependency is abstracted
 * by the [SecureArea] interface and allows the use of different [SecureArea]
 * implementations for *Authentication Keys*) associated with credentials stored
 * in the Credential Store.
 *
 * It is guaranteed that once a credential is created with [.createCredential],
 * each subsequent call to [.lookupCredential] will return the same
 * [Credential] instance.
 *
 * For more details about credentials stored in a [CredentialStore] see the
 * [Credential] class.
 *
 * @param storageEngine the [StorageEngine] to use for storing/retrieving credentials.
 * @param secureAreaRepository the repository of configured [SecureArea] that can
 * be used.
 */
class CredentialStore(
    private val storageEngine: StorageEngine,
    private val secureAreaRepository: SecureAreaRepository
) {
    // Use a cache so the same instance is returned by multiple lookupCredential() calls.
    private val credentialCache = mutableMapOf<String, Credential>()

    /**
     * Creates a new credential.
     *
     * If a credential with the given identifier already exists, it will be deleted prior to
     * creating the credential.
     *
     * The returned credential isn't yet added to the store and exists only in memory
     * (e.g. not persisted to the [StorageEngine] the credential store has been configured with)
     * until [addCredential] has been called. Observers of the store will not be notified until
     * this happens.
     *
     * @param name an identifier for the credential.
     * @return A newly created credential.
     */
    fun createCredential(name: String): Credential {
        lookupCredential(name)?.let { credential ->
            credentialCache.remove(name)
            emitOnCredentialDeleted(credential)
            credential.deleteCredential()
        }
        val transientCredential = Credential.create(
            storageEngine,
            secureAreaRepository,
            name,
            this
        )
        return transientCredential
    }

    /**
     * Adds a credential created with [createCredential] to the credential store.
     *
     * This makes the credential visible to observers.
     *
     * @param credential the credential.
     */
    fun addCredential(credential: Credential) {
        credential.addToStore()
        credentialCache[credential.name] = credential
        emitOnCredentialAdded(credential)
    }

    /**
     * Looks up a credential previously added to the store with [addCredential].
     *
     * @param name the identifier of the credential.
     * @return the credential or `null` if not found.
     */
    fun lookupCredential(name: String): Credential? {
        val result =
            credentialCache[name]
                ?: Credential.lookup(storageEngine, secureAreaRepository, name, this)
                ?: return null
        credentialCache[name] = result
        return result
    }

    /**
     * Lists all credentials in the store.
     *
     * @return list of all the credential names in the store.
     */
    fun listCredentials(): List<String> = mutableListOf<String>().apply {
        storageEngine.enumerate()
            .filter { name -> name.startsWith(Credential.CREDENTIAL_PREFIX) }
            .map { name -> name.substring(Credential.CREDENTIAL_PREFIX.length) }
            .forEach { name -> add(name) }
    }

    /**
     * Deletes a credential.
     *
     * If the credential doesn't exist this does nothing.
     *
     * @param name the identifier of the credential.
     */
    fun deleteCredential(name: String) {
        lookupCredential(name)?.let { credential ->
            credentialCache.remove(name)
            emitOnCredentialDeleted(credential)
            credential.deleteCredential()
        }
    }

    private val observers = mutableListOf<Observer>()

    fun startObserving(observer: Observer) {
        observers.add(observer)
    }

    fun stopObserving(observer: Observer) {
        if (!observers.remove(observer)) {
            Logger.w(TAG, "Observer to be removed doesn't exist")
        }
    }

    private fun emitOnCredentialAdded(credential: Credential) {
        for (observer in observers) {
            observer.onCredentialAdded(credential)
        }
    }

    private fun emitOnCredentialDeleted(credential: Credential) {
        for (observer in observers) {
            observer.onCredentialDeleted(credential)
        }
    }

    // Called by code in Credential class
    internal fun emitOnCredentialChanged(credential: Credential) {
        if (credentialCache[credential.name] == null) {
            // This is to prevent emitting onChanged when creating a credential.
            return
        }
        for (observer in observers) {
            observer.onCredentialChanged(credential)
        }
    }

    /**
     * An interface which can be used to observe when credentials are added, removed, and changed
     * in a [CredentialStore].
     */
    interface Observer {
        /**
         * Called when a credential is added to the store using [addCredential].
         *
         * @param credential the credential that was added.
         */
        fun onCredentialAdded(credential: Credential)

        /**
         * Called when a credential is removed from the store using [deleteCredential].
         *
         * @param credential the credential that was removed.
         */
        fun onCredentialDeleted(credential: Credential)

        /**
         * Called when data on a credential changed.
         *
         * @param credential the credential for which data was changed.
         */
        fun onCredentialChanged(credential: Credential)
    }

    companion object {
        const val TAG = "CredentialStore"
    }
}