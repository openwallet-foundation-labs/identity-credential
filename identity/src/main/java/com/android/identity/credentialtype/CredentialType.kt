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
 * Class representing the metadata of a Credential Type
 *
 * @param displayName the friendly name of the Credential Type
 * @param mdocCredentialType metadata of an mDoc Credential Type (optional)
 * @param vcCredentialType metadata of a W3C VC Credential Type (optional)
 *
 */
class CredentialType private constructor(
    val displayName: String,
    val mdocCredentialType: MdocCredentialType?,
    val vcCredentialType: VcCredentialType?
) {

    /**
     * Builder class for class [CredentialType]
     *
     * @param displayName the friendly name of the Credential Type
     * @param mdocBuilder a builder for the [MdocCredentialType]
     * @param vcBuilder a builder for the [VcCredentialType]
     */
    data class Builder(
        val displayName: String,
        var mdocBuilder: MdocCredentialType.Builder? = null,
        var vcBuilder: VcCredentialType.Builder? = null
    ) {
        /**
         * Initialize the [mdocBuilder]
         *
         * @param mdocDocType the DocType of the mDoc
         */
        fun addMdocCredentialType(mdocDocType: String) = apply {
            mdocBuilder = MdocCredentialType.Builder(mdocDocType)
        }

        /**
         * Initialize the [vcBuilder]
         *
         * @param vcType the type of the VC Credential Type
         */
        fun addVcCredentialType(vcType: String) = apply {
            vcBuilder = VcCredentialType.Builder(vcType)
        }

        /**
         * Add an attribute for both mDoc and VC, using the same identifier
         *
         * @param type the datatype of this attribute
         * @param identifier the identifier of this attribute for both mDoc and VC
         * @param displayName the friendly name of the attribute
         * @param description a description of the attribute
         * @param mandatory indication whether the mDoc attribute is mandatory
         * @param mdocNamespace the namespace of the mDoc attribute
         */
        fun addAttribute(
            type: CredentialAttributeType,
            identifier: String,
            displayName: String,
            description: String,
            mandatory: Boolean,
            mdocNamespace: String
        ) = apply {
            addMdocAttribute(type, identifier, displayName, description, mandatory, mdocNamespace)
            addVcAttribute(type, identifier, displayName, description)
        }

        /**
         * Add an attribute for both mDoc and VC, using a different identifier
         *
         * @param type the datatype of this attribute
         * @param mdocIdentifier the identifier of this attribute for mDoc
         * @param vcIdentifier the identifier of this attribute for VC
         * @param displayName the friendly name of the attribute
         * @param description a description of the attribute
         * @param mandatory indication whether the mDoc attribute is mandatory
         * @param mdocNamespace the namespace of the mDoc attribute
         */
        fun addAttribute(
            type: CredentialAttributeType,
            mdocIdentifier: String,
            vcIdentifier: String,
            displayName: String,
            description: String,
            mandatory: Boolean,
            mdocNamespace: String,
        ) = apply {
            addMdocAttribute(type, mdocIdentifier, displayName, description, mandatory, mdocNamespace)
            addVcAttribute(type, vcIdentifier, displayName, description)
        }

        /**
         * Add an attribute for mDoc only
         *
         * @param type the datatype of this attribute
         * @param identifier the identifier of this attribute
         * @param displayName the friendly name of the attribute
         * @param description a description of the attribute
         * @param mandatory indication whether the mDoc attribute is mandatory
         * @param mdocNamespace the namespace of the mDoc attribute
         */
        fun addMdocAttribute(
            type: CredentialAttributeType,
            identifier: String,
            displayName: String,
            description: String,
            mandatory: Boolean,
            mdocNamespace: String
        ) = apply {
            mdocBuilder?.addDataElement(
                mdocNamespace,
                type,
                identifier,
                displayName,
                description,
                mandatory
            )
                ?: throw Exception("The mDoc Credential Type was not initialized")
        }

        /**
         * Add an attribute for VC only
         *
         * @param type the datatype of this attribute
         * @param identifier the identifier of this attribute
         * @param displayName the friendly name of the attribute
         * @param description a description of the attribute
         */
        fun addVcAttribute(
            type: CredentialAttributeType,
            identifier: String,
            displayName: String,
            description: String
        ) = apply {
            vcBuilder?.addClaim(type, identifier, displayName, description)
                ?: throw Exception("The VC Credential Type was not initialized")
        }

        /**
         * Build the [CredentialType]
         */
        fun build() = CredentialType(displayName, mdocBuilder?.build(), vcBuilder?.build())
    }
}