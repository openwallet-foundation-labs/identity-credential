package com.android.mdl.app.authconfirmation

class SignedDocumentData(
    val namespace: String,
    val signedProperties: Collection<String>,
    val identityCredentialName: String,
    val documentType: String,
    val readerAuth: ByteArray?,
    val itemsRequest: ByteArray
)