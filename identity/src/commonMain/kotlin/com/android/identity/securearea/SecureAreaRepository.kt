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
package com.android.identity.securearea

import com.android.identity.util.Logger

/**
 * A repository of [SecureArea] implementations.
 *
 * This is used by to provide fine-grained control for which [SecureArea]
 * implementation to use when loading keys and objects using different implementations.
 */
class SecureAreaRepository {
    companion object {
        private const val TAG = "SecureAreaRepository"
    }

    private var _implementations = mutableListOf<SecureArea>()

    private val implementationFactories = mutableMapOf<String, (String) -> SecureArea?>()

    /**
     * All [SecureArea] implementations in the repository.
     */
    val implementations: List<SecureArea>
        get() = _implementations

    /**
     * Gets a [SecureArea] by identifier
     *
     * The identifier being used is the one returned by [SecureArea.identifier].
     *
     * @param identifier the identifier for the Secure Area.
     * @return the implementation or `null` if no implementation has been registered.
     */
    fun getImplementation(identifier: String): SecureArea? {
        var result = _implementations.firstOrNull { it.identifier == identifier }
        if (result != null) {
            return result
        }

        val questionMarkLocation = identifier.indexOf('?')
        if (questionMarkLocation == -1) {
            return null
        }

        val identifierPrefix = identifier.substring(0, questionMarkLocation)
        val factoryFunc = implementationFactories[identifierPrefix]
        if (factoryFunc == null) {
            return null
        }

        result = factoryFunc(identifier)
        if (result != null) {
            if (identifier != result.identifier) {
                Logger.w(TAG, "Requested identifier `$identifier` got `${result.identifier}`")
            }
            _implementations.add(result)
            return result
        }

        return null
    }

    /**
     * Adds a [SecureArea] to the repository.
     *
     * @param secureArea an instance of a type implementing the [SecureArea] interface.
     */
    fun addImplementation(secureArea: SecureArea) = _implementations.add(secureArea)

    /**
     * Adds a factory function to create a [SecureArea] on demand.
     *
     * This can be used for Secure Areas where it's possible to have multiple instances. For
     * example for a [CloudSecureArea] the URL of the server is part of the identifier e.g.
     * `CloudSecureArea?id=SOME_UNIQUE_ID&url=https://csa.example.com/server`. An application
     * can use this method to register a factory for establishing a connection to the
     * requested URL and configure the instance as needed.
     *
     * @param identifierPrefix prefix of the Secure Area identifier up until the '?' character.
     * @param factoryFunc a function to create a new Secure Area with the requested identifier.
     */
    fun addImplementationFactory(
        identifierPrefix: String,
        factoryFunc: (identifier: String) -> SecureArea?
    ) {
        implementationFactories.put(identifierPrefix, factoryFunc)
    }
}