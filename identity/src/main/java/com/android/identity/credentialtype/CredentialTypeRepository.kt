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
    private val credentialTypes: MutableList<CredentialType> = mutableListOf()

    /**
     * Add a Credential Type to the repository.
     *
     * @param credentialType the Credential Type to add
     */
    fun addCredentialType(credentialType: CredentialType) {
        credentialTypes.add(credentialType)
    }

    /**
     * Get all the Credential Types that are in the repository.
     *
     * @return all the [CredentialType]s in the repository
     */
    fun getCredentialTypes(): List<CredentialType> {
        return credentialTypes
    }

    /**
     * Get an mdoc credential type by its doc type
     *
     * @param docType the type of the mdoc credential     *
     * @return the [MdocCredentialType] when found
     */
    fun getMdocCredentialType(docType: String): MdocCredentialType? {
        return credentialTypes.find {
            it.mdocCredentialType?.docType?.equals(docType) ?: false
        }?.mdocCredentialType
    }

    /**
     * Get a VC credential type by its type
     *
     * @param type the type of the VC credential     *
     * @return the [VcCredentialType] when found
     */
    fun getVcCredentialType(type: String): VcCredentialType? {
        return credentialTypes.find {
            it.vcCredentialType?.type?.equals(type) ?: false
        }?.vcCredentialType
    }
}