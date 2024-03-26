package com.android.identity.wallet.authconfirmation

import com.android.identity.mdoc.request.DeviceRequestParser

data class RequestedDocumentData(
    val userReadableName: String,
    val identityCredentialName: String,
    val requestedElements: ArrayList<RequestedElement>,
    val requestedDocument: DeviceRequestParser.DocRequest
)