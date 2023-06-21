package com.android.mdl.app.transfer

sealed class AddDocumentToResponseResult {

    object DocumentAdded : AddDocumentToResponseResult()

    object UserAuthRequired : AddDocumentToResponseResult()

    object PassphraseRequired : AddDocumentToResponseResult()
}