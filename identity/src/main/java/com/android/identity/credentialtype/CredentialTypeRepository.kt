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
    private val mdocDataElements: MutableMap<String, Map<String, Map<String, MdocDataElement>>> =
        mutableMapOf()

    /**
     * Add a Credential Type to the repository.
     */
    fun addCredentialType(credentialType: CredentialType) {
        credentialTypes.add(credentialType)
        if (credentialType.mdocCredentialType != null) {
            mdocDataElements[credentialType.mdocCredentialType.docType] =
                credentialType.mdocCredentialType.namespaces.map {
                    Pair(
                        it.namespace,
                        it.dataElements.associateBy { el -> el.attribute.identifier }.toMap()
                    )
                }.toMap()
        }
    }

    /**
     * Get all the Credential Types that are in the repository.
     */
    fun getCredentialTypes(): List<CredentialType> {
        return credentialTypes
    }

    /**
     * Get an mdoc data element by doc type, namespace and identifier
     *
     * @param docType the type of the mdoc credential
     * @param namespace the namespace of the data element
     * @param identifier the identifier of the data element
     */
    fun getMdocDataElement(
        docType: String,
        namespace: String,
        identifier: String
    ): MdocDataElement? {
        return mdocDataElements[docType]?.get(namespace)?.get(identifier)
    }

    /**
     * Get an mdoc credential type by its doc type
     *
     * @param docType the type of the mdoc credential
     */
    fun getMdocCredentialType(docType: String): MdocCredentialType? {
        return credentialTypes.find {
            it.mdocCredentialType?.docType?.equals(docType) ?: false
        }?.mdocCredentialType
    }
}