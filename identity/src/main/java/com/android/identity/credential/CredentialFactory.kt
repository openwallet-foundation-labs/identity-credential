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

import com.android.identity.cbor.DataItem
import com.android.identity.document.Document
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.declaredMemberFunctions

/**
 * A class that aids in creation of credentials from serialized data.
 *
 * The [CredentialFactory] is initially empty, but in the
 * [com.android.identity.credential] package, there are well known [Credential] implementations
 * which can be added using the [addCredentialImplementation] method. In addition,
 * applications may add their own [Credential] implementations.
 */
class CredentialFactory {
    private val credentials: MutableList<KClass<out Credential>> = mutableListOf()

    /**
     * Get all the [Credential] types that are in the repository.
     */
    val credentialTypes: List<KClass<out Credential>>
        get() = credentials

    /**
     * Add a new [Credential] implementation to the repository.
     *
     * @param credentialClass the [Credential] class to add
     */
    fun addCredentialImplementation(credentialClass: KClass<out Credential>) =
        credentials.add(credentialClass)

    /**
     * Creates a [Credential] from serialized data.
     *
     * @param dataItem The serialized credential
     * @param document The document associated with the credential
     * @return a credential instance
     */
    fun createCredential(dataItem: DataItem, document: Document): Credential {
        val credentialType = dataItem["credentialType"].asTstr
        val credClass = credentials.first { it.simpleName == credentialType}
        val fromCbor = credClass.companionObject?.declaredMemberFunctions?.first { it.name == "fromCbor" }
        check(fromCbor != null)
        return fromCbor.call(credClass.companionObject?.objectInstance, dataItem, document) as Credential
    }
}