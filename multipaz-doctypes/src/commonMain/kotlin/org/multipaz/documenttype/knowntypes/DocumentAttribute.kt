package org.multipaz.documenttype.knowntypes

import org.multipaz.cbor.DataItem
import kotlinx.serialization.json.JsonElement
import org.multipaz.documenttype.DocumentAttributeType
import org.multipaz.documenttype.Icon

data class DocumentAttribute(
    val type: DocumentAttributeType,
    val identifier: String,
    val displayName: String,
    val description: String,
    val mandatory: Boolean,
    val mdocNamespace: String,
    val icon: Icon,
    val sampleCbor: DataItem? = null,  // Replace with the actual CBOR type
    val sampleJson: JsonElement? = null
)
