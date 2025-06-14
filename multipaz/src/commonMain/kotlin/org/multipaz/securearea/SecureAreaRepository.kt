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
package org.multipaz.securearea

import org.multipaz.util.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A repository of [SecureArea] implementations.
 *
 * This is used by to provide fine-grained control for which [SecureArea]
 * implementation to use when loading keys and objects using different implementations.
 *
 * Use [Builder] to create a new instance.
 */
class SecureAreaRepository private constructor(
    private val secureAreaByIdentifier: MutableMap<String, SecureArea>,
    private val implementationFactories: Map<String, suspend (String) -> SecureArea?>,
    private var providers: List<SecureAreaProvider<*>>?
) {
    private val lock = Mutex()

    // Must be called with lock held
    private suspend fun drainProviders() {
        require(lock.isLocked)
        if (providers != null) {
            for (provider in providers) {
                val secureArea = provider.get()
                secureAreaByIdentifier[secureArea.identifier] = secureArea
            }
        }
        providers = null
    }

    /**
     * Gets a [SecureArea] by identifier
     *
     * The identifier being used is the one returned by [SecureArea.identifier].
     *
     * @param identifier the identifier for the Secure Area.
     * @return the implementation or `null` if no implementation has been registered.
     */
    suspend fun getImplementation(identifier: String): SecureArea? {
        lock.withLock {
            drainProviders()

            val existing = secureAreaByIdentifier[identifier]
            if (existing != null) {
                return existing
            }

            val questionMarkLocation = identifier.indexOf('?')
            if (questionMarkLocation == -1) {
                return null
            }
            val identifierPrefix = identifier.substring(0, questionMarkLocation)
            val factoryFunc = implementationFactories[identifierPrefix] ?: return null

            val newSecureArea = factoryFunc(identifier) ?: return null
            if (identifier != newSecureArea.identifier) {
                Logger.e(TAG, "Requested identifier `$identifier` got `${newSecureArea.identifier}`")
            }
            secureAreaByIdentifier[newSecureArea.identifier] = newSecureArea
            return newSecureArea
        }
    }

    /**
     * Builder for [SecureAreaRepository].
     */
    class Builder {
        val byIdentifier = mutableMapOf<String, SecureArea>()
        val factories = mutableMapOf<String, suspend (String) -> SecureArea?>()
        val providers = mutableListOf<SecureAreaProvider<*>>()

        /**
         * Adds a Secure Area factory that can be used for Secure Areas where it's possible to
         * have multiple instances.
         *
         * For example for a `CloudSecureArea` the URL of the server is part of the identifier e.g.
         * `CloudSecureArea?id=SOME_UNIQUE_ID&url=https://csa.example.com/server`. An application
         * can use this method to register a factory for establishing a connection to the
         * requested URL and configure the instance as needed. Key to the map is Secure Area
         * identifier prefix of the Secure Area identifier up until the '?' character.
         *
         * @param identifierPrefix part of the identifier before '?' character for the secure
         *     areas created by the factory
         * @param factory a function to create a new Secure Area with the requested identifier.
         * @return the builder.
         */
        fun addFactory(identifierPrefix: String, factory: suspend (identifierPrefix: String) -> SecureArea?): Builder {
            if (factories.contains(identifierPrefix)) {
                throw IllegalArgumentException("Duplicate SecureArea factory: $identifierPrefix")
            }
            factories[identifierPrefix] = factory
            return this
        }

        /**
         * Adds a Secure Area implementation.
         *
         * @param secureArea the [SecureArea] to add.
         * @return the builder.
         */
        fun add(secureArea: SecureArea): Builder {
            if (byIdentifier.contains(secureArea.identifier)) {
                throw IllegalArgumentException("Duplicate SecureArea: ${secureArea.identifier}")
            }
            byIdentifier[secureArea.identifier] = secureArea
            return this
        }

        /**
         * Adds a Secure Area implemention from a [SecureAreaProvider]
         *
         * @param secureAreaProvider the [SecureAreaProvider] which can provide the [SecureArea]
         * @return the builder
         */
        fun <T: SecureArea> addProvider(secureAreaProvider: SecureAreaProvider<T>): Builder {
            providers.add(secureAreaProvider)
            return this
        }

        /**
         * Builds the [SecureAreaRepository].
         *
         * @return a [SecureAreaRepository].
         */
        fun build(): SecureAreaRepository {
            return SecureAreaRepository(byIdentifier, factories, providers)
        }
    }

    companion object {
        private const val TAG = "SecureAreaRepository"
    }
}