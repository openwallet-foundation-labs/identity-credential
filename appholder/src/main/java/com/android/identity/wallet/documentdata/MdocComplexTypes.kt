package com.android.identity.wallet.documentdata

import com.android.identity.documenttype.DocumentAttributeType

class MdocComplexTypes private constructor(
    val docType: String,
    val namespaces: List<MdocNamespaceComplexTypes>,
) {
    data class Builder(
        val docType: String,
        val namespaces: MutableMap<String, MdocNamespaceComplexTypes.Builder> = mutableMapOf(),
    ) {
        fun addDefinition(
            namespace: String,
            parentIdentifiers: HashSet<String>,
            partOfArray: Boolean,
            identifier: String,
            displayName: String,
            type: DocumentAttributeType,
        ) = apply {
            if (!namespaces.containsKey(namespace)) {
                namespaces[namespace] = MdocNamespaceComplexTypes.Builder(namespace)
            }
            namespaces[namespace]!!.addDefinition(parentIdentifiers, partOfArray, identifier, displayName, type)
        }

        fun build() = MdocComplexTypes(docType, namespaces.values.map { it.build() })
    }
}
