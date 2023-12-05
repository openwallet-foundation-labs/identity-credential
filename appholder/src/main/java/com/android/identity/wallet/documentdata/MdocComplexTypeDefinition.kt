package com.android.identity.wallet.documentdata

import com.android.identity.credentialtype.CredentialAttributeType

data class MdocComplexTypeDefinition(
    val parentIdentifiers: HashSet<String>,
    val partOfArray: Boolean,
    val identifier: String,
    val displayName: String,
    val type: CredentialAttributeType
)
