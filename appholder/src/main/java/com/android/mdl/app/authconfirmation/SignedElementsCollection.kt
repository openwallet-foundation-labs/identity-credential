package com.android.mdl.app.authconfirmation

class SignedElementsCollection {

    private val requestedDocuments = mutableMapOf<String, RequestedDocumentData>()
    private val signedElements = mutableListOf<RequestedElement>()

    fun addNamespace(requestedData: RequestedDocumentData) {
        this.requestedDocuments[requestedData.identityCredentialName] = requestedData
    }

    fun toggleProperty(element: RequestedElement) {
        if (!signedElements.remove(element)) {
            signedElements.add(element)
        }
    }

    fun collect(): List<SignedDocumentData> {
        return requestedDocuments.keys.map { namespace ->
            val document = requestedDocuments.getValue(namespace)
            SignedDocumentData(
                signedElements = signedElements,
                identityCredentialName = document.identityCredentialName,
                documentType = document.requestedDocument.docType,
            )
        }
    }

    fun clear() {
        requestedDocuments.clear()
        signedElements.clear()
    }
}