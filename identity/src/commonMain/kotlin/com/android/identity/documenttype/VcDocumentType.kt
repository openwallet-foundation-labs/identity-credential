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

package com.android.identity.documenttype

import com.android.identity.cbor.DataItem

/**
 * Class containing the metadata of an W3C VC Document Type.
 */
class VcDocumentType private constructor(
    val type: String,
    val claims: Map<String, DocumentAttribute>
) {

    /**
     * Builder class for class [VcDocumentType].
     */
    data class Builder(
        val type: String,
        val claims: MutableMap<String, DocumentAttribute> = mutableMapOf()
    ) {
        /**
         * Add a claim to the metadata of the VC Document Type.
         *
         * @param type the datatype of this claim.
         * @param identifier the identifier of this claim.
         * @param displayName a name suitable for display of the claim.
         * @param description a description of the claim.
         * @param sampleValue a sample value for the attribute.
         */
        fun addClaim(
            type: DocumentAttributeType,
            identifier: String,
            displayName: String,
            description: String,
            sampleValue: DataItem? = null
        ) = apply {
            claims[identifier] = DocumentAttribute(
                type, identifier, displayName, description, sampleValue
            )
        }

        /**
         * Build the [VcDocumentType].
         */
        fun build() = VcDocumentType(type, claims)
    }
}