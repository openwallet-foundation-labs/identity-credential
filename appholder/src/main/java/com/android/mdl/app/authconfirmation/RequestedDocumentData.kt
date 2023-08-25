package com.android.mdl.app.authconfirmation

import com.android.identity.mdoc.request.DeviceRequestParser

data class RequestedDocumentData(
    val userReadableName: String,
    val identityCredentialName: String,
    val requestedElements: ArrayList<RequestedElement>,
    val requestedDocument: DeviceRequestParser.DocumentRequest
)