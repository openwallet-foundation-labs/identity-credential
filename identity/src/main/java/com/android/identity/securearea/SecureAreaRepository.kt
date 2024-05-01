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

import java.util.Collections

/**
 * A repository of [SecureArea] implementations.
 *
 * This is used by to provide fine-grained control for which [SecureArea]
 * implementation to use when loading keys and objects using different implementations.
 */
class SecureAreaRepository {
    private var privateImplementations = mutableListOf<SecureArea>()

    /**
     * All [SecureArea] implementations in the repository.
     */
    val implementations: List<SecureArea>
        get() = Collections.unmodifiableList(privateImplementations)

    /**
     * Gets a [SecureArea] by identifier
     *
     * The identifier being used is the one returned by [SecureArea.identifier].
     *
     * @param identifier the identifier for the Secure Area.
     * @return the implementation or `null` if no implementation has been registered.
     */
    fun getImplementation(identifier: String): SecureArea? = privateImplementations.firstOrNull { it.identifier == identifier }

    /**
     * Adds a [SecureArea] to the repository.
     *
     * @param secureArea an instance of a type implementing the [SecureArea] interface.
     */
    fun addImplementation(secureArea: SecureArea) = privateImplementations.add(secureArea)
}
