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

package com.android.identity.credentialtype

/**
 * A class that contains the metadata of Credential Types.
 *
 * The repository is initially empty, but in the
 * [com.android.identity.credentialtype.knowntypes] package
 * there are well known credential types which can be added
 * using the [addCredentialType] method.
 *
 * Applications also may add their own Credential Types.
 */
class CredentialTypeRepository {
    private val _credentialTypes: MutableList<CredentialType> = mutableListOf()

    /**
     * Get all the Credential Types that are in the repository.
     */
    val credentialTypes: List<CredentialType>
        get() = _credentialTypes

    /**
     * Add a Credential Type to the repository.
     *
     * @param credentialType the Credential Type to add
     */
    fun addCredentialType(credentialType: CredentialType) =
        _credentialTypes.add(credentialType)

    /**
     * Gets the first [CredentialType] in [credentialTypes] with a given mdoc doctype.
     *
     * @param mdocDocType the mdoc doc type.
     * @return the [CredentialType] or null if not found.
     */
    fun getCredentialTypeForMdoc(mdocDocType: String): CredentialType? =
        _credentialTypes.find {
            it.mdocCredentialType?.docType?.equals(mdocDocType) ?: false
        }
}