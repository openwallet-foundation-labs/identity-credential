package com.android.identity.wallet.documentdata

import com.android.identity.documenttype.DocumentAttributeType

data class MdocComplexTypeDefinition(
    val parentIdentifiers: HashSet<String>,
    val partOfArray: Boolean,
    val identifier: String,
    val displayName: String,
    val type: DocumentAttributeType
)
