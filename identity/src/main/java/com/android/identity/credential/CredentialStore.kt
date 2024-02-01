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
    private val credentialCache: HashMap<String, Credential> = LinkedHashMap()

    /**
     * Creates a new credential.
     *
     * If a credential with the given name already exists, it will be overwritten by the
     * newly created credential.
     *
     * @param name name of the credential.
     * @return A newly created credential.
     */
    fun createCredential(name: String) = Credential.create(
        storageEngine,
        secureAreaRepository,
        name
    ).also {
        credentialCache[name] = it
    }

    /**
     * Looks up a previously created credential.
     *
     * @param name the name of the credential.
     * @return the credential or `null` if not found.
     */
    fun lookupCredential(name: String): Credential? {
        val result =
            credentialCache[name]
                ?: Credential.lookup(storageEngine, secureAreaRepository, name)
                ?: return null
        credentialCache[name] = result
        return result
    }

    /**
     * Lists all credentials in the store.
     *
     * @return list of all credential names in the store.
     */
    fun listCredentials(): List<String> {
        val ret = mutableListOf<String>()
        for (name in storageEngine.enumerate()) {
            if (name.startsWith(Credential.CREDENTIAL_PREFIX)) {
                ret.add(name.substring(Credential.CREDENTIAL_PREFIX.length))
            }
        }
        return ret
    }

    /**
     * Deletes a credential.
     *
     * If the credential doesn't exist this does nothing.
     *
     * @param name the name of the credential.
     */
    fun deleteCredential(name: String) {
        val credential = lookupCredential(name) ?: return
        credentialCache.remove(name)
        credential.deleteCredential()
    }
}