package com.android.mdl.app.authconfirmation

import com.android.identity.DeviceRequestParser

data class RequestedDocumentData(
    val userReadableName: String,
    val namespace: String,
    val identityCredentialName: String,
    val needsAuth: Boolean,
    val requestedProperties: Collection<String>,
    val requestedDocument: DeviceRequestParser.DocumentRequest
) {

    fun nameTypeTitle(): String {
        return "$userReadableName  |  ${requestedDocument.docType}"
    }
}