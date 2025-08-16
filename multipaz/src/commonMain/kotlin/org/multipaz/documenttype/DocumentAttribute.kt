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

package org.multipaz.documenttype

import org.multipaz.cbor.DataItem
import kotlinx.serialization.json.JsonElement

/**
 * Class containing the metadata of an attribute/data element/claim of a Document Type
 *
 * @property type the datatype of this attribute.
 * @property identifier the identifier of this attribute.
 * @property displayName the name suitable for display of the attribute.
 * @property description a description of the attribute.
 * @property icon the icon for the attribute, if available.
 * @property sampleValueMdoc a sample value for the attribute for ISO mdoc credentials, if available.
 * @property sampleValueJson a sample value for the attribute for JSON-based credentials, if available.
 * @property parentAttribute the parent attribute or `null` if this is not an embedded attribute.
 * @property embeddedAttributes attributes embedded in this attribute, only applicable for JSON-based credentials.
 */
data class DocumentAttribute(
    val type: DocumentAttributeType,
    val identifier: String,
    val displayName: String,
    val description: String,
    val icon: Icon?,
    val sampleValueMdoc: DataItem?,
    val sampleValueJson: JsonElement?,
    val parentAttribute: DocumentAttribute?,
    val embeddedAttributes: List<DocumentAttribute>
)