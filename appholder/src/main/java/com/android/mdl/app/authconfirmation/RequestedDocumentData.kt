package com.android.mdl.app.authconfirmation

import com.android.identity.DeviceRequestParser

data class RequestedDocumentData(
    val userReadableName: String,
    val identityCredentialName: String,
    val needsAuth: Boolean,
    val requestedElements: ArrayList<RequestedElement>,
    val requestedDocument: DeviceRequestParser.DocumentRequest
)